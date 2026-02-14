package com.regattadesk.linescan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Represents a line-scan manifest containing tile grid metadata and timestamp mapping.
 * 
 * Immutable domain model for line-scan capture session manifests.
 * Tracks tile grid layout, time mapping, and retention state.
 */
public class LineScanManifest {
    
    private final UUID id;
    private final UUID regattaId;
    private final UUID captureSessionId;
    private final int tileSizePx;
    private final String primaryFormat;
    private final String fallbackFormat;
    private final long xOriginTimestampMs;
    private final double msPerPixel;
    private final List<LineScanManifestTile> tiles;
    private final int retentionDays;
    private final int pruneWindowSeconds;
    private final RetentionState retentionState;
    private final Instant pruneEligibleAt;
    private final Instant prunedAt;
    private final Instant createdAt;
    private final Instant updatedAt;
    
    /**
     * Retention state for line-scan data lifecycle management.
     */
    public enum RetentionState {
        FULL_RETAINED("full_retained"),
        PENDING_DELAY("pending_delay"),
        ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS("eligible_waiting_archive_or_approvals"),
        PRUNED("pruned");
        
        private final String value;
        
        RetentionState(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static RetentionState fromValue(String value) {
            for (RetentionState state : values()) {
                if (state.value.equals(value)) {
                    return state;
                }
            }
            throw new IllegalArgumentException("Unknown retention state: " + value);
        }
    }
    
    private LineScanManifest(Builder builder) {
        this.id = builder.id;
        this.regattaId = builder.regattaId;
        this.captureSessionId = builder.captureSessionId;
        this.tileSizePx = builder.tileSizePx;
        this.primaryFormat = builder.primaryFormat;
        this.fallbackFormat = builder.fallbackFormat;
        this.xOriginTimestampMs = builder.xOriginTimestampMs;
        this.msPerPixel = builder.msPerPixel;
        this.tiles = builder.tiles;
        this.retentionDays = builder.retentionDays;
        this.pruneWindowSeconds = builder.pruneWindowSeconds;
        this.retentionState = builder.retentionState;
        this.pruneEligibleAt = builder.pruneEligibleAt;
        this.prunedAt = builder.prunedAt;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }
    
    public UUID getId() {
        return id;
    }
    
    public UUID getRegattaId() {
        return regattaId;
    }
    
    public UUID getCaptureSessionId() {
        return captureSessionId;
    }
    
    public int getTileSizePx() {
        return tileSizePx;
    }
    
    public String getPrimaryFormat() {
        return primaryFormat;
    }
    
    public String getFallbackFormat() {
        return fallbackFormat;
    }
    
    public long getXOriginTimestampMs() {
        return xOriginTimestampMs;
    }
    
    public double getMsPerPixel() {
        return msPerPixel;
    }
    
    public List<LineScanManifestTile> getTiles() {
        return tiles;
    }
    
    public int getRetentionDays() {
        return retentionDays;
    }
    
    public int getPruneWindowSeconds() {
        return pruneWindowSeconds;
    }
    
    public RetentionState getRetentionState() {
        return retentionState;
    }
    
    public Instant getPruneEligibleAt() {
        return pruneEligibleAt;
    }
    
    public Instant getPrunedAt() {
        return prunedAt;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private UUID id;
        private UUID regattaId;
        private UUID captureSessionId;
        private int tileSizePx;
        private String primaryFormat;
        private String fallbackFormat;
        private long xOriginTimestampMs;
        private double msPerPixel;
        private List<LineScanManifestTile> tiles;
        private int retentionDays = 14;
        private int pruneWindowSeconds = 2;
        private RetentionState retentionState = RetentionState.FULL_RETAINED;
        private Instant pruneEligibleAt;
        private Instant prunedAt;
        private Instant createdAt;
        private Instant updatedAt;
        
        public Builder id(UUID id) {
            this.id = id;
            return this;
        }
        
        public Builder regattaId(UUID regattaId) {
            this.regattaId = regattaId;
            return this;
        }
        
        public Builder captureSessionId(UUID captureSessionId) {
            this.captureSessionId = captureSessionId;
            return this;
        }
        
        public Builder tileSizePx(int tileSizePx) {
            this.tileSizePx = tileSizePx;
            return this;
        }
        
        public Builder primaryFormat(String primaryFormat) {
            this.primaryFormat = primaryFormat;
            return this;
        }
        
        public Builder fallbackFormat(String fallbackFormat) {
            this.fallbackFormat = fallbackFormat;
            return this;
        }
        
        public Builder xOriginTimestampMs(long xOriginTimestampMs) {
            this.xOriginTimestampMs = xOriginTimestampMs;
            return this;
        }
        
        public Builder msPerPixel(double msPerPixel) {
            this.msPerPixel = msPerPixel;
            return this;
        }
        
        public Builder tiles(List<LineScanManifestTile> tiles) {
            this.tiles = tiles;
            return this;
        }
        
        public Builder retentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
            return this;
        }
        
        public Builder pruneWindowSeconds(int pruneWindowSeconds) {
            this.pruneWindowSeconds = pruneWindowSeconds;
            return this;
        }
        
        public Builder retentionState(RetentionState retentionState) {
            this.retentionState = retentionState;
            return this;
        }
        
        public Builder pruneEligibleAt(Instant pruneEligibleAt) {
            this.pruneEligibleAt = pruneEligibleAt;
            return this;
        }
        
        public Builder prunedAt(Instant prunedAt) {
            this.prunedAt = prunedAt;
            return this;
        }
        
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }
        
        public LineScanManifest build() {
            return new LineScanManifest(this);
        }
    }
}
