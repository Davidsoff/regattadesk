package com.regattadesk.finance;

import com.regattadesk.eventstore.DomainEvent;
import com.regattadesk.eventstore.EventEnvelope;
import com.regattadesk.eventstore.EventMetadata;
import com.regattadesk.eventstore.EventStore;
import com.regattadesk.finance.PaymentStatusRepository.EntryPaymentRow;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Service for managing payment statuses of entries and clubs.
 * 
 * This service coordinates payment status transitions, bulk operations,
 * and idempotency handling while delegating data access to PaymentStatusRepository.
 */
@ApplicationScoped
public class PaymentStatusService {

    @Inject
    EventStore eventStore;

    @Inject
    PaymentStatusRepository paymentRepository;

    @Inject
    FinanceProjectionHandler projectionHandler;

    public Optional<EntryPaymentStatusDetails> getEntryPaymentStatus(UUID regattaId, UUID entryId) {
        return paymentRepository.getEntryPaymentStatus(regattaId, entryId);
    }

    @Transactional
    public EntryPaymentStatusDetails updateEntryPaymentStatus(
        UUID regattaId,
        UUID entryId,
        PaymentStatus targetStatus,
        String paymentReference,
        String actor
    ) {
        EntryPaymentRow row = paymentRepository.loadEntryPaymentRow(regattaId, entryId)
            .orElseThrow(() -> new IllegalArgumentException("Entry not found"));

        EntryPaymentStatusModel model = new EntryPaymentStatusModel(
            row.paymentStatus(),
            row.paidAt(),
            row.paidBy(),
            row.paymentReference()
        );
        EntryPaymentStatusModel.TransitionResult transition = model.transitionTo(
            targetStatus,
            paymentReference,
            actor,
            Instant.now()
        );

        if (transition.changed()) {
            EntryPaymentStatusUpdatedEvent event = new EntryPaymentStatusUpdatedEvent(
                entryId,
                regattaId,
                row.effectiveClubId(),
                transition.previousStatus().value(),
                transition.nextStatus().value(),
                transition.nextPaidAt(),
                transition.nextPaidBy(),
                transition.nextPaymentReference(),
                "single_entry_update"
            );
            appendAndProject(entryId, "EntryPayment", List.of(event), actor);
        }

        return getEntryPaymentStatus(regattaId, entryId)
            .orElseThrow(() -> new IllegalStateException("Entry disappeared during payment status update"));
    }

    public Optional<ClubPaymentStatusDetails> getClubPaymentStatus(UUID regattaId, UUID clubId) {
        if (!paymentRepository.clubExists(clubId)) {
            return Optional.empty();
        }

        paymentRepository.recomputeClubPaymentStatus(regattaId, clubId, projectionHandler);
        return paymentRepository.getClubPaymentStatus(regattaId, clubId);
    }

    @Transactional
    public ClubPaymentStatusDetails updateClubPaymentStatus(
        UUID regattaId,
        UUID clubId,
        PaymentStatus targetStatus,
        String paymentReference,
        String actor
    ) {
        if (!paymentRepository.clubExists(clubId)) {
            throw new IllegalArgumentException("Club not found");
        }

        List<EntryPaymentRow> rows = paymentRepository.listClubEntryPaymentRows(regattaId, clubId);
        int changedCount = 0;
        for (EntryPaymentRow row : rows) {
            EntryPaymentStatusModel model = new EntryPaymentStatusModel(
                row.paymentStatus(),
                row.paidAt(),
                row.paidBy(),
                row.paymentReference()
            );
            EntryPaymentStatusModel.TransitionResult transition = model.transitionTo(
                targetStatus,
                paymentReference,
                actor,
                Instant.now()
            );
            if (!transition.changed()) {
                continue;
            }

            changedCount++;
            EntryPaymentStatusUpdatedEvent entryEvent = new EntryPaymentStatusUpdatedEvent(
                row.entryId(),
                regattaId,
                row.effectiveClubId(),
                transition.previousStatus().value(),
                transition.nextStatus().value(),
                transition.nextPaidAt(),
                transition.nextPaidBy(),
                transition.nextPaymentReference(),
                "club_update"
            );
            appendAndProject(row.entryId(), "EntryPayment", List.of(entryEvent), actor);
        }

        ClubPaymentStatusUpdateRequestedEvent clubEvent = new ClubPaymentStatusUpdateRequestedEvent(
            clubId,
            regattaId,
            targetStatus.value(),
            changedCount,
            actor,
            paymentReference
        );
        appendAndProject(clubId, "ClubPayment", List.of(clubEvent), actor);

        return getClubPaymentStatus(regattaId, clubId)
            .orElseThrow(() -> new IllegalStateException("Club disappeared during payment status update"));
    }

