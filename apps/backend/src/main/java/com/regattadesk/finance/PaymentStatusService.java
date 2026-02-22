package com.regattadesk.finance;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PaymentStatusService {

    @Inject
    EventStore eventStore;

    @Inject
    DataSource dataSource;

    @Inject
    FinanceProjectionHandler projectionHandler;

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
