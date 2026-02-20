package com.regattadesk.eventstore;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test demonstrating PostgreSQL integration testing pattern.
 * 
 * This test uses the default Quarkus test profile which connects to H2 for now.
 * In a full implementation, use Testcontainers with PostgreSQL:
 * 
 * @TestProfile(PostgresTestProfile.class)
 * 
 * Where PostgresTestProfile configures:
 * - @TestcontainersResource PostgreSQLContainer
 * - Database connection properties
 * - Flyway migrations
 */
@QuarkusTest
class EventStoreDatabaseIntegrationTest {

    @Inject
    DataSource dataSource;

    @Test
    void shouldConnectToDatabase() throws Exception {
        assertNotNull(dataSource, "DataSource should be injected");
        
        try (Connection conn = dataSource.getConnection()) {
            assertTrue(conn.isValid(5), "Database connection should be valid");
        }
    }

    @Test
    void shouldHaveFlywaySchemaVersionTable() throws Exception {
        // Verify Flyway schema version table exists
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "flyway_schema_history", null)) {
            assertTrue(rs.next(), "flyway_schema_history table should exist");
        }
    }

    @Test
    void shouldHaveEventStoreSchema() throws Exception {
        // Verify event store table exists (from Flyway migration)
        // Note: H2 uses uppercase table names by default
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "EVENT_STORE", null)) {
            assertTrue(rs.next(), "EVENT_STORE table should exist");
        }
    }

    @Test
    void shouldHaveCorrectEventStoreColumns() throws Exception {
        // Verify key columns exist in event_store table
        // Note: H2 uses uppercase column names by default
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getColumns(null, null, "EVENT_STORE", null)) {
            
            boolean hasIdColumn = false;
            boolean hasAggregateIdColumn = false;
            boolean hasEventTypeColumn = false;
            boolean hasCreatedAtColumn = false;
            
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                if ("ID".equalsIgnoreCase(columnName)) hasIdColumn = true;
                if ("AGGREGATE_ID".equalsIgnoreCase(columnName)) hasAggregateIdColumn = true;
                if ("EVENT_TYPE".equalsIgnoreCase(columnName)) hasEventTypeColumn = true;
                if ("CREATED_AT".equalsIgnoreCase(columnName)) hasCreatedAtColumn = true;
            }
            
            assertTrue(hasIdColumn, "EVENT_STORE should have ID column");
            assertTrue(hasAggregateIdColumn, "EVENT_STORE should have AGGREGATE_ID column");
            assertTrue(hasEventTypeColumn, "EVENT_STORE should have EVENT_TYPE column");
            assertTrue(hasCreatedAtColumn, "EVENT_STORE should have CREATED_AT column");
        }
    }

    @Test
    void shouldEnforceUniqueSequencePerAggregateStream() throws Exception {
        UUID aggregateId = UUID.randomUUID();
        UUID firstEventId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement insertAggregate = conn.prepareStatement(
                "INSERT INTO aggregates (id, aggregate_type, version, created_at, updated_at) VALUES (?, ?, ?, ?, ?)"
            )) {
                insertAggregate.setObject(1, aggregateId);
                insertAggregate.setString(2, "TestAggregate");
                insertAggregate.setLong(3, 0L);
                insertAggregate.setTimestamp(4, now);
                insertAggregate.setTimestamp(5, now);
                insertAggregate.executeUpdate();
            }

            try (PreparedStatement insertEvent = conn.prepareStatement(
                "INSERT INTO event_store (id, aggregate_id, event_type, sequence_number, payload, metadata, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)"
            )) {
                insertEvent.setObject(1, firstEventId);
                insertEvent.setObject(2, aggregateId);
                insertEvent.setString(3, "TestEventCreated");
                insertEvent.setLong(4, 1L);
                insertEvent.setString(5, "{\"value\":1}");
                insertEvent.setString(6, "{}");
                insertEvent.setTimestamp(7, now);
                insertEvent.executeUpdate();

                insertEvent.setObject(1, secondEventId);
                insertEvent.setObject(2, aggregateId);
                insertEvent.setString(3, "TestEventUpdated");
                insertEvent.setLong(4, 1L);
                insertEvent.setString(5, "{\"value\":2}");
                insertEvent.setString(6, "{}");
                insertEvent.setTimestamp(7, now);

                SQLException exception = assertThrows(SQLException.class, insertEvent::executeUpdate);
                assertTrue(
                    exception.getMessage().toLowerCase().contains("unique"),
                    "Duplicate sequence should fail with unique constraint violation"
                );
            }
        }
    }

    @Test
    void shouldEnforceAggregateForeignKey() throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement insertEvent = conn.prepareStatement(
                 "INSERT INTO event_store (id, aggregate_id, event_type, sequence_number, payload, metadata, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)"
             )) {
            insertEvent.setObject(1, UUID.randomUUID());
            insertEvent.setObject(2, UUID.randomUUID());
            insertEvent.setString(3, "OrphanEvent");
            insertEvent.setLong(4, 1L);
            insertEvent.setString(5, "{\"value\":1}");
            insertEvent.setString(6, "{}");
            insertEvent.setTimestamp(7, new Timestamp(System.currentTimeMillis()));

            SQLException exception = assertThrows(SQLException.class, insertEvent::executeUpdate);
            assertTrue(
                exception.getMessage().toLowerCase().contains("referential")
                    || exception.getMessage().toLowerCase().contains("foreign key"),
                "Orphan event insert should fail due to aggregate foreign key"
            );
        }
    }
}