    @Transactional
    public BulkPaymentMarkResult bulkMarkPaymentStatuses(
        UUID regattaId,
        List<UUID> entryIds,
        List<UUID> clubIds,
        PaymentStatus targetStatus,
        String paymentReference,
        String actor,
        String idempotencyKey
    ) {
        Set<UUID> normalizedEntryIds = normalizeIds(entryIds);
        Set<UUID> normalizedClubIds = normalizeIds(clubIds);
        if (normalizedEntryIds.isEmpty() && normalizedClubIds.isEmpty()) {
            throw new IllegalArgumentException("At least one entry_id or club_id is required");
        }

        String normalizedActor = (actor == null || actor.isBlank()) ? "unknown" : actor.trim();
        String normalizedReference = normalizeText(paymentReference);
        String normalizedIdempotencyKey = normalizeText(idempotencyKey);
        if (normalizedIdempotencyKey != null && normalizedIdempotencyKey.length() > 128) {
            throw new IllegalArgumentException("idempotency_key must be 128 characters or fewer");
        }

        String requestFingerprint = computeRequestFingerprint(
            normalizedEntryIds, normalizedClubIds, normalizedReference);

        if (normalizedIdempotencyKey != null) {
            Optional<BulkPaymentMarkResult> replay = findBulkOperationReplay(
                regattaId,
                normalizedActor,
                normalizedIdempotencyKey,
                targetStatus,
                requestFingerprint
            );
            if (replay.isPresent()) {
                return replay.get();
            }
        }

        Set<UUID> targetEntryIds = new LinkedHashSet<>();
        List<BulkPaymentFailure> failures = new ArrayList<>();
        int missingClubCount = 0;

        for (UUID clubId : normalizedClubIds) {
            if (!paymentRepository.clubExists(clubId)) {
                failures.add(new BulkPaymentFailure("club", clubId, "CLUB_NOT_FOUND", "Club not found"));
                missingClubCount++;
                continue;
            }
            paymentRepository.listClubEntryPaymentRows(regattaId, clubId).stream()
                .map(EntryPaymentRow::entryId)
                .forEach(targetEntryIds::add);
        }
        targetEntryIds.addAll(normalizedEntryIds);

        int totalRequested = targetEntryIds.size() + missingClubCount;
        Map<UUID, EntryPaymentRow> rowsByEntryId = paymentRepository.loadEntryPaymentRows(regattaId, targetEntryIds);

        int updatedCount = 0;
        int unchangedCount = 0;
        for (UUID entryId : targetEntryIds) {
            EntryPaymentRow row = rowsByEntryId.get(entryId);
            if (row == null) {
                failures.add(new BulkPaymentFailure("entry", entryId, "ENTRY_NOT_FOUND", "Entry not found"));
                continue;
            }

            EntryPaymentStatusModel model = new EntryPaymentStatusModel(
                row.paymentStatus(),
                row.paidAt(),
                row.paidBy(),
                row.paymentReference()
            );
            EntryPaymentStatusModel.TransitionResult transition = model.transitionTo(
                targetStatus,
                normalizedReference,
                normalizedActor,
                Instant.now()
            );

            if (!transition.changed()) {
                unchangedCount++;
                continue;
            }

            updatedCount++;
            EntryPaymentStatusUpdatedEvent entryEvent = new EntryPaymentStatusUpdatedEvent(
                row.entryId(),
                regattaId,
                row.effectiveClubId(),
                transition.previousStatus().value(),
                transition.nextStatus().value(),
                transition.nextPaidAt(),
                transition.nextPaidBy(),
                transition.nextPaymentReference(),
                "bulk_update"
            );
            appendAndProject(row.entryId(), "EntryPayment", List.of(entryEvent), normalizedActor);
        }

        int processedCount = updatedCount + unchangedCount;
        int failedCount = failures.size();
        String message = failedCount == 0
            ? "Bulk payment update completed"
            : "Bulk payment update completed with partial failures";

        BulkPaymentMarkResult result = new BulkPaymentMarkResult(
            failedCount == 0,
            message,
            totalRequested,
            processedCount,
            updatedCount,
            unchangedCount,
            failedCount,
            List.copyOf(failures),
            normalizedIdempotencyKey,
            false
        );

        BulkPaymentStatusMarkedEvent summaryEvent = new BulkPaymentStatusMarkedEvent(
            regattaId,
            targetStatus.value(),
            totalRequested,
            processedCount,
            updatedCount,
            unchangedCount,
            failedCount,
            result.failures(),
            normalizedActor,
            normalizedReference,
            normalizedIdempotencyKey,
            requestFingerprint
        );
        appendAndProject(regattaId, "BulkPayment", List.of(summaryEvent), normalizedActor);

        return result;
    }

