package com.regattadesk.linescan;

import io.minio.MinioClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Configuration and bean producer for MinIO client.
 */
@ApplicationScoped
public class MinioConfiguration {
    
    @ConfigProperty(name = "minio.endpoint")
    String endpoint;
    
    @ConfigProperty(name = "minio.access-key")
    String accessKey;
    
    @ConfigProperty(name = "minio.secret-key")
    String secretKey;
    
    @ConfigProperty(name = "minio.bucket-prefix")
    String bucketPrefix;
    
    @Produces
    @ApplicationScoped
    public MinioClient minioClient() {
        return MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();
    }
    
    public String getBucketPrefix() {
        return bucketPrefix;
    }
    
    /**
     * Generate bucket name for a regatta's line-scan data.
     * Format: {prefix}-regatta-{regattaId}
     */
    public String getBucketName(String regattaId) {
        return String.format("%s-regatta-%s", bucketPrefix, regattaId);
    }
    
    /**
     * Generate object key for a tile.
     * Format: line-scan/{captureSessionId}/{tileId}
     */
    public String getTileObjectKey(String captureSessionId, String tileId) {
        return String.format("line-scan/%s/%s", captureSessionId, tileId);
    }
}
