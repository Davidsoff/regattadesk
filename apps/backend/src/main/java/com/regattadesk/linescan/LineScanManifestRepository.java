package com.regattadesk.linescan;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for line-scan manifest persistence.
 */
public interface LineScanManifestRepository {
    
    /**
     * Save a new manifest or update an existing one.
     */
    LineScanManifest save(LineScanManifest manifest);
    
    /**
     * Find a manifest by its ID.
     */
    Optional<LineScanManifest> findById(UUID manifestId);
    
    /**
     * Find a manifest by capture session ID.
     */
    Optional<LineScanManifest> findByCaptureSessionId(UUID captureSessionId);
}
