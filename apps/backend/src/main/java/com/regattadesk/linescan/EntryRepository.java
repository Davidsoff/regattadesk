package com.regattadesk.linescan;

import java.util.UUID;

/**
 * Repository interface for entry completion state queries.
 * Simplified interface for BC06-006 retention scheduler needs.
 */
public interface EntryRepository {
    
    /**
     * Check if all entries for a regatta are approved (completion_status = 'completed').
     * 
     * @param regattaId The regatta ID
     * @return true if all entries are in completed state
     */
    boolean areAllEntriesApprovedForRegatta(UUID regattaId);
}
