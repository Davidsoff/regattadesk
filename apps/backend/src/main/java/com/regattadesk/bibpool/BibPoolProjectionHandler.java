package com.regattadesk.bibpool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regattadesk.eventstore.EventEnvelope;
import com.regattadesk.projection.ProjectionHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.util.UUID;

/**
 * Projection handler for BibPool read model.
 * 
 * Transforms bib pool domain events into the bib_pools read model table.
 */
@ApplicationScoped
public class BibPoolProjectionHandler implements ProjectionHandler {
    
    private static final Logger LOG = LoggerFactory.getLogger(BibPoolProjectionHandler.class);
    
    @Inject
    DataSource dataSource;
    
    @Inject
    ObjectMapper objectMapper;
    
    @Override
    public String getProjectionName() {
        return "BibPoolProjection";
    }
    
    @Override
    public boolean canHandle(EventEnvelope event) {
        return "BibPoolCreated".equals(event.getEventType()) ||
               "BibPoolUpdated".equals(event.getEventType()) ||
               "BibPoolDeleted".equals(event.getEventType());
    }
    
    @Override
    public void handle(EventEnvelope event) {
        if ("BibPoolCreated".equals(event.getEventType())) {
            handleBibPoolCreated(event);
        } else if ("BibPoolUpdated".equals(event.getEventType())) {
            handleBibPoolUpdated(event);
        } else if ("BibPoolDeleted".equals(event.getEventType())) {
            handleBibPoolDeleted(event);
        }
    }
    
    private void handleBibPoolCreated(EventEnvelope envelope) {
        try {
            BibPoolCreatedEvent event = parseEvent(envelope, BibPoolCreatedEvent.class);
            insertBibPool(event);
            LOG.debug("Projected BibPoolCreated event for pool {}", event.getPoolId());
        } catch (Exception e) {
            LOG.error("Failed to handle BibPoolCreated event", e);
            throw new RuntimeException("Failed to handle BibPoolCreated event", e);
        }
    }
    
    private void handleBibPoolUpdated(EventEnvelope envelope) {
        try {
            BibPoolUpdatedEvent event = parseEvent(envelope, BibPoolUpdatedEvent.class);
            updateBibPool(event);
            LOG.debug("Projected BibPoolUpdated event for pool {}", event.getPoolId());
        } catch (Exception e) {
            LOG.error("Failed to handle BibPoolUpdated event", e);
            throw new RuntimeException("Failed to handle BibPoolUpdated event", e);
        }
    }
    
    private void handleBibPoolDeleted(EventEnvelope envelope) {
        try {
            BibPoolDeletedEvent event = parseEvent(envelope, BibPoolDeletedEvent.class);
            deleteBibPool(event);
            LOG.debug("Projected BibPoolDeleted event for pool {}", event.getPoolId());
        } catch (Exception e) {
            LOG.error("Failed to handle BibPoolDeleted event", e);
            throw new RuntimeException("Failed to handle BibPoolDeleted event", e);
        }
    }
    
    private void insertBibPool(BibPoolCreatedEvent event) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertBibPoolSql(conn))) {
            
            stmt.setObject(1, event.getPoolId());
            stmt.setObject(2, event.getRegattaId());
            stmt.setObject(3, event.getBlockId());
            stmt.setString(4, event.getName());
            stmt.setString(5, event.getAllocationMode());
            
            if (event.getStartBib() != null) {
                stmt.setInt(6, event.getStartBib());
            } else {
                stmt.setNull(6, Types.INTEGER);
            }
            
            if (event.getEndBib() != null) {
                stmt.setInt(7, event.getEndBib());
            } else {
                stmt.setNull(7, Types.INTEGER);
            }
            
            // Handle bib_numbers array
            if (event.getBibNumbers() != null) {
                Array bibNumbersArray = conn.createArrayOf("INTEGER", event.getBibNumbers().toArray());
                stmt.setArray(8, bibNumbersArray);
            } else {
                stmt.setNull(8, Types.ARRAY);
            }
            
            stmt.setInt(9, event.getPriority());
            stmt.setBoolean(10, event.isOverflow());
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert bib pool into read model", e);
        }
    }
    
    private void updateBibPool(BibPoolUpdatedEvent event) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE bib_pools SET name = ?, allocation_mode = ?, start_bib = ?, " +
                 "end_bib = ?, bib_numbers = ?, priority = ?, updated_at = now() WHERE id = ?")) {
            
            stmt.setString(1, event.getName());
            stmt.setString(2, event.getAllocationMode());
            
            if (event.getStartBib() != null) {
                stmt.setInt(3, event.getStartBib());
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            
            if (event.getEndBib() != null) {
                stmt.setInt(4, event.getEndBib());
            } else {
                stmt.setNull(4, Types.INTEGER);
            }
            
            // Handle bib_numbers array
            if (event.getBibNumbers() != null) {
                Array bibNumbersArray = conn.createArrayOf("INTEGER", event.getBibNumbers().toArray());
                stmt.setArray(5, bibNumbersArray);
            } else {
                stmt.setNull(5, Types.ARRAY);
            }
            
            stmt.setInt(6, event.getPriority());
            stmt.setObject(7, event.getPoolId());
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                LOG.warn("No rows updated for BibPoolUpdated event, pool {} may not exist", event.getPoolId());
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update bib pool in read model", e);
        }
    }
    
    private void deleteBibPool(BibPoolDeletedEvent event) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM bib_pools WHERE id = ?")) {
            
            stmt.setObject(1, event.getPoolId());
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                LOG.warn("No rows deleted for BibPoolDeleted event, pool {} may not exist", event.getPoolId());
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete bib pool from read model", e);
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
    
    private String insertBibPoolSql(Connection conn) throws SQLException {
        String databaseName = conn.getMetaData().getDatabaseProductName();
        if ("PostgreSQL".equalsIgnoreCase(databaseName)) {
            return """
                INSERT INTO bib_pools (id, regatta_id, block_id, name, allocation_mode,
                                       start_bib, end_bib, bib_numbers, priority, is_overflow,
                                       created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                ON CONFLICT (id) DO UPDATE SET
                    regatta_id = EXCLUDED.regatta_id,
                    block_id = EXCLUDED.block_id,
                    name = EXCLUDED.name,
                    allocation_mode = EXCLUDED.allocation_mode,
                    start_bib = EXCLUDED.start_bib,
                    end_bib = EXCLUDED.end_bib,
                    bib_numbers = EXCLUDED.bib_numbers,
                    priority = EXCLUDED.priority,
                    is_overflow = EXCLUDED.is_overflow,
                    updated_at = now()
                """;
        }
        
        return """
            MERGE INTO bib_pools (id, regatta_id, block_id, name, allocation_mode,
                                 start_bib, end_bib, bib_numbers, priority, is_overflow,
                                 created_at, updated_at)
            KEY (id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """;
    }
}
