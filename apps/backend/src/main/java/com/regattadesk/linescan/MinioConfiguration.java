package com.regattadesk.linescan;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Objects;

/**
 * Configuration and bean producer for MinIO client.
 */
@ApplicationScoped
public class MinioConfiguration {
    
    @ConfigProperty(name = "minio.bucket-prefix")
    String bucketPrefix;
    
    public String getBucketPrefix() {
        return bucketPrefix;
    }
    
    /**
     * Generate bucket name for a regatta's line-scan data.
     * Format: {prefix}-regatta-{regattaId}
     */
    public String getBucketName(String regattaId) {
        String prefix = requireNonBlank(bucketPrefix, "bucketPrefix");
        String safeRegattaId = requireNonBlank(regattaId, "regattaId");
        return String.format("%s-regatta-%s", prefix, safeRegattaId);
    }
    
    /**
     * Generate object key for a tile.
     * Format: line-scan/{captureSessionId}/{tileId}
     */
    public String getTileObjectKey(String captureSessionId, String tileId) {
        String safeCaptureSessionId = requireNonBlank(captureSessionId, "captureSessionId");
        String safeTileId = requireNonBlank(tileId, "tileId");
        return String.format("line-scan/%s/%s", safeCaptureSessionId, safeTileId);
    }

    private String requireNonBlank(String value, String name) {
        String nonNull = Objects.requireNonNull(value, name + " cannot be null");
        if (nonNull.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return nonNull.trim();
    }
}
