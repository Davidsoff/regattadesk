package com.regattadesk.projection;

import java.time.Instant;
import java.util.UUID;

/**
 * Checkpoint model for tracking projection progress.
 * 
 * Each projection maintains a checkpoint that records the last processed event.
 * This enables idempotent replay and resumption after restarts.
 */
public class ProjectionCheckpoint {
    
    private final String projectionName;
    private final UUID lastProcessedEventId;
    private final Instant lastProcessedAt;
    
    public ProjectionCheckpoint(String projectionName, UUID lastProcessedEventId, Instant lastProcessedAt) {
        if (projectionName == null || projectionName.isBlank()) {
            throw new IllegalArgumentException("Projection name cannot be null or blank");
        }
        if (lastProcessedEventId == null) {
            throw new IllegalArgumentException("Last processed event ID cannot be null");
        }
        if (lastProcessedAt == null) {
            throw new IllegalArgumentException("Last processed at cannot be null");
        }
        
        this.projectionName = projectionName;
        this.lastProcessedEventId = lastProcessedEventId;
        this.lastProcessedAt = lastProcessedAt;
    }
    
    public String getProjectionName() {
        return projectionName;
    }
    
    public UUID getLastProcessedEventId() {
        return lastProcessedEventId;
    }
    
    public Instant getLastProcessedAt() {
        return lastProcessedAt;
    }
}
