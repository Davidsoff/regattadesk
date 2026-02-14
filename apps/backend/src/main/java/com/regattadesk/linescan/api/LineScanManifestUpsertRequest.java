package com.regattadesk.linescan.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for upserting a line-scan manifest.
 */
public class LineScanManifestUpsertRequest {
    
    @NotNull(message = "capture_session_id is required")
    @JsonProperty("capture_session_id")
    private UUID captureSessionId;
    
    @NotNull(message = "tile_size_px is required")
    @JsonProperty("tile_size_px")
    private Integer tileSizePx;
    
    @NotNull(message = "primary_format is required")
    @JsonProperty("primary_format")
    private String primaryFormat;
    
    @JsonProperty("fallback_format")
    private String fallbackFormat;
    
    @NotNull(message = "x_origin_timestamp_ms is required")
    @JsonProperty("x_origin_timestamp_ms")
    private Long xOriginTimestampMs;
    
    @NotNull(message = "ms_per_pixel is required")
    @JsonProperty("ms_per_pixel")
    private Double msPerPixel;
    
    @NotEmpty(message = "tiles array cannot be empty")
    @Valid
    private List<TileDto> tiles;
    
    public UUID getCaptureSessionId() {
        return captureSessionId;
    }
    
    public void setCaptureSessionId(UUID captureSessionId) {
        this.captureSessionId = captureSessionId;
    }
    
    public Integer getTileSizePx() {
        return tileSizePx;
    }
    
    public void setTileSizePx(Integer tileSizePx) {
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
    
    public Long getXOriginTimestampMs() {
        return xOriginTimestampMs;
    }
    
    public void setXOriginTimestampMs(Long xOriginTimestampMs) {
        this.xOriginTimestampMs = xOriginTimestampMs;
    }
    
    public Double getMsPerPixel() {
        return msPerPixel;
    }
    
    public void setMsPerPixel(Double msPerPixel) {
        this.msPerPixel = msPerPixel;
    }
    
    public List<TileDto> getTiles() {
        return tiles;
    }
    
    public void setTiles(List<TileDto> tiles) {
        this.tiles = tiles;
    }
    
    public static class TileDto {
        @NotNull(message = "tile_id is required")
        @JsonProperty("tile_id")
        private String tileId;
        
        @NotNull(message = "tile_x is required")
        @JsonProperty("tile_x")
        private Integer tileX;
        
        @NotNull(message = "tile_y is required")
        @JsonProperty("tile_y")
        private Integer tileY;
        
        @JsonProperty("content_type")
        private String contentType;
        
        @Min(0)
        @JsonProperty("byte_size")
        private Integer byteSize;
        
        public String getTileId() {
            return tileId;
        }
        
        public void setTileId(String tileId) {
            this.tileId = tileId;
        }
        
        public Integer getTileX() {
            return tileX;
        }
        
        public void setTileX(Integer tileX) {
            this.tileX = tileX;
        }
        
        public Integer getTileY() {
            return tileY;
        }
        
        public void setTileY(Integer tileY) {
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
