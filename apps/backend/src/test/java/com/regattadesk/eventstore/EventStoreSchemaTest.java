package com.regattadesk.eventstore;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for event store schema and immutability constraints.
 * 
 * Tests verify:
 * - Schema is applied successfully
 * - Append-only constraints prevent updates and deletes
 * - Indexes are created for performance
 * - Event sequence ordering works correctly
 * 
 * Note: Helper methods use String.format() for SQL construction for test simplicity.
 * This is acceptable in test code with controlled inputs (UUIDs, type discriminators).
 * Production code should always use PreparedStatement with parameterized queries.
 */
@QuarkusTest
class EventStoreSchemaTest {

    @Inject
    DataSource dataSource;

    private Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }

    private void insertAggregate(Statement stmt, UUID aggregateId, String type, int version) throws SQLException {
        Timestamp ts = now();
        stmt.executeUpdate(String.format(
            "INSERT INTO aggregates (id, aggregate_type, version, created_at, updated_at) VALUES ('%s', '%s', %d, '%s', '%s')",
            aggregateId, type, version, ts, ts
        ));
    }

    private void insertEvent(Statement stmt, UUID eventId, UUID aggregateId, String eventType, long sequence, String payload) throws SQLException {
        stmt.executeUpdate(String.format(
            "INSERT INTO event_store (id, aggregate_id, event_type, sequence_number, payload, created_at) " +
            "VALUES ('%s', '%s', '%s', %d, '%s', '%s')",
            eventId, aggregateId, eventType, sequence, payload, now()
        ));
    }

    @Test
    void testSchemaTablesExist() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            // Verify aggregates table exists
            try (ResultSet rs = metaData.getTables(null, null, "AGGREGATES", new String[]{"TABLE"})) {
                assertTrue(rs.next(), "aggregates table should exist");
            }
            
            // Verify event_store table exists
            try (ResultSet rs = metaData.getTables(null, null, "EVENT_STORE", new String[]{"TABLE"})) {
                assertTrue(rs.next(), "event_store table should exist");
            }
        }
    }

    @Test
    void testAggregatesTableStructure() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Test that we can insert a valid aggregate
            UUID aggregateId = UUID.randomUUID();
            insertAggregate(stmt, aggregateId, "TestAggregate", 0);
            
            // Verify the aggregate was created with timestamps
            try (ResultSet rs = stmt.executeQuery(
                String.format("SELECT id, aggregate_type, version, created_at, updated_at FROM aggregates WHERE id = '%s'", aggregateId)
            )) {
                assertTrue(rs.next(), "Should retrieve inserted aggregate");
                assertEquals("TestAggregate", rs.getString("aggregate_type"));
                assertEquals(0, rs.getLong("version"));
                assertNotNull(rs.getTimestamp("created_at"), "created_at should be set");
                assertNotNull(rs.getTimestamp("updated_at"), "updated_at should be set");
            }
        }
    }

    @Test
    void testEventStoreTableStructure() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // First create an aggregate
            UUID aggregateId = UUID.randomUUID();
            insertAggregate(stmt, aggregateId, "TestAggregate", 1);
            
            // Insert an event
            UUID eventId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();
            stmt.executeUpdate(String.format(
                "INSERT INTO event_store (id, aggregate_id, event_type, sequence_number, payload, metadata, correlation_id, created_at) " +
                "VALUES ('%s', '%s', 'TestEvent', 1, '{\"data\":\"test\"}', '{\"user\":\"test\"}', '%s', '%s')",
                eventId, aggregateId, correlationId, now()
            ));
            
            // Verify the event structure
            try (ResultSet rs = stmt.executeQuery(
                String.format("SELECT id, aggregate_id, event_type, sequence_number, payload, metadata, correlation_id, created_at FROM event_store WHERE id = '%s'", eventId)
            )) {
                assertTrue(rs.next(), "Should retrieve inserted event");
                assertEquals(aggregateId, UUID.fromString(rs.getString("aggregate_id")));
                assertEquals("TestEvent", rs.getString("event_type"));
                assertEquals(1, rs.getLong("sequence_number"));
                assertEquals("{\"data\":\"test\"}", rs.getString("payload"));
                assertEquals("{\"user\":\"test\"}", rs.getString("metadata"));
                assertEquals(correlationId, UUID.fromString(rs.getString("correlation_id")));
                assertNotNull(rs.getTimestamp("created_at"), "created_at should be set");
            }
        }
    }

    @Test
    void testEventStoreUpdatesAreBlocked() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Create aggregate and event
            UUID aggregateId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            
            insertAggregate(stmt, aggregateId, "TestAggregate", 1);
            insertEvent(stmt, eventId, aggregateId, "TestEvent", 1, "{\"data\":\"original\"}");
            
            // Attempt to update the event should fail
            SQLException exception = assertThrows(SQLException.class, () -> {
                stmt.executeUpdate(String.format(
                    "UPDATE event_store SET payload = '{\"data\":\"modified\"}' WHERE id = '%s'",
                    eventId
                ));
            });
            
            assertTrue(
                exception.getMessage().contains("event_store is append-only") || 
                exception.getMessage().contains("updates are not allowed"),
                "Exception message should indicate updates are blocked: " + exception.getMessage()
            );
            
            // Verify original data is unchanged
            try (ResultSet rs = stmt.executeQuery(
                String.format("SELECT payload FROM event_store WHERE id = '%s'", eventId)
            )) {
                assertTrue(rs.next());
                assertEquals("{\"data\":\"original\"}", rs.getString("payload"));
            }
        }
    }

    @Test
    void testEventStoreDeletesAreBlocked() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Create aggregate and event
            UUID aggregateId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            
            insertAggregate(stmt, aggregateId, "TestAggregate", 1);
            insertEvent(stmt, eventId, aggregateId, "TestEvent", 1, "{\"data\":\"test\"}");
            
            // Attempt to delete the event should fail
            SQLException exception = assertThrows(SQLException.class, () -> {
                stmt.executeUpdate(String.format(
                    "DELETE FROM event_store WHERE id = '%s'",
                    eventId
                ));
            });
            
            assertTrue(
                exception.getMessage().contains("event_store is immutable") || 
                exception.getMessage().contains("deletes are not allowed"),
                "Exception message should indicate deletes are blocked: " + exception.getMessage()
            );
            
            // Verify event still exists
            try (ResultSet rs = stmt.executeQuery(
                String.format("SELECT COUNT(*) FROM event_store WHERE id = '%s'", eventId)
            )) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Event should still exist");
            }
        }
    }

    @Test
    void testEventSequenceUniqueness() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Create aggregate
            UUID aggregateId = UUID.randomUUID();
            insertAggregate(stmt, aggregateId, "TestAggregate", 2);
            
            // Insert first event with sequence 1
            insertEvent(stmt, UUID.randomUUID(), aggregateId, "TestEvent1", 1, "{\"data\":\"event1\"}");
            
            // Insert second event with sequence 2
            insertEvent(stmt, UUID.randomUUID(), aggregateId, "TestEvent2", 2, "{\"data\":\"event2\"}");
            
            // Attempt to insert duplicate sequence number should fail
            SQLException exception = assertThrows(SQLException.class, () -> {
                insertEvent(stmt, UUID.randomUUID(), aggregateId, "TestEvent3", 1, "{\"data\":\"event3\"}");
            });
            
            assertTrue(
                exception.getMessage().contains("duplicate key") || 
                exception.getMessage().contains("unique") ||
                exception.getMessage().contains("Unique index"),
                "Should fail with unique constraint violation: " + exception.getMessage()
            );
        }
    }

    @Test
    void testIndexesExist() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            // Check for key indexes on event_store
            String[] expectedIndexes = {
                "IDX_EVENT_STORE_AGGREGATE",
                "IDX_EVENT_STORE_TYPE",
                "IDX_EVENT_STORE_CREATED",
                "IDX_EVENT_STORE_CORRELATION",
                "IDX_EVENT_STORE_AGGREGATE_SEQUENCE"
            };
            
            for (String indexName : expectedIndexes) {
                try (ResultSet rs = metaData.getIndexInfo(null, null, "EVENT_STORE", false, false)) {
                    boolean indexFound = false;
                    while (rs.next()) {
                        if (indexName.equals(rs.getString("INDEX_NAME"))) {
                            indexFound = true;
                            break;
                        }
                    }
                    assertTrue(indexFound, "Index " + indexName + " should exist on event_store table");
                }
            }
        }
    }

    @Test
    void testAggregateUpdatedAtNotAutomatic() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Create aggregate
            UUID aggregateId = UUID.randomUUID();
            Timestamp ts1 = now();
            stmt.executeUpdate(String.format(
                "INSERT INTO aggregates (id, aggregate_type, version, created_at, updated_at) VALUES ('%s', 'TestAggregate', 0, '%s', '%s')",
                aggregateId, ts1, ts1
            ));
            
            // Update the aggregate version - H2 doesn't have auto-update trigger like PostgreSQL
            Timestamp ts2 = now();
            stmt.executeUpdate(String.format(
                "UPDATE aggregates SET version = 1, updated_at = '%s' WHERE id = '%s'",
                ts2, aggregateId
            ));
            
            // Verify version updated
            try (ResultSet rs = stmt.executeQuery(
                String.format("SELECT version FROM aggregates WHERE id = '%s'", aggregateId)
            )) {
                assertTrue(rs.next());
                assertEquals(1, rs.getLong("version"));
            }
        }
    }
}
