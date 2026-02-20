package com.regattadesk.linescan;

/**
 * Represents a single tile within a line-scan manifest.
 */
public record LineScanManifestTile(
    String tileId,
    int tileX,
    int tileY,
    String contentType,
    Integer byteSize
) {
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
