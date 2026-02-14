package com.regattadesk.linescan;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

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
    private final MinioConfiguration minioConfig;
    
    @Inject
    public LineScanTileService(
            LineScanTileRepository tileRepository,
            LineScanManifestRepository manifestRepository,
            MinioStorageAdapter storageAdapter,
            MinioConfiguration minioConfig) {
        this.tileRepository = tileRepository;
        this.manifestRepository = manifestRepository;
        this.storageAdapter = storageAdapter;
        this.minioConfig = minioConfig;
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
        
        // Store binary data in MinIO
        storageAdapter.storeTile(
            regattaId,
            manifest.getCaptureSessionId(),
            tileId,
            tileData,
            contentType
        );
        
        // Update metadata with size information
        LineScanTileMetadata updated = LineScanTileMetadata.builder()
            .id(existingMetadata.getId())
            .manifestId(existingMetadata.getManifestId())
            .tileId(existingMetadata.getTileId())
            .tileX(existingMetadata.getTileX())
            .tileY(existingMetadata.getTileY())
            .contentType(contentType)
            .byteSize(tileData.length)
            .minioBucket(existingMetadata.getMinioBucket())
            .minioObjectKey(existingMetadata.getMinioObjectKey())
            .createdAt(existingMetadata.getCreatedAt())
            .build();
        
        tileRepository.save(updated);
        
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
        
        // Retrieve from MinIO
        return storageAdapter.retrieveTile(regattaId, manifest.getCaptureSessionId(), tileId);
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
