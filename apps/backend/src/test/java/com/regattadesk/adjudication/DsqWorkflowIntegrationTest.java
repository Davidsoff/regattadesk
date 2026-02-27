package com.regattadesk.adjudication;

import com.regattadesk.entry.EntryAggregate;
import com.regattadesk.regatta.RegattaAggregate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DSQ/exclusion workflows (BC07-002).
 * 
 * Tests complete workflows including:
 * - DSQ application with results revision increment
 * - Exclusion application with results revision increment
 * - DSQ revert with results revision increment
 * - Event sourcing replay of complete workflow
 */
class DsqWorkflowIntegrationTest {

    @Test
    void completeDsqWorkflow_shouldUpdateEntryAndIncrementResultsRevision() {
        // Arrange - Create regatta and entry
        UUID regattaId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        
        RegattaAggregate regatta = RegattaAggregate.create(
            regattaId,
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            BigDecimal.valueOf(50.00),
            "EUR",
            60,
            false
        );
        
        EntryAggregate entry = EntryAggregate.create(
            entryId,
            regattaId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        // Initial state
        assertEquals("entered", entry.getStatus());
        assertEquals(0, regatta.getResultsRevision());
        
        // Act - Apply DSQ
        entry.disqualify("Rule violation: false start");
        regatta.incrementResultsRevision("DSQ applied to entry " + entryId);
        
        // Assert - Entry status and regatta revision updated
        assertEquals("dsq", entry.getStatus());
        assertEquals(1, regatta.getResultsRevision());
    }
    
    @Test
    void completeExclusionWorkflow_shouldUpdateEntryAndIncrementResultsRevision() {
        // Arrange - Create regatta and entry
        UUID regattaId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        
        RegattaAggregate regatta = RegattaAggregate.create(
            regattaId,
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            BigDecimal.valueOf(50.00),
            "EUR",
            60,
            false
        );
        
        EntryAggregate entry = EntryAggregate.create(
            entryId,
            regattaId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        // Initial state
        assertEquals("entered", entry.getStatus());
        assertEquals(0, regatta.getResultsRevision());
        
        // Act - Apply exclusion
        entry.exclude("Equipment violation: underweight boat");
        regatta.incrementResultsRevision("Exclusion applied to entry " + entryId);
        
        // Assert - Entry status and regatta revision updated
        assertEquals("excluded", entry.getStatus());
        assertEquals(1, regatta.getResultsRevision());
    }
    
    @Test
    void completeDsqRevertWorkflow_shouldRestoreStateAndIncrementResultsRevision() {
        // Arrange - Create regatta and entry with DSQ
        UUID regattaId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        
        RegattaAggregate regatta = RegattaAggregate.create(
            regattaId,
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            BigDecimal.valueOf(50.00),
            "EUR",
            60,
            false
        );
        
        EntryAggregate entry = EntryAggregate.create(
            entryId,
            regattaId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        // Apply DSQ
        entry.disqualify("Rule violation: false start");
        regatta.incrementResultsRevision("DSQ applied to entry " + entryId);
        
        assertEquals("dsq", entry.getStatus());
        assertEquals(1, regatta.getResultsRevision());
        
        // Act - Revert DSQ
        entry.revertDsq("Decision overturned on appeal");
        regatta.incrementResultsRevision("DSQ reverted for entry " + entryId);
        
        // Assert - Status restored and revision incremented again
        assertEquals("entered", entry.getStatus());
        assertEquals(2, regatta.getResultsRevision());
    }
    
    @Test
    void multipleAdjudicationActions_shouldIncrementResultsRevisionSequentially() {
        // Arrange
        UUID regattaId = UUID.randomUUID();
        RegattaAggregate regatta = RegattaAggregate.create(
            regattaId,
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            BigDecimal.valueOf(50.00),
            "EUR",
            60,
            false
        );
        
        UUID entry1Id = UUID.randomUUID();
        UUID entry2Id = UUID.randomUUID();
        UUID entry3Id = UUID.randomUUID();
        
        EntryAggregate entry1 = EntryAggregate.create(
            entry1Id, regattaId, UUID.randomUUID(), UUID.randomUUID(), 
            UUID.randomUUID(), UUID.randomUUID()
        );
        
        EntryAggregate entry2 = EntryAggregate.create(
            entry2Id, regattaId, UUID.randomUUID(), UUID.randomUUID(), 
            UUID.randomUUID(), UUID.randomUUID()
        );
        
        EntryAggregate entry3 = EntryAggregate.create(
            entry3Id, regattaId, UUID.randomUUID(), UUID.randomUUID(), 
            UUID.randomUUID(), UUID.randomUUID()
        );
        
        assertEquals(0, regatta.getResultsRevision());
        
        // Act - Multiple adjudication actions
        entry1.disqualify("False start");
        regatta.incrementResultsRevision("DSQ entry1");
        assertEquals(1, regatta.getResultsRevision());
        
        entry2.exclude("Equipment violation");
        regatta.incrementResultsRevision("Excluded entry2");
        assertEquals(2, regatta.getResultsRevision());
        
        entry1.revertDsq("Appeal successful");
        regatta.incrementResultsRevision("Reverted DSQ entry1");
        assertEquals(3, regatta.getResultsRevision());
        
        entry3.disqualify("Dangerous steering");
        regatta.incrementResultsRevision("DSQ entry3");
        assertEquals(4, regatta.getResultsRevision());
        
        // Assert - Final states
        assertEquals("entered", entry1.getStatus());
        assertEquals("excluded", entry2.getStatus());
        assertEquals("dsq", entry3.getStatus());
        assertEquals(4, regatta.getResultsRevision());
    }
    
    @Test
    void eventSourcing_shouldReplayCompleteWorkflowCorrectly() {
        // Arrange - Build complete workflow
        UUID regattaId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        
        RegattaAggregate regatta = RegattaAggregate.create(
            regattaId,
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            BigDecimal.valueOf(50.00),
            "EUR",
            60,
            false
        );
        
        EntryAggregate entry = EntryAggregate.create(
            entryId,
            regattaId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        // Complete workflow: create -> DSQ -> revert
        entry.disqualify("Rule violation");
        regatta.incrementResultsRevision("DSQ applied");
        entry.revertDsq("Decision overturned");
        regatta.incrementResultsRevision("DSQ reverted");
        
        // Capture events
        var entryEvents = entry.getUncommittedChanges();
        var regattaEvents = regatta.getUncommittedEvents();
        
        // Act - Replay events to reconstruct state
        EntryAggregate reconstructedEntry = new EntryAggregate(entryId);
        reconstructedEntry.loadFromHistory(entryEvents);
        
        RegattaAggregate reconstructedRegatta = new RegattaAggregate(regattaId);
        reconstructedRegatta.loadFromHistory(regattaEvents);
        
        // Assert - Reconstructed state matches
        assertEquals(entry.getStatus(), reconstructedEntry.getStatus());
        assertEquals("entered", reconstructedEntry.getStatus());
        
        assertEquals(regatta.getResultsRevision(), reconstructedRegatta.getResultsRevision());
        assertEquals(2, reconstructedRegatta.getResultsRevision());
    }
    
    @Test
    void dsqRevert_shouldNotBeAllowedForExcludedEntry() {
        // Arrange
        EntryAggregate entry = EntryAggregate.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        entry.exclude("Equipment violation");
        
        // Act & Assert - Cannot revert DSQ on excluded entry
        assertThrows(IllegalStateException.class, () -> {
            entry.revertDsq("Cannot revert excluded entry");
        });
    }
    
    @Test
    void consecutiveDsq_shouldNotBeAllowedWithoutRevert() {
        // Arrange
        EntryAggregate entry = EntryAggregate.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        entry.disqualify("First violation");
        
        // Act & Assert - Cannot DSQ already DSQ'd entry
        assertThrows(IllegalStateException.class, () -> {
            entry.disqualify("Second violation");
        });
    }
    
    @Test
    void consecutiveExclusions_shouldNotBeAllowed() {
        // Arrange
        EntryAggregate entry = EntryAggregate.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        entry.exclude("First violation");
        
        // Act & Assert - Cannot exclude already excluded entry
        assertThrows(IllegalStateException.class, () -> {
            entry.exclude("Second violation");
        });
    }
}
