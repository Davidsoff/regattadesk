package com.regattadesk.linescan;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for line-scan manifest operations.
 * 
 * Coordinates manifest persistence with event logging.
 */
@ApplicationScoped
public class LineScanManifestService {
    
    private static final Logger LOG = Logger.getLogger(LineScanManifestService.class);
    
    private final LineScanManifestRepository manifestRepository;
    private final LineScanTileRepository tileRepository;
    private final MinioStorageAdapter storageAdapter;
    private final MinioConfiguration minioConfig;
    
    @Inject
    public LineScanManifestService(
            LineScanManifestRepository manifestRepository,
            LineScanTileRepository tileRepository,
            MinioStorageAdapter storageAdapter,
            MinioConfiguration minioConfig) {
        this.manifestRepository = manifestRepository;
        this.tileRepository = tileRepository;
        this.storageAdapter = storageAdapter;
        this.minioConfig = minioConfig;
    }
    
    /**
     * Create or update a line-scan manifest.
     * 
     * This operation is upsert-based on capture_session_id.
     */
    @Transactional
    public LineScanManifest upsertManifest(LineScanManifest manifest) 
            throws MinioStorageAdapter.MinioStorageException {
        
        // Ensure MinIO bucket exists for this regatta
        storageAdapter.ensureBucket(manifest.getRegattaId());
        
        // Save manifest metadata
        LineScanManifest saved = manifestRepository.save(manifest);

        // Replace tile metadata entries to keep manifest replacement semantics deterministic.
        tileRepository.deleteByManifestId(saved.getId());

        // Save tile metadata entries
        String bucket = minioConfig.getBucketName(manifest.getRegattaId().toString());
        for (LineScanManifestTile tile : manifest.getTiles()) {
            String objectKey = minioConfig.getTileObjectKey(
                manifest.getCaptureSessionId().toString(),
                tile.getTileId()
            );
            
            LineScanTileMetadata metadata = LineScanTileMetadata.builder()
                .manifestId(saved.getId())
                .tileId(tile.getTileId())
                .tileX(tile.getTileX())
                .tileY(tile.getTileY())
                .contentType(tile.getContentType())
                .byteSize(tile.getByteSize())
                .minioBucket(bucket)
                .minioObjectKey(objectKey)
                .build();
            
            tileRepository.save(metadata);
        }
        
        LOG.infof("Upserted manifest: id=%s, regatta=%s, session=%s, tiles=%d",
            saved.getId(), saved.getRegattaId(), saved.getCaptureSessionId(), 
            manifest.getTiles().size());
        
        // Reload to include the latest persisted tile set in the response payload.
        return manifestRepository.findById(saved.getId()).orElse(saved);
    }
    
    /**
     * Retrieve a manifest by ID.
     */
    public Optional<LineScanManifest> getManifest(UUID manifestId) {
        return manifestRepository.findById(manifestId);
    }
    
    /**
     * Retrieve a manifest by capture session ID.
     */
    public Optional<LineScanManifest> getManifestByCaptureSession(UUID captureSessionId) {
        return manifestRepository.findByCaptureSessionId(captureSessionId);
    }
}
