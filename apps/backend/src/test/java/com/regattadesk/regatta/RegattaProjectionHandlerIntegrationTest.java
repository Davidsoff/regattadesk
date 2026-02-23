package com.regattadesk.regatta;

import com.regattadesk.eventstore.EventMetadata;
import com.regattadesk.eventstore.EventStore;
import com.regattadesk.projection.ProjectionWorker;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RegattaProjectionHandler.
 *
 * Verifies that projecting a DrawPublished event updates draw_revision and
 * draw_seed in the regattas read-model table.
 */
@QuarkusTest
class RegattaProjectionHandlerIntegrationTest {

    @Inject
    EventStore eventStore;

    @Inject
    ProjectionWorker projectionWorker;

    @Inject
    RegattaProjectionHandler regattaProjectionHandler;

    @Inject
    DataSource dataSource;

    @Test
    @Transactional
    void testDrawPublishedEventUpdatesReadModel() throws Exception {
        UUID regattaId = UUID.randomUUID();

        // Append RegattaCreated so the read-model row exists
        RegattaCreatedEvent created = new RegattaCreatedEvent(
                regattaId,
                "Test Regatta",
                "Description",
                "Europe/Amsterdam",
                new BigDecimal("50.00"),
                "EUR"
        );
        eventStore.append(regattaId, "Regatta", -1,
                List.of(created), EventMetadata.builder().build());

        // Project RegattaCreated first
        projectionWorker.processProjection(regattaProjectionHandler);

        // Append DrawPublished event
        DrawPublishedEvent drawPublished = new DrawPublishedEvent(regattaId, 987654321L, 1);
        eventStore.append(regattaId, "Regatta", 0,
                List.of(drawPublished), EventMetadata.builder().build());

        // Project the DrawPublished event
        projectionWorker.processProjection(regattaProjectionHandler);

        // Verify the regattas table is updated
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
}
