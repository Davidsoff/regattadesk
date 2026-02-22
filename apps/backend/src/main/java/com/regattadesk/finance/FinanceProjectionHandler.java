package com.regattadesk.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regattadesk.eventstore.EventEnvelope;
import com.regattadesk.projection.ProjectionHandler;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class FinanceProjectionHandler implements ProjectionHandler {

    @Inject
    DataSource dataSource;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public String getProjectionName() {
        return "finance_projection";
    }

    @Override
    public boolean canHandle(EventEnvelope event) {
        String eventType = event.getEventType();
        return "EntryPaymentStatusUpdated".equals(eventType)
            || "ClubPaymentStatusUpdateRequested".equals(eventType);
    }

    @Override
    public void handle(EventEnvelope envelope) {
        String eventType = envelope.getEventType();
        try {
            switch (eventType) {
                case "EntryPaymentStatusUpdated" -> handleEntryPaymentStatusUpdated(envelope);
                case "ClubPaymentStatusUpdateRequested" -> handleClubPaymentStatusUpdateRequested(envelope);
                default -> Log.debugf("Ignoring finance event type: %s", eventType);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle finance event: " + eventType, e);
        }
    }

    private void handleEntryPaymentStatusUpdated(EventEnvelope envelope) throws Exception {
        EntryPaymentStatusUpdatedEvent event = parseEvent(envelope, EntryPaymentStatusUpdatedEvent.class);

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("""
                UPDATE entries
                SET payment_status = ?, paid_at = ?, paid_by = ?, payment_reference = ?, updated_at = ?
                WHERE id = ? AND regatta_id = ?
                """)) {
                stmt.setString(1, event.getNewStatus());
                stmt.setTimestamp(2, toTimestamp(event.getPaidAt()));
                stmt.setString(3, event.getPaidBy());
                stmt.setString(4, event.getPaymentReference());
                stmt.setTimestamp(5, Timestamp.from(Instant.now()));
                stmt.setObject(6, event.getEntryId());
                stmt.setObject(7, event.getRegattaId());
                stmt.executeUpdate();
            }

            if (event.getClubId() != null) {
                recomputeClubPaymentStatus(conn, event.getRegattaId(), event.getClubId());
            }
        }
    }

    private void handleClubPaymentStatusUpdateRequested(EventEnvelope envelope) throws Exception {
        ClubPaymentStatusUpdateRequestedEvent event = parseEvent(envelope, ClubPaymentStatusUpdateRequestedEvent.class);

        try (Connection conn = dataSource.getConnection()) {
            recomputeClubPaymentStatus(conn, event.getRegattaId(), event.getClubId());
        }
    }

    void recomputeClubPaymentStatus(Connection conn, UUID regattaId, UUID clubId) throws Exception {
        int billableCount = 0;
        int paidCount = 0;

        try (PreparedStatement stmt = conn.prepareStatement("""
            SELECT
                COUNT(*) AS billable_count,
                COALESCE(SUM(CASE WHEN e.payment_status = 'paid' THEN 1 ELSE 0 END), 0) AS paid_count
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
            """)) {
            stmt.setObject(1, regattaId);
            stmt.setObject(2, clubId);
            stmt.setObject(3, clubId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    billableCount = rs.getInt("billable_count");
                    paidCount = rs.getInt("paid_count");
                }
            }
        }

        PaymentStatus derivedStatus = billableCount > 0 && paidCount == billableCount
            ? PaymentStatus.PAID
            : PaymentStatus.UNPAID;

        String sql = upsertClubPaymentStatusSql(conn);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            Timestamp now = Timestamp.from(Instant.now());
            stmt.setObject(1, regattaId);
            stmt.setObject(2, clubId);
            stmt.setString(3, derivedStatus.value());
            stmt.setInt(4, billableCount);
            stmt.setInt(5, paidCount);
            stmt.setTimestamp(6, now);
            stmt.executeUpdate();
        }
    }

    private String upsertClubPaymentStatusSql(Connection conn) throws Exception {
        if (isPostgres(conn)) {
            return """
                INSERT INTO club_payment_statuses (regatta_id, club_id, payment_status, billable_entry_count, paid_entry_count, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (regatta_id, club_id)
                DO UPDATE SET
                    payment_status = EXCLUDED.payment_status,
                    billable_entry_count = EXCLUDED.billable_entry_count,
                    paid_entry_count = EXCLUDED.paid_entry_count,
                    updated_at = EXCLUDED.updated_at
                """;
        }

        return """
            MERGE INTO club_payment_statuses
                (regatta_id, club_id, payment_status, billable_entry_count, paid_entry_count, updated_at)
            KEY (regatta_id, club_id)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    }

    private boolean isPostgres(Connection conn) throws Exception {
        return "PostgreSQL".equalsIgnoreCase(conn.getMetaData().getDatabaseProductName());
    }

    private Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private <T> T parseEvent(EventEnvelope envelope, Class<T> eventClass) {
        try {
            String payload = envelope.getRawPayload();
            if (payload != null && !payload.isBlank()) {
                return objectMapper.readValue(payload, eventClass);
            }
            return objectMapper.convertValue(envelope.getPayload(), eventClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse finance event payload", e);
        }
    }
}
