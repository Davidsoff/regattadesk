package com.regattadesk.linescan.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.linescan.LineScanManifest;
import com.regattadesk.linescan.LineScanManifestTile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Response DTO for line-scan manifest.
 */
public class LineScanManifestResponse {
    
    private UUID id;
    
    @JsonProperty("regatta_id")
    private UUID regattaId;
    
    @JsonProperty("capture_session_id")
    private UUID captureSessionId;
    
    @JsonProperty("tile_size_px")
    private int tileSizePx;
    
    @JsonProperty("primary_format")
    private String primaryFormat;
    
    @JsonProperty("fallback_format")
    private String fallbackFormat;
    
    @JsonProperty("x_origin_timestamp_ms")
    private long xOriginTimestampMs;
    
    @JsonProperty("ms_per_pixel")
    private double msPerPixel;
    
    private List<TileDto> tiles;
    
    @JsonProperty("retention_days")
    private int retentionDays;
    
    @JsonProperty("prune_window_seconds")
    private int pruneWindowSeconds;
    
    @JsonProperty("retention_state")
    private String retentionState;
    
    @JsonProperty("prune_eligible_at")
    private Instant pruneEligibleAt;
    
    @JsonProperty("pruned_at")
    private Instant prunedAt;
    
    public LineScanManifestResponse() {
    }
    
    public LineScanManifestResponse(LineScanManifest manifest) {
        this.id = manifest.getId();
        this.regattaId = manifest.getRegattaId();
        this.captureSessionId = manifest.getCaptureSessionId();
        this.tileSizePx = manifest.getTileSizePx();
        this.primaryFormat = manifest.getPrimaryFormat();
        this.fallbackFormat = manifest.getFallbackFormat();
        this.xOriginTimestampMs = manifest.getXOriginTimestampMs();
        this.msPerPixel = manifest.getMsPerPixel();
        this.tiles = manifest.getTiles().stream()
            .map(TileDto::new)
            .collect(Collectors.toList());
        this.retentionDays = manifest.getRetentionDays();
        this.pruneWindowSeconds = manifest.getPruneWindowSeconds();
        this.retentionState = manifest.getRetentionState().getValue();
        this.pruneEligibleAt = manifest.getPruneEligibleAt();
        this.prunedAt = manifest.getPrunedAt();
    }
    
    // Getters and setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getRegattaId() {
        return regattaId;
    }
    
    public void setRegattaId(UUID regattaId) {
        this.regattaId = regattaId;
    }
    
    public UUID getCaptureSessionId() {
        return captureSessionId;
    }
    
    public void setCaptureSessionId(UUID captureSessionId) {
        this.captureSessionId = captureSessionId;
    }
    
    public int getTileSizePx() {
        return tileSizePx;
    }
    
    public void setTileSizePx(int tileSizePx) {
        this.tileSizePx = tileSizePx;
    }
    
    public String getPrimaryFormat() {
        return primaryFormat;
    }
    
    public void setPrimaryFormat(String primaryFormat) {
        this.primaryFormat = primaryFormat;
    }
    
    public String getFallbackFormat() {
        return fallbackFormat;
    }
    
    public void setFallbackFormat(String fallbackFormat) {
        this.fallbackFormat = fallbackFormat;
    }
    
    public long getXOriginTimestampMs() {
        return xOriginTimestampMs;
    }
    
    public void setXOriginTimestampMs(long xOriginTimestampMs) {
        this.xOriginTimestampMs = xOriginTimestampMs;
    }
    
    public double getMsPerPixel() {
        return msPerPixel;
    }
    
    public void setMsPerPixel(double msPerPixel) {
        this.msPerPixel = msPerPixel;
    }
    
    public List<TileDto> getTiles() {
        return tiles;
    }
    
    public void setTiles(List<TileDto> tiles) {
        this.tiles = tiles;
    }
    
    public int getRetentionDays() {
        return retentionDays;
    }
    
    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
    
    public int getPruneWindowSeconds() {
        return pruneWindowSeconds;
    }
    
    public void setPruneWindowSeconds(int pruneWindowSeconds) {
        this.pruneWindowSeconds = pruneWindowSeconds;
    }
    
    public String getRetentionState() {
        return retentionState;
    }
    
    public void setRetentionState(String retentionState) {
        this.retentionState = retentionState;
    }
    
    public Instant getPruneEligibleAt() {
        return pruneEligibleAt;
    }
    
    public void setPruneEligibleAt(Instant pruneEligibleAt) {
        this.pruneEligibleAt = pruneEligibleAt;
    }
    
    public Instant getPrunedAt() {
        return prunedAt;
    }
    
    public void setPrunedAt(Instant prunedAt) {
        this.prunedAt = prunedAt;
    }
    
    public static class TileDto {
        @JsonProperty("tile_id")
        private String tileId;
        
        @JsonProperty("tile_x")
        private int tileX;
        
        @JsonProperty("tile_y")
        private int tileY;
        
        @JsonProperty("content_type")
        private String contentType;
        
        @JsonProperty("byte_size")
        private Integer byteSize;
        
        public TileDto() {
        }
        
        public TileDto(LineScanManifestTile tile) {
            this.tileId = tile.getTileId();
            this.tileX = tile.getTileX();
            this.tileY = tile.getTileY();
            this.contentType = tile.getContentType();
            this.byteSize = tile.getByteSize();
        }
        
        public String getTileId() {
            return tileId;
        }
        
        public void setTileId(String tileId) {
            this.tileId = tileId;
        }
        
        public int getTileX() {
            return tileX;
        }
        
        public void setTileX(int tileX) {
            this.tileX = tileX;
        }
        
        public int getTileY() {
            return tileY;
        }
        
        public void setTileY(int tileY) {
            this.tileY = tileY;
        }
        
        public String getContentType() {
            return contentType;
        }
        
        public void setContentType(String contentType) {
            this.contentType = contentType;
        }
        
        public Integer getByteSize() {
            return byteSize;
        }
        
        public void setByteSize(Integer byteSize) {
            this.byteSize = byteSize;
        }
    }
}
