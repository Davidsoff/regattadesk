package com.regattadesk.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regattadesk.eventstore.DomainEvent;
import com.regattadesk.eventstore.EventEnvelope;
import com.regattadesk.eventstore.EventMetadata;
import com.regattadesk.eventstore.EventStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class PaymentStatusService {

    @Inject
    EventStore eventStore;

    @Inject
    DataSource dataSource;

    @Inject
    FinanceProjectionHandler projectionHandler;

    @Inject
    ObjectMapper objectMapper;

    public Optional<EntryPaymentStatusDetails> getEntryPaymentStatus(UUID regattaId, UUID entryId) {
        String sql = """
            SELECT
                e.id,
                e.regatta_id,
                e.billing_club_id,
                e.payment_status,
                e.paid_at,
                e.paid_by,
                e.payment_reference
            FROM entries e
            WHERE e.regatta_id = ? AND e.id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            stmt.setObject(2, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                return Optional.of(new EntryPaymentStatusDetails(
                    (UUID) rs.getObject("id"),
                    (UUID) rs.getObject("regatta_id"),
                    (UUID) rs.getObject("billing_club_id"),
                    PaymentStatus.fromValue(rs.getString("payment_status")),
                    toInstant(rs.getTimestamp("paid_at")),
                    rs.getString("paid_by"),
                    rs.getString("payment_reference")
                ));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load entry payment status", e);
        }
    }

    @Transactional
    public EntryPaymentStatusDetails updateEntryPaymentStatus(
        UUID regattaId,
        UUID entryId,
        PaymentStatus targetStatus,
        String paymentReference,
        String actor
    ) {
        EntryPaymentRow row = loadEntryPaymentRow(regattaId, entryId)
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
            appendAndProject(
                entryId,
                "EntryPayment",
                List.of(event),
                actor
            );
        }

        return getEntryPaymentStatus(regattaId, entryId)
            .orElseThrow(() -> new IllegalStateException("Entry disappeared during payment status update"));
    }

    public Optional<ClubPaymentStatusDetails> getClubPaymentStatus(UUID regattaId, UUID clubId) {
        if (!clubExists(clubId)) {
            return Optional.empty();
        }

        try (Connection conn = dataSource.getConnection()) {
            projectionHandler.recomputeClubPaymentStatus(conn, regattaId, clubId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to refresh club payment status projection", e);
        }

        String sql = """
            SELECT regatta_id, club_id, payment_status, billable_entry_count, paid_entry_count
            FROM club_payment_statuses
            WHERE regatta_id = ? AND club_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            stmt.setObject(2, clubId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.of(new ClubPaymentStatusDetails(
                        regattaId,
                        clubId,
                        PaymentStatus.UNPAID,
                        0,
                        0
                    ));
                }

                return Optional.of(new ClubPaymentStatusDetails(
                    (UUID) rs.getObject("regatta_id"),
                    (UUID) rs.getObject("club_id"),
                    PaymentStatus.fromValue(rs.getString("payment_status")),
                    rs.getInt("billable_entry_count"),
                    rs.getInt("paid_entry_count")
                ));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load club payment status", e);
        }
    }

    @Transactional
    public ClubPaymentStatusDetails updateClubPaymentStatus(
        UUID regattaId,
        UUID clubId,
        PaymentStatus targetStatus,
        String paymentReference,
        String actor
    ) {
        if (!clubExists(clubId)) {
            throw new IllegalArgumentException("Club not found");
        }

        List<EntryPaymentRow> rows = listClubEntryPaymentRows(regattaId, clubId);
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
            if (!clubExists(clubId)) {
                failures.add(new BulkPaymentFailure("club", clubId, "CLUB_NOT_FOUND", "Club not found"));
                missingClubCount++;
                continue;
            }
            listClubEntryPaymentRows(regattaId, clubId).stream()
                .map(EntryPaymentRow::entryId)
                .forEach(targetEntryIds::add);
        }
        targetEntryIds.addAll(normalizedEntryIds);
        // totalRequested reflects the actual expanded set: entries after club resolution + unresolvable clubs
        int totalRequested = targetEntryIds.size() + missingClubCount;
        Map<UUID, EntryPaymentRow> rowsByEntryId = loadEntryPaymentRows(regattaId, targetEntryIds);

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

    private Optional<EntryPaymentRow> loadEntryPaymentRow(UUID regattaId, UUID entryId) {
        String sql = """
            SELECT
                e.id,
                e.regatta_id,
                e.billing_club_id,
                e.payment_status,
                e.paid_at,
                e.paid_by,
                e.payment_reference,
                c.id AS crew_id,
                c.club_id AS crew_club_id,
                c.is_composite
            FROM entries e
            JOIN crews c ON c.id = e.crew_id
            WHERE e.regatta_id = ? AND e.id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            stmt.setObject(2, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapEntryPaymentRow(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load entry payment row", e);
        }
    }

    private static final int IN_CLAUSE_CHUNK_SIZE = 500;

    private Map<UUID, EntryPaymentRow> loadEntryPaymentRows(UUID regattaId, Set<UUID> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {
            return Map.of();
        }
        // Chunk large sets to avoid hitting DB IN-clause limits and query planner degradation
        if (entryIds.size() > IN_CLAUSE_CHUNK_SIZE) {
            Map<UUID, EntryPaymentRow> result = new HashMap<>();
            List<UUID> idList = new ArrayList<>(entryIds);
            for (int i = 0; i < idList.size(); i += IN_CLAUSE_CHUNK_SIZE) {
                Set<UUID> chunk = new LinkedHashSet<>(
                    idList.subList(i, Math.min(i + IN_CLAUSE_CHUNK_SIZE, idList.size()))
                );
                result.putAll(loadEntryPaymentRowsChunk(regattaId, chunk));
            }
            return result;
        }
        return loadEntryPaymentRowsChunk(regattaId, entryIds);
    }

    private Map<UUID, EntryPaymentRow> loadEntryPaymentRowsChunk(UUID regattaId, Set<UUID> entryIds) {
        StringBuilder placeholders = new StringBuilder();
        int index = 0;
        for (UUID ignored : entryIds) {
            if (index++ > 0) {
                placeholders.append(", ");
            }
            placeholders.append("?");
        }

        String sql = """
            SELECT
                e.id,
                e.regatta_id,
                e.billing_club_id,
                e.payment_status,
                e.paid_at,
                e.paid_by,
                e.payment_reference,
                c.id AS crew_id,
                c.club_id AS crew_club_id,
                c.is_composite
            FROM entries e
            JOIN crews c ON c.id = e.crew_id
            WHERE e.regatta_id = ? AND e.id IN (%s)
            """.formatted(placeholders);

        Map<UUID, EntryPaymentRow> rowsByEntryId = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            int parameterIndex = 2;
            for (UUID entryId : entryIds) {
                stmt.setObject(parameterIndex++, entryId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    EntryPaymentRow row = mapEntryPaymentRow(rs);
                    rowsByEntryId.put(row.entryId(), row);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load entry payment rows", e);
        }

        return rowsByEntryId;
    }

    private List<EntryPaymentRow> listClubEntryPaymentRows(UUID regattaId, UUID clubId) {
        List<EntryPaymentRow> rows = new ArrayList<>();
        String sql = """
            SELECT
                e.id,
                e.regatta_id,
                e.billing_club_id,
                e.payment_status,
                e.paid_at,
                e.paid_by,
                e.payment_reference,
                c.id AS crew_id,
                c.club_id AS crew_club_id,
                c.is_composite
            FROM entries e
            JOIN crews c ON c.id = e.crew_id
            WHERE e.regatta_id = ?
              AND (
                    e.billing_club_id = ?
                 OR (
                        e.billing_club_id IS NULL
                    AND c.is_composite = FALSE
                    AND c.club_id = ?
                 )
              )
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            stmt.setObject(2, clubId);
            stmt.setObject(3, clubId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapEntryPaymentRow(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list club entry payments", e);
        }
        return rows;
    }

    private EntryPaymentRow mapEntryPaymentRow(ResultSet rs) throws Exception {
        UUID billingClubId = (UUID) rs.getObject("billing_club_id");
        UUID crewClubId = (UUID) rs.getObject("crew_club_id");
        boolean isComposite = rs.getBoolean("is_composite");

        UUID effectiveClubId = billingClubId != null
            ? billingClubId
            : (!isComposite ? crewClubId : null);

        return new EntryPaymentRow(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("regatta_id"),
            PaymentStatus.fromValue(rs.getString("payment_status")),
            toInstant(rs.getTimestamp("paid_at")),
            rs.getString("paid_by"),
            rs.getString("payment_reference"),
            effectiveClubId
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

    private Optional<BulkPaymentMarkResult> findBulkOperationReplay(
        UUID regattaId,
        String actor,
        String idempotencyKey,
        PaymentStatus targetStatus,
        String requestFingerprint
    ) {
        // Query directly by idempotency key fields including a request fingerprint
        // (sorted entry/club IDs + payment reference) to prevent incorrect replay
        // when the same actor reuses a key with a different request payload.
        String sql = """
            SELECT payload
            FROM event_store
            WHERE aggregate_id = ?
              AND event_type = 'BulkPaymentStatusMarked'
              AND payload->>'idempotencyKey' = ?
              AND payload->>'requestedBy' = ?
              AND payload->>'targetStatus' = ?
              AND payload->>'requestFingerprint' = ?
            ORDER BY created_at DESC
            LIMIT 1
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            stmt.setString(2, idempotencyKey);
            stmt.setString(3, actor);
            stmt.setString(4, targetStatus.value());
            stmt.setString(5, requestFingerprint);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BulkPaymentStatusMarkedEvent event = objectMapper.readValue(
                        rs.getString("payload"),
                        BulkPaymentStatusMarkedEvent.class
                    );
                    return Optional.of(new BulkPaymentMarkResult(
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
                    ));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve idempotency replay", e);
        }
        return Optional.empty();
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

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private boolean clubExists(UUID clubId) {
        String sql = "SELECT id FROM clubs WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, clubId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify club existence", e);
        }
    }

    private record EntryPaymentRow(
        UUID entryId,
        UUID regattaId,
        PaymentStatus paymentStatus,
        Instant paidAt,
        String paidBy,
        String paymentReference,
        UUID effectiveClubId
    ) {
    }
}
