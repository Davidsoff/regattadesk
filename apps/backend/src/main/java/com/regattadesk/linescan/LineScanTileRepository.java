package com.regattadesk.linescan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for line-scan tile metadata persistence.
 */
public interface LineScanTileRepository {
    
    /**
     * Save tile metadata.
     */
    LineScanTileMetadata save(LineScanTileMetadata metadata);
    
    /**
     * Find tile metadata by manifest ID and tile ID.
     */
    Optional<LineScanTileMetadata> findByManifestAndTileId(UUID manifestId, String tileId);
    
    /**
     * Find all tiles for a manifest.
     */
    List<LineScanTileMetadata> findByManifestId(UUID manifestId);
}
