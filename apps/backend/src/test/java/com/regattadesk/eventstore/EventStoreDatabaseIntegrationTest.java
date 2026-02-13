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
}
