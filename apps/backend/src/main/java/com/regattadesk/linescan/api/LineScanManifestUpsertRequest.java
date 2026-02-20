package com.regattadesk.linescan.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

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
    @Pattern(regexp = "webp_lossless|png", message = "primary_format must be webp_lossless or png")
    @JsonProperty("primary_format")
    private String primaryFormat;
    
    @Pattern(regexp = "png", message = "fallback_format must be png")
    @JsonProperty("fallback_format")
    private String fallbackFormat;
    
    @NotNull(message = "x_origin_timestamp_ms is required")
    @JsonProperty("x_origin_timestamp_ms")
    private Long xOriginTimestampMs;
    
    @NotNull(message = "ms_per_pixel is required")
    @JsonProperty("ms_per_pixel")
    private Double msPerPixel;
    
    @NotNull(message = "tiles array is required")
    private List<@Valid TileDto> tiles;

    @AssertTrue(message = "tile_size_px must be 512 or 1024")
    public boolean isTileSizePxAllowed() {
        return tileSizePx == null || tileSizePx == 512 || tileSizePx == 1024;
    }
    
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
