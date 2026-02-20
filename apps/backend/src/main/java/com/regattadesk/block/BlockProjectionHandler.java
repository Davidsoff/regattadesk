package com.regattadesk.block;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regattadesk.eventstore.EventEnvelope;
import com.regattadesk.projection.ProjectionHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Projection handler for Block read model.
 * 
 * Transforms block domain events into the blocks read model table.
 */
@ApplicationScoped
public class BlockProjectionHandler implements ProjectionHandler {
    
    private static final Logger LOG = LoggerFactory.getLogger(BlockProjectionHandler.class);
    
    @Inject
    DataSource dataSource;
    
    @Inject
    ObjectMapper objectMapper;
    
    @Override
    public String getProjectionName() {
        return "BlockProjection";
    }
    
    @Override
    public boolean canHandle(EventEnvelope event) {
        return "BlockCreated".equals(event.getEventType()) ||
               "BlockUpdated".equals(event.getEventType()) ||
               "BlockDeleted".equals(event.getEventType());
    }
    
    @Override
    public void handle(EventEnvelope event) {
        if ("BlockCreated".equals(event.getEventType())) {
            handleBlockCreated(event);
        } else if ("BlockUpdated".equals(event.getEventType())) {
            handleBlockUpdated(event);
        } else if ("BlockDeleted".equals(event.getEventType())) {
            handleBlockDeleted(event);
        }
    }
    
    private void handleBlockCreated(EventEnvelope envelope) {
        try {
            BlockCreatedEvent event = parseEvent(envelope, BlockCreatedEvent.class);
            insertBlock(event);
            LOG.debug("Projected BlockCreated event for block {}", event.getBlockId());
        } catch (Exception e) {
            LOG.error("Failed to handle BlockCreated event", e);
            throw new RuntimeException("Failed to handle BlockCreated event", e);
        }
    }
    
    private void handleBlockUpdated(EventEnvelope envelope) {
        try {
            BlockUpdatedEvent event = parseEvent(envelope, BlockUpdatedEvent.class);
            updateBlock(event);
            LOG.debug("Projected BlockUpdated event for block {}", event.getBlockId());
        } catch (Exception e) {
            LOG.error("Failed to handle BlockUpdated event", e);
            throw new RuntimeException("Failed to handle BlockUpdated event", e);
        }
    }
    
    private void handleBlockDeleted(EventEnvelope envelope) {
        try {
            BlockDeletedEvent event = parseEvent(envelope, BlockDeletedEvent.class);
            deleteBlock(event);
            LOG.debug("Projected BlockDeleted event for block {}", event.getBlockId());
        } catch (Exception e) {
            LOG.error("Failed to handle BlockDeleted event", e);
            throw new RuntimeException("Failed to handle BlockDeleted event", e);
        }
    }
    
    private void insertBlock(BlockCreatedEvent event) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertBlockSql(conn))) {
            
            stmt.setObject(1, event.getBlockId());
            stmt.setObject(2, event.getRegattaId());
            stmt.setString(3, event.getName());
            stmt.setTimestamp(4, Timestamp.from(event.getStartTime()));
            stmt.setInt(5, event.getEventIntervalSeconds());
            stmt.setInt(6, event.getCrewIntervalSeconds());
            stmt.setInt(7, event.getDisplayOrder());
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert block into read model", e);
        }
    }
    
    private void updateBlock(BlockUpdatedEvent event) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE blocks SET name = ?, start_time = ?, event_interval_seconds = ?, " +
                 "crew_interval_seconds = ?, display_order = ?, updated_at = now() WHERE id = ?")) {
            
            stmt.setString(1, event.getName());
            stmt.setTimestamp(2, Timestamp.from(event.getStartTime()));
            stmt.setInt(3, event.getEventIntervalSeconds());
            stmt.setInt(4, event.getCrewIntervalSeconds());
            stmt.setInt(5, event.getDisplayOrder());
            stmt.setObject(6, event.getBlockId());
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                LOG.warn("No rows updated for BlockUpdated event, block {} may not exist", event.getBlockId());
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update block in read model", e);
        }
    }
    
    private void deleteBlock(BlockDeletedEvent event) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM blocks WHERE id = ?")) {
            
            stmt.setObject(1, event.getBlockId());
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                LOG.warn("No rows deleted for BlockDeleted event, block {} may not exist", event.getBlockId());
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete block from read model", e);
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
    
    private String insertBlockSql(Connection conn) throws SQLException {
        String databaseName = conn.getMetaData().getDatabaseProductName();
        if ("PostgreSQL".equalsIgnoreCase(databaseName)) {
            return """
                INSERT INTO blocks (id, regatta_id, name, start_time, event_interval_seconds,
                                    crew_interval_seconds, display_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
                ON CONFLICT (id) DO UPDATE SET
                    regatta_id = EXCLUDED.regatta_id,
                    name = EXCLUDED.name,
                    start_time = EXCLUDED.start_time,
                    event_interval_seconds = EXCLUDED.event_interval_seconds,
                    crew_interval_seconds = EXCLUDED.crew_interval_seconds,
                    display_order = EXCLUDED.display_order,
                    updated_at = now()
                """;
        }
        
        return """
            MERGE INTO blocks (id, regatta_id, name, start_time, event_interval_seconds,
                              crew_interval_seconds, display_order, created_at, updated_at)
            KEY (id)
            VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
            """;
    }
}
