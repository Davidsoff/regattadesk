package com.regattadesk.regatta;

import com.regattadesk.eventstore.DomainEvent;
import com.regattadesk.eventstore.EventEnvelope;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RegattaProjectionHandler.
 *
 * Verifies that handling a RegattaCreated event inserts a read-model row and
 * that handling a subsequent DrawPublished event updates draw_revision and
 * draw_seed in the regattas table.
 */
@QuarkusTest
class RegattaProjectionHandlerIntegrationTest {

    @Inject
    RegattaProjectionHandler regattaProjectionHandler;

    @Inject
    DataSource dataSource;

    @Test
    void testDrawPublishedEventUpdatesReadModel() throws Exception {
        UUID regattaId = UUID.randomUUID();

        // Handle RegattaCreated so the read-model row exists
        RegattaCreatedEvent created = new RegattaCreatedEvent(
                regattaId,
                "Test Regatta",
                "Description",
                "Europe/Amsterdam",
                new BigDecimal("50.00"),
                "EUR",
                60,
                false
        );
        regattaProjectionHandler.handle(envelope(created));

        // Handle DrawPublished and verify draw_revision and draw_seed are updated
        DrawPublishedEvent drawPublished = new DrawPublishedEvent(regattaId, 987654321L, 1);
        regattaProjectionHandler.handle(envelope(drawPublished));

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT draw_revision, draw_seed FROM regattas WHERE id = ?")) {
            stmt.setObject(1, regattaId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next(), "Expected a row in regattas for regattaId " + regattaId);
                assertEquals(1, rs.getInt("draw_revision"));
                assertEquals(987654321L, rs.getLong("draw_seed"));
            }
        }
    }

    private EventEnvelope envelope(DomainEvent event) {
        return EventEnvelope.builder()
                .eventId(UUID.randomUUID())
                .aggregateId(event.getAggregateId())
                .aggregateType("Regatta")
                .eventType(event.getEventType())
                .sequenceNumber(1)
                .payload(event)
                .createdAt(Instant.now())
                .build();
    }
}