    private Optional<BulkPaymentMarkResult> findBulkOperationReplay(
        UUID regattaId,
        String actor,
        String idempotencyKey,
        PaymentStatus targetStatus,
        String requestFingerprint
    ) {
        Optional<BulkPaymentStatusMarkedEvent> event;
        if (paymentRepository.isPostgres()) {
            event = paymentRepository.findBulkOperationReplayPostgres(
                regattaId, actor, idempotencyKey, targetStatus, requestFingerprint);
        } else {
            event = paymentRepository.findBulkOperationReplayFallback(
                regattaId, actor, idempotencyKey, targetStatus, requestFingerprint);
        }
        return event.map(this::toReplayResult);
    }

    private BulkPaymentMarkResult toReplayResult(BulkPaymentStatusMarkedEvent event) {
        return new BulkPaymentMarkResult(
            event.getFailedCount() == 0,
            "Replayed previous bulk payment update",
            event.getTotalRequested(),
            event.getProcessedCount(),
            event.getUpdatedCount(),
            event.getUnchangedCount(),
            event.getFailedCount(),
            event.getFailures() == null ? List.of() : List.copyOf(event.getFailures()),
            event.getIdempotencyKey(),
            true
        );
    }

    private void appendAndProject(
        UUID aggregateId,
        String aggregateType,
        List<? extends DomainEvent> events,
        String actor
    ) {
        long expectedVersion = eventStore.getCurrentVersion(aggregateId);
        long fromSequence = expectedVersion + 1;
        EventMetadata metadata = EventMetadata.builder()
            .correlationId(UUID.randomUUID())
            .addData("actor", actor)
            .build();

        eventStore.append(aggregateId, aggregateType, expectedVersion, List.copyOf(events), metadata);

        for (EventEnvelope envelope : eventStore.readStream(aggregateId, fromSequence)) {
            if (projectionHandler.canHandle(envelope)) {
                projectionHandler.handle(envelope);
            }
        }
    }

    private Set<UUID> normalizeIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<UUID> normalized = new LinkedHashSet<>();
        for (UUID id : ids) {
            if (id != null) {
                normalized.add(id);
            }
        }
        return normalized;
    }

    private String computeRequestFingerprint(Set<UUID> entryIds, Set<UUID> clubIds, String paymentReference) {
        List<String> entries = new ArrayList<>();
        for (UUID id : entryIds) entries.add(id.toString());
        Collections.sort(entries);

        List<String> clubs = new ArrayList<>();
        for (UUID id : clubIds) clubs.add(id.toString());
        Collections.sort(clubs);

        String ref = paymentReference != null ? paymentReference : "";
        return String.join(",", entries) + "|" + String.join(",", clubs) + "|" + ref;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
