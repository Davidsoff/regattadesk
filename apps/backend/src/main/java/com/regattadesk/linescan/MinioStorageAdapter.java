package com.regattadesk.linescan;

import io.minio.*;
import io.minio.errors.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Storage adapter for line-scan manifests and tiles using MinIO.
 * 
 * Handles object storage operations including bucket creation, tile upload/retrieval.
 */
@ApplicationScoped
public class MinioStorageAdapter {
    
    private static final Logger LOG = Logger.getLogger(MinioStorageAdapter.class);
    
    private final MinioClient minioClient;
    private final MinioConfiguration config;
    
    @Inject
    public MinioStorageAdapter(MinioClient minioClient, MinioConfiguration config) {
        this.minioClient = minioClient;
        this.config = config;
    }
    
    /**
     * Ensure bucket exists for the given regatta, creating it if necessary.
     */
    public void ensureBucket(UUID regattaId) throws MinioStorageException {
        String bucketName = config.getBucketName(regattaId.toString());
        try {
            boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build()
            );
            
            if (!exists) {
                minioClient.makeBucket(
                    MakeBucketArgs.builder()
                        .bucket(bucketName)
                        .build()
                );
                LOG.infof("Created MinIO bucket: %s", bucketName);
            }
        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                 InvalidKeyException | InvalidResponseException | IOException |
                 NoSuchAlgorithmException | ServerException | XmlParserException e) {
            throw new MinioStorageException("Failed to ensure bucket exists: " + bucketName, e);
        }
    }
    
    /**
     * Store a tile in MinIO.
     */
    public void storeTile(UUID regattaId, UUID captureSessionId, String tileId, 
                         byte[] tileData, String contentType) throws MinioStorageException {
        String bucketName = config.getBucketName(regattaId.toString());
        String objectKey = config.getTileObjectKey(captureSessionId.toString(), tileId);
        
        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(tileData), tileData.length, -1)
                    .contentType(contentType)
                    .build()
            );
            LOG.debugf("Stored tile: bucket=%s, key=%s, size=%d", bucketName, objectKey, tileData.length);
        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                 InvalidKeyException | InvalidResponseException | IOException |
                 NoSuchAlgorithmException | ServerException | XmlParserException e) {
            throw new MinioStorageException("Failed to store tile: " + objectKey, e);
        }
    }
    
    /**
     * Retrieve a tile from MinIO.
     */
    public TileData retrieveTile(UUID regattaId, UUID captureSessionId, String tileId) 
            throws MinioStorageException {
        String bucketName = config.getBucketName(regattaId.toString());
        String objectKey = config.getTileObjectKey(captureSessionId.toString(), tileId);
        
        try {
            GetObjectResponse response = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build()
            );
            
            byte[] data = response.readAllBytes();
            String contentType = response.headers().get("Content-Type");
            
            LOG.debugf("Retrieved tile: bucket=%s, key=%s, size=%d", bucketName, objectKey, data.length);
            return new TileData(data, contentType != null ? contentType : "application/octet-stream");
            
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                throw new TileNotFoundException("Tile not found: " + objectKey);
            }
            throw new MinioStorageException("Failed to retrieve tile: " + objectKey, e);
        } catch (InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | IOException | NoSuchAlgorithmException |
                 ServerException | XmlParserException e) {
            throw new MinioStorageException("Failed to retrieve tile: " + objectKey, e);
        }
    }
    
    /**
     * Check if a tile exists in MinIO.
     */
    public boolean tileExists(UUID regattaId, UUID captureSessionId, String tileId) {
        String bucketName = config.getBucketName(regattaId.toString());
        String objectKey = config.getTileObjectKey(captureSessionId.toString(), tileId);
        
        try {
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Container for tile binary data and content type.
     */
    public static class TileData {
        private final byte[] data;
        private final String contentType;
        
        public TileData(byte[] data, String contentType) {
            this.data = data;
            this.contentType = contentType;
        }
        
        public byte[] getData() {
            return data;
        }
        
        public String getContentType() {
            return contentType;
        }
    }
    
    /**
     * Exception thrown when MinIO storage operations fail.
     */
    public static class MinioStorageException extends Exception {
        public MinioStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Exception thrown when a tile is not found in storage.
     */
    public static class TileNotFoundException extends MinioStorageException {
        public TileNotFoundException(String message) {
            super(message, null);
        }
    }
}
