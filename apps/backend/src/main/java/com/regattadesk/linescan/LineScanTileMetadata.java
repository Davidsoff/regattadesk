package com.regattadesk.linescan;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for a stored line-scan tile in MinIO.
 * 
 * Tracks storage location and metadata for tile binary data.
 */
public class LineScanTileMetadata {
    
    private final UUID id;
    private final UUID manifestId;
    private final String tileId;
    private final int tileX;
    private final int tileY;
    private final String contentType;
    private final Integer byteSize;
    private final UploadState uploadState;
    private final Integer uploadAttempts;
    private final String lastUploadError;
    private final Instant lastUploadAttemptAt;
    private final String minioBucket;
    private final String minioObjectKey;
    private final Instant createdAt;
    private final Instant updatedAt;
    
    private LineScanTileMetadata(Builder builder) {
        this.id = builder.id;
        this.manifestId = builder.manifestId;
        this.tileId = builder.tileId;
        this.tileX = builder.tileX;
        this.tileY = builder.tileY;
        this.contentType = builder.contentType;
        this.byteSize = builder.byteSize;
        this.uploadState = builder.uploadState;
        this.uploadAttempts = builder.uploadAttempts;
        this.lastUploadError = builder.lastUploadError;
        this.lastUploadAttemptAt = builder.lastUploadAttemptAt;
        this.minioBucket = builder.minioBucket;
        this.minioObjectKey = builder.minioObjectKey;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }
    
    public UUID getId() {
        return id;
    }
    
    public UUID getManifestId() {
        return manifestId;
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

    public UploadState getUploadState() {
        return uploadState;
    }

    public Integer getUploadAttempts() {
        return uploadAttempts;
    }

    public String getLastUploadError() {
        return lastUploadError;
    }

    public Instant getLastUploadAttemptAt() {
        return lastUploadAttemptAt;
    }
    
    public String getMinioBucket() {
        return minioBucket;
    }
    
    public String getMinioObjectKey() {
        return minioObjectKey;
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
        private UUID manifestId;
        private String tileId;
        private int tileX;
        private int tileY;
        private String contentType;
        private Integer byteSize;
        private UploadState uploadState;
        private Integer uploadAttempts;
        private String lastUploadError;
        private Instant lastUploadAttemptAt;
        private String minioBucket;
        private String minioObjectKey;
        private Instant createdAt;
        private Instant updatedAt;
        
        public Builder id(UUID id) {
            this.id = id;
            return this;
        }
        
        public Builder manifestId(UUID manifestId) {
            this.manifestId = manifestId;
            return this;
        }
        
        public Builder tileId(String tileId) {
            this.tileId = tileId;
            return this;
        }
        
        public Builder tileX(int tileX) {
            this.tileX = tileX;
            return this;
        }
        
        public Builder tileY(int tileY) {
            this.tileY = tileY;
            return this;
        }
        
        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }
        
        public Builder byteSize(Integer byteSize) {
            this.byteSize = byteSize;
            return this;
        }

        public Builder uploadState(UploadState uploadState) {
            this.uploadState = uploadState;
            return this;
        }

        public Builder uploadAttempts(Integer uploadAttempts) {
            this.uploadAttempts = uploadAttempts;
            return this;
        }

        public Builder lastUploadError(String lastUploadError) {
            this.lastUploadError = lastUploadError;
            return this;
        }

        public Builder lastUploadAttemptAt(Instant lastUploadAttemptAt) {
            this.lastUploadAttemptAt = lastUploadAttemptAt;
            return this;
        }
        
        public Builder minioBucket(String minioBucket) {
            this.minioBucket = minioBucket;
            return this;
        }
        
        public Builder minioObjectKey(String minioObjectKey) {
            this.minioObjectKey = minioObjectKey;
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
        
        public LineScanTileMetadata build() {
            return new LineScanTileMetadata(this);
        }
    }

    public enum UploadState {
        PENDING("pending"),
        READY("ready"),
        FAILED("failed");

        private final String value;

        UploadState(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static UploadState fromValue(String value) {
            if (value == null) {
                return PENDING;
            }
            for (UploadState state : values()) {
                if (state.value.equalsIgnoreCase(value)) {
                    return state;
                }
            }
            throw new IllegalArgumentException("Unknown upload state: " + value);
        }
    }
}
