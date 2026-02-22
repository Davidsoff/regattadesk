package com.regattadesk.entry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regattadesk.eventstore.EventEnvelope;
import com.regattadesk.projection.ProjectionHandler;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Projection handler for Entry aggregate (BC03-004, BC08-001).
 * 
 * Maintains the entries read model from entry domain events.
 */
@ApplicationScoped
public class EntryProjectionHandler implements ProjectionHandler {

    @Inject
    DataSource dataSource;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public String getProjectionName() {
        return "entry_projection";
    }

    @Override
    public boolean canHandle(EventEnvelope event) {
        String eventType = event.getEventType();
        return "EntryCreated".equals(eventType) ||
               "EntryCreatedEvent".equals(eventType) ||
               "EntryPaymentStatusUpdatedEvent".equals(eventType);
    }

    @Override
    public void handle(EventEnvelope envelope) {
        var eventType = envelope.getEventType();

        try {
            switch (eventType) {
                case "EntryCreated", "EntryCreatedEvent" -> handleEntryCreated(envelope);
                case "EntryPaymentStatusUpdatedEvent" -> handlePaymentStatusUpdated(envelope);
                default -> Log.debugf("Ignoring event type: %s", eventType);
            }
        } catch (Exception e) {
            Log.errorf(e, "Failed to handle event type: %s", eventType);
            throw new RuntimeException("Failed to handle event: " + eventType, e);
        }
    }

    private void handleEntryCreated(EventEnvelope envelope) throws Exception {
        var event = parseEvent(envelope, EntryCreatedEvent.class);

        try (Connection conn = dataSource.getConnection()) {
            String sql = insertEntrySql(conn);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                var now = Timestamp.from(Instant.now());
                stmt.setObject(1, event.getEntryId());
                stmt.setObject(2, event.getRegattaId());
                stmt.setObject(3, event.getEventId());
                stmt.setObject(4, event.getBlockId());
                stmt.setObject(5, event.getCrewId());
                stmt.setObject(6, event.getBillingClubId());
                stmt.setString(7, "entered");
                stmt.setString(8, "unpaid");
                stmt.setTimestamp(9, null); // paid_at
                stmt.setString(10, null); // paid_by
                stmt.setString(11, null); // payment_reference
                stmt.setTimestamp(12, now);
                stmt.setTimestamp(13, now);
                stmt.executeUpdate();
            }
        }
    }

    private void handlePaymentStatusUpdated(EventEnvelope envelope) throws Exception {
        var event = parseEvent(envelope, EntryPaymentStatusUpdatedEvent.class);

        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                UPDATE entries
                SET payment_status = ?, paid_at = ?, paid_by = ?, payment_reference = ?, updated_at = ?
                WHERE id = ?
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                var now = Timestamp.from(Instant.now());
                stmt.setString(1, event.getPaymentStatus());
                stmt.setTimestamp(2, event.getPaidAt() != null ? Timestamp.from(event.getPaidAt()) : null);
                stmt.setString(3, event.getPaidBy());
                stmt.setString(4, event.getPaymentReference());
                stmt.setTimestamp(5, now);
                stmt.setObject(6, event.getEntryId());
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    Log.warnf(
                        "Payment status update affected 0 rows for entry %s (eventId=%s, status=%s)",
                        event.getEntryId(),
                        envelope.getEventId(),
                        event.getPaymentStatus()
                    );
                }
            }
        }
    }

    private <T> T parseEvent(EventEnvelope envelope, Class<T> eventClass) {
        try {
            String payload = envelope.getRawPayload();
            if (payload != null && !payload.isBlank()) {
                return objectMapper.readValue(payload, eventClass);
            }

            return objectMapper.convertValue(envelope.getPayload(), eventClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse event payload", e);
        }
    }

    private String insertEntrySql(Connection conn) throws SQLException {
        String databaseName = conn.getMetaData().getDatabaseProductName();
        if ("PostgreSQL".equalsIgnoreCase(databaseName)) {
            return """
                INSERT INTO entries (id, regatta_id, event_id, block_id, crew_id, billing_club_id, status, payment_status, paid_at, paid_by, payment_reference, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    regatta_id = EXCLUDED.regatta_id,
                    event_id = EXCLUDED.event_id,
                    block_id = EXCLUDED.block_id,
                    crew_id = EXCLUDED.crew_id,
                    billing_club_id = EXCLUDED.billing_club_id,
                    status = EXCLUDED.status,
                    payment_status = EXCLUDED.payment_status,
                    paid_at = EXCLUDED.paid_at,
                    paid_by = EXCLUDED.paid_by,
                    payment_reference = EXCLUDED.payment_reference,
                    updated_at = EXCLUDED.updated_at
                """;
        }

        return """
            MERGE INTO entries (id, regatta_id, event_id, block_id, crew_id, billing_club_id, status, payment_status, paid_at, paid_by, payment_reference, created_at, updated_at)
            KEY (id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    }
}
