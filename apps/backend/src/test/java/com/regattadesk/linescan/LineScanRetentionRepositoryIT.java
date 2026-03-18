package com.regattadesk.linescan;

import com.regattadesk.linescan.model.LineScanManifest;
import com.regattadesk.linescan.model.TimingMarker;
import com.regattadesk.linescan.repository.EntryRepository;
import com.regattadesk.linescan.repository.LineScanManifestRepository;
import com.regattadesk.linescan.repository.RegattaRepository;
import com.regattadesk.linescan.repository.TimingMarkerRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for line-scan retention repository methods.
 * 
 * Verifies database interactions for retention-related queries work without errors.
 * These tests don't require test data setup - they verify the queries execute correctly.
 */
@QuarkusTest
class LineScanRetentionRepositoryIT {
    
    @Inject
    LineScanManifestRepository manifestRepository;
    
    @Inject
    RegattaRepository regattaRepository;
    
    @Inject
    EntryRepository entryRepository;
    
    @Inject
    TimingMarkerRepository markerRepository;
    
    @Test
    void findByRetentionStateIn_executesWithoutError() {
        // Query for specific states - may return empty list if no data
        List<LineScanManifest.RetentionState> searchStates = List.of(
            LineScanManifest.RetentionState.FULL_RETAINED,
            LineScanManifest.RetentionState.PENDING_DELAY
        );
        
        List<LineScanManifest> results = manifestRepository.findByRetentionStateIn(searchStates);
        
        // Should execute without error
        assertNotNull(results, "Should return a list (possibly empty)");
    }
    
    @Test
    void findByRetentionStateIn_emptyList_returnsEmptyList() {
        List<LineScanManifest> results = manifestRepository.findByRetentionStateIn(List.of());
        
        assertTrue(results.isEmpty(), "Empty states list should return empty results");
    }
    
    @Test
    void findByRetentionStateIn_nullList_returnsEmptyList() {
        List<LineScanManifest> results = manifestRepository.findByRetentionStateIn(null);
        
        assertTrue(results.isEmpty(), "Null states list should return empty results");
    }
    
    @Test
    void regattaRepository_isArchived_executesWithoutError() {
        // Query with random ID - should return false but not throw
        boolean isArchived = regattaRepository.isArchived(UUID.randomUUID());
        
        // Should execute without error (result can be true or false)
        assertFalse(isArchived, "Random UUID should not be archived");
    }
    
    @Test
    void entryRepository_areAllEntriesApprovedForRegatta_executesWithoutError() {
        // Query with random ID - should return true (no entries = all approved)
        boolean allApproved = entryRepository.areAllEntriesApprovedForRegatta(UUID.randomUUID());
        
        // Random UUID should have no entries, so should return true
        assertTrue(allApproved, "Regatta with no entries should be considered all approved");
    }
    
    @Test
    void timingMarkerRepository_findApprovedByRegattaId_executesWithoutError() {
        // Query with random ID - should return empty list
        List<TimingMarker> markers = markerRepository.findApprovedByRegattaId(UUID.randomUUID());
        
        // Should execute without error and return empty list
        assertNotNull(markers, "Should return a list");
        assertTrue(markers.isEmpty(), "Random UUID should have no markers");
    }
}
