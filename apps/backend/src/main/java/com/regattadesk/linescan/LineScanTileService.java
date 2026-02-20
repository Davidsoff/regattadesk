package com.regattadesk.linescan;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for line-scan tile operations.
 * 
 * Coordinates tile storage between MinIO and database metadata.
 */
@ApplicationScoped
public class LineScanTileService {
    
    private static final Logger LOG = Logger.getLogger(LineScanTileService.class);
    
    private final LineScanTileRepository tileRepository;
    private final LineScanManifestRepository manifestRepository;
    private final MinioStorageAdapter storageAdapter;
    
    @Inject
    public LineScanTileService(
            LineScanTileRepository tileRepository,
            LineScanManifestRepository manifestRepository,
            MinioStorageAdapter storageAdapter) {
        this.tileRepository = tileRepository;
        this.manifestRepository = manifestRepository;
        this.storageAdapter = storageAdapter;
    }
    
    /**
     * Store a tile (metadata + binary data).
     * 
     * Note: This requires the manifest to have been created first with tile metadata.
     */
    @Transactional
    public void storeTile(UUID regattaId, String tileId, byte[] tileData, String contentType) 
            throws TileNotFoundException, MinioStorageAdapter.MinioStorageException {
        
        // Find the tile metadata
        LineScanTileMetadata existingMetadata = tileRepository.findByRegattaAndTileId(regattaId, tileId)
            .orElseThrow(() -> new TileNotFoundException(
                "Tile metadata not found. Manifest must be created before uploading tiles: " + tileId));
        
        // Get manifest to retrieve capture session ID
        LineScanManifest manifest = manifestRepository.findById(existingMetadata.getManifestId())
            .orElseThrow(() -> new TileNotFoundException("Manifest not found for tile: " + tileId));

        int nextAttempt = (existingMetadata.getUploadAttempts() != null ? existingMetadata.getUploadAttempts() : 0) + 1;
        Instant now = Instant.now();

        // Persist upload intent first, then attempt object storage.
        LineScanTileMetadata pending = buildState(existingMetadata, contentType, null,
            LineScanTileMetadata.UploadState.PENDING, nextAttempt, null, now);
        tileRepository.save(pending);

        try {
            storageAdapter.storeTile(
                regattaId,
                manifest.getCaptureSessionId(),
                tileId,
                tileData,
                contentType
            );
        } catch (MinioStorageAdapter.MinioStorageException e) {
            tileRepository.save(buildState(existingMetadata, contentType, null,
                LineScanTileMetadata.UploadState.FAILED, nextAttempt, safeErrorMessage(e), now));
            throw e;
        } catch (RuntimeException e) {
            tileRepository.save(buildState(existingMetadata, contentType, null,
                LineScanTileMetadata.UploadState.FAILED, nextAttempt, safeErrorMessage(e), now));
            throw new MinioStorageAdapter.MinioStorageException("Unexpected tile upload failure", e);
        }

        tileRepository.save(buildState(existingMetadata, contentType, tileData.length,
            LineScanTileMetadata.UploadState.READY, nextAttempt, null, now));
        
        LOG.infof("Stored tile: regatta=%s, tile=%s, size=%d", regattaId, tileId, tileData.length);
    }
    
    /**
     * Retrieve tile binary data.
     */
    public MinioStorageAdapter.TileData retrieveTile(UUID regattaId, String tileId) 
            throws TileNotFoundException, MinioStorageAdapter.MinioStorageException {
        
        // Find tile metadata
        LineScanTileMetadata metadata = tileRepository.findByRegattaAndTileId(regattaId, tileId)
            .orElseThrow(() -> new TileNotFoundException("Tile not found: " + tileId));
        
        // Get manifest to retrieve capture session ID
        LineScanManifest manifest = manifestRepository.findById(metadata.getManifestId())
            .orElseThrow(() -> new TileNotFoundException("Manifest not found for tile: " + tileId));

        if (metadata.getUploadState() != LineScanTileMetadata.UploadState.READY) {
            throw new TileNotFoundException("Tile data not yet available: " + tileId);
        }
        
        // Retrieve from MinIO
        return storageAdapter.retrieveTile(regattaId, manifest.getCaptureSessionId(), tileId);
    }

    private LineScanTileMetadata buildState(
            LineScanTileMetadata base,
            String contentType,
            Integer byteSize,
            LineScanTileMetadata.UploadState state,
            Integer attempts,
            String lastError,
            Instant attemptAt) {
        return LineScanTileMetadata.builder()
            .id(base.getId())
            .manifestId(base.getManifestId())
            .tileId(base.getTileId())
            .tileX(base.getTileX())
            .tileY(base.getTileY())
            .contentType(contentType)
            .byteSize(byteSize)
            .uploadState(state)
            .uploadAttempts(attempts)
            .lastUploadError(lastError)
            .lastUploadAttemptAt(attemptAt)
            .minioBucket(base.getMinioBucket())
            .minioObjectKey(base.getMinioObjectKey())
            .createdAt(base.getCreatedAt())
            .build();
    }

    private String safeErrorMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
    
    /**
     * Exception thrown when a tile is not found.
     */
    public static class TileNotFoundException extends Exception {
        public TileNotFoundException(String message) {
            super(message);
        }
    }
}
