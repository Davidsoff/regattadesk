package com.regattadesk.linescan.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for regatta state queries.
 * Simplified interface for BC06-006 retention scheduler needs.
 */
public interface RegattaRepository {
    
    /**
     * Check if a regatta is archived.
     * 
     * @param regattaId The regatta ID
     * @return true if regatta is archived
     */
    boolean isArchived(UUID regattaId);

    /**
     * Find configured regatta end timestamp.
     *
     * @param regattaId The regatta ID
     * @return regatta end time when configured
     */
    Optional<Instant> findRegattaEndAt(UUID regattaId);
}
