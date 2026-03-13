package com.regattadesk.linescan.repository;

import com.regattadesk.linescan.model.TimingMarker;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for timing marker queries.
 * Used by retention scheduler to find approved markers for pruning window calculation.
 */
public interface TimingMarkerRepository {
    
    /**
     * Find all approved and linked markers for a regatta.
     * These markers define the time windows that must be preserved during pruning.
     * 
     * @param regattaId The regatta ID
     * @return List of approved markers
     */
    List<TimingMarker> findApprovedByRegattaId(UUID regattaId);
}
