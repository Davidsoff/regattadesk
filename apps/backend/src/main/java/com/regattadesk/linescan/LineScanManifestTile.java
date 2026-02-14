package com.regattadesk.linescan;

/**
 * Represents a single tile within a line-scan manifest.
 * 
 * Immutable domain model for tile grid coordinates and metadata.
 */
public class LineScanManifestTile {
    
    private final String tileId;
    private final int tileX;
    private final int tileY;
    private final String contentType;
    private final Integer byteSize;
    
    public LineScanManifestTile(String tileId, int tileX, int tileY, String contentType, Integer byteSize) {
        this.tileId = tileId;
        this.tileX = tileX;
        this.tileY = tileY;
        this.contentType = contentType;
        this.byteSize = byteSize;
    }
    
    public String getTileId() {
        return tileId;
    }
    
    public int getTileX() {
        return tileX;
    }
    
    public int getTileY() {
        return tileY;
    }
    
    public String getContentType() {
        return contentType;
    }
    
    public Integer getByteSize() {
        return byteSize;
    }
}
