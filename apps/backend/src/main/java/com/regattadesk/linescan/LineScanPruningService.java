package com.regattadesk.linescan;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Executes line-scan data pruning with marker window preservation.
 * 
 * Implements BC06-006 pruning logic:
 * - Deletes tile data outside marker preservation windows
 * - Preserves tiles within ±2s (configurable) of approved markers
 * - Updates manifest state to PRUNED
 * - Maintains transactional consistency
 */
@ApplicationScoped
public class LineScanPruningService {
    
    private static final Logger LOG = Logger.getLogger(LineScanPruningService.class);
    
    private final LineScanManifestRepository manifestRepository;
    private final LineScanTileRepository tileRepository;
    private final MinioStorageAdapter storageAdapter;
    private final MinioConfiguration minioConfiguration;
    
    public LineScanPruningService(
        LineScanManifestRepository manifestRepository,
        LineScanTileRepository tileRepository,
        MinioStorageAdapter storageAdapter,
        MinioConfiguration minioConfiguration
    ) {
        this.manifestRepository = manifestRepository;
        this.tileRepository = tileRepository;
        this.storageAdapter = storageAdapter;
        this.minioConfiguration = minioConfiguration;
    }
    
    /**
     * Prunes a manifest by deleting tiles outside marker preservation windows.
     * 
     * @param manifest The manifest to prune
     * @param markerWindows Time windows to preserve around approved markers
     * @throws IllegalStateException if manifest is not in ELIGIBLE state
     */
    @Transactional
    public void prune(LineScanManifest manifest, List<LineScanRetentionEvaluator.TimeWindow> markerWindows) {
        // Validate manifest is eligible for pruning
        if (manifest.getRetentionState() == LineScanManifest.RetentionState.PRUNED) {
            throw new IllegalStateException(
                "Manifest " + manifest.getId() + " is already pruned"
            );
        }
        
        if (manifest.getRetentionState() != LineScanManifest.RetentionState.ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS) {
            throw new IllegalStateException(
                "Manifest " + manifest.getId() + " is not eligible for pruning, current state: " 
                + manifest.getRetentionState()
            );
        }
        
        LOG.infof("Starting pruning for manifest %s with %d marker windows",
            manifest.getId(), markerWindows.size());
        
        // Get all tiles for this manifest
        List<LineScanTileMetadata> allTiles = tileRepository.findByManifestId(manifest.getId());
        
        if (allTiles.isEmpty()) {
            LOG.infof("No tiles found for manifest %s, marking as pruned", manifest.getId());
            updateManifestToPruned(manifest);
            return;
        }
        
        // Determine which tiles to delete (those outside marker windows)
        List<UUID> tilesToDelete = new ArrayList<>();
        List<LineScanTileMetadata> tilesToKeep = new ArrayList<>();
        
        for (LineScanTileMetadata tile : allTiles) {
            boolean inAnyWindow = markerWindows.stream()
                .anyMatch(window -> isTileInWindow(
                    tile,
                    window,
                    manifest.getXOriginTimestampMs(),
                    manifest.getMsPerPixel(),
                    manifest.getTileSizePx()
                ));
            
            if (inAnyWindow) {
                tilesToKeep.add(tile);
            } else {
                tilesToDelete.add(tile.getId());
            }
        }
        
        LOG.infof("Manifest %s: keeping %d tiles, deleting %d tiles",
            manifest.getId(), tilesToKeep.size(), tilesToDelete.size());
        
        // Delete tiles from MinIO storage
        for (LineScanTileMetadata tile : allTiles) {
            if (tilesToDelete.contains(tile.getId())) {
                try {
                    storageAdapter.deleteTile(
                        manifest.getRegattaId(),
                        manifest.getCaptureSessionId(),
                        tile.getTileId()
                    );
                    LOG.debugf("Deleted tile %s from MinIO",
                        tile.getTileId());
                } catch (Exception e) {
                    LOG.warnf(e, "Failed to delete tile %s from MinIO, continuing with metadata deletion",
                        tile.getTileId());
                }
            }
        }
        
        // Delete tile metadata from database
        if (!tilesToDelete.isEmpty()) {
            tileRepository.deleteByIds(tilesToDelete);
            LOG.infof("Deleted %d tile metadata records for manifest %s",
                tilesToDelete.size(), manifest.getId());
        }
        
        // Update manifest to PRUNED state
        updateManifestToPruned(manifest);
        
        LOG.infof("Successfully pruned manifest %s", manifest.getId());
    }
    
    /**
     * Determines if a tile overlaps with a time window.
     * 
     * @param tile The tile to check
     * @param window The time window
     * @param xOriginTimestampMs The x-axis origin timestamp in milliseconds
     * @param msPerPixel Milliseconds per pixel conversion factor
     * @param tileSizePx Tile size in pixels
     * @return true if tile overlaps the window
     */
    public boolean isTileInWindow(
        LineScanTileMetadata tile,
        LineScanRetentionEvaluator.TimeWindow window,
        long xOriginTimestampMs,
        double msPerPixel,
        int tileSizePx
    ) {
        // Calculate tile's time range
        long tileStartMs = xOriginTimestampMs + (long)(tile.getTileX() * msPerPixel);
        long tileEndMs = xOriginTimestampMs + (long)((tile.getTileX() + tileSizePx) * msPerPixel);
        
        // Check for overlap: tile overlaps window if:
        // - tile starts before window ends AND
        // - tile ends after window starts
        boolean overlaps = tileStartMs < window.endMs() && tileEndMs > window.startMs();
        
        return overlaps;
    }
    
    private void updateManifestToPruned(LineScanManifest manifest) {
        LineScanManifest updated = LineScanManifest.builder()
            .id(manifest.getId())
            .regattaId(manifest.getRegattaId())
            .captureSessionId(manifest.getCaptureSessionId())
            .tileSizePx(manifest.getTileSizePx())
            .primaryFormat(manifest.getPrimaryFormat())
            .fallbackFormat(manifest.getFallbackFormat())
            .xOriginTimestampMs(manifest.getXOriginTimestampMs())
            .msPerPixel(manifest.getMsPerPixel())
            .tiles(manifest.getTiles())
            .retentionDays(manifest.getRetentionDays())
            .pruneWindowSeconds(manifest.getPruneWindowSeconds())
            .retentionState(LineScanManifest.RetentionState.PRUNED)
            .pruneEligibleAt(manifest.getPruneEligibleAt())
            .prunedAt(Instant.now())
            .createdAt(manifest.getCreatedAt())
            .updatedAt(Instant.now())
            .build();
        
        manifestRepository.save(updated);
    }
}
