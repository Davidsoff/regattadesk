package com.regattadesk.projection;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing projection checkpoints.
 */
public interface ProjectionCheckpointRepository {
    
    /**
     * Gets the checkpoint for a projection.
     * 
     * @param projectionName the projection name
     * @return the checkpoint, or empty if none exists
     */
    Optional<ProjectionCheckpoint> getCheckpoint(String projectionName);
    
    /**
     * Saves or updates a projection checkpoint.
     * 
     * @param checkpoint the checkpoint to save
     */
    void saveCheckpoint(ProjectionCheckpoint checkpoint);
    
    /**
     * Gets the last processed event ID for a projection.
     * 
     * @param projectionName the projection name
     * @return the last processed event ID, or empty if none exists
     */
    default Optional<UUID> getLastProcessedEventId(String projectionName) {
        return getCheckpoint(projectionName)
                .map(ProjectionCheckpoint::getLastProcessedEventId);
    }
}
