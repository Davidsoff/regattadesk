package com.regattadesk.projection;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC-based implementation of ProjectionCheckpointRepository.
 */
@ApplicationScoped
public class JdbcProjectionCheckpointRepository implements ProjectionCheckpointRepository {
    
    @Inject
    DataSource dataSource;
    
    @Override
    public Optional<ProjectionCheckpoint> getCheckpoint(String projectionName) {
        if (projectionName == null || projectionName.isBlank()) {
            throw new IllegalArgumentException("Projection name cannot be null or blank");
        }
        
        String sql = """
            SELECT projection_name, last_processed_event_id, last_processed_at
            FROM projection_checkpoints
            WHERE projection_name = ?
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, projectionName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    UUID eventId = (UUID) rs.getObject("last_processed_event_id");
                    Timestamp timestamp = rs.getTimestamp("last_processed_at");
                    Instant lastProcessedAt = timestamp.toInstant();
                    
                    return Optional.of(new ProjectionCheckpoint(projectionName, eventId, lastProcessedAt));
                }
            }
            
            return Optional.empty();
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get checkpoint for projection " + projectionName, e);
        }
    }
    
    @Override
    public void saveCheckpoint(ProjectionCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("Checkpoint cannot be null");
        }
        
        // Use MERGE for H2 compatibility (works in both H2 and PostgreSQL 15+)
        String sql = """
            MERGE INTO projection_checkpoints (projection_name, last_processed_event_id, last_processed_at, updated_at)
            KEY (projection_name)
            VALUES (?, ?, ?, now())
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, checkpoint.getProjectionName());
            stmt.setObject(2, checkpoint.getLastProcessedEventId());
            stmt.setTimestamp(3, Timestamp.from(checkpoint.getLastProcessedAt()));
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save checkpoint for projection " + checkpoint.getProjectionName(), e);
        }
    }
}
