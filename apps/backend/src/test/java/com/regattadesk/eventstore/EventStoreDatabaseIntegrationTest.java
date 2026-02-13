package com.regattadesk.eventstore;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

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
class EventStoreIntegrationTest {

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
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "event_store", null)) {
            assertTrue(rs.next(), "event_store table should exist");
        }
    }

    @Test
    void shouldHaveCorrectEventStoreColumns() throws Exception {
        // Verify key columns exist in event_store table
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getColumns(null, null, "event_store", null)) {
            
            boolean hasEventIdColumn = false;
            boolean hasAggregateIdColumn = false;
            boolean hasEventTypeColumn = false;
            boolean hasTimestampColumn = false;
            
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                if ("event_id".equalsIgnoreCase(columnName)) hasEventIdColumn = true;
                if ("aggregate_id".equalsIgnoreCase(columnName)) hasAggregateIdColumn = true;
                if ("event_type".equalsIgnoreCase(columnName)) hasEventTypeColumn = true;
                if ("timestamp".equalsIgnoreCase(columnName)) hasTimestampColumn = true;
            }
            
            assertTrue(hasEventIdColumn, "event_store should have event_id column");
            assertTrue(hasAggregateIdColumn, "event_store should have aggregate_id column");
            assertTrue(hasEventTypeColumn, "event_store should have event_type column");
            assertTrue(hasTimestampColumn, "event_store should have timestamp column");
        }
    }
}
