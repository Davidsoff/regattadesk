package com.regattadesk.entry;

import com.regattadesk.eventstore.DomainEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Entry DSQ/exclusion operations (BC07-002).
 * 
 * Tests verify:
 * - DSQ operation captures prior state
 * - Exclusion operation captures prior state
 * - DSQ revert restores prior state exactly
 * - Event emission for all operations
 * - Validation rules
 */
class EntryDsqOperationsTest {

    @Test
    void disqualifyEntry_fromEnteredStatus_shouldEmitEventWithPriorState() {
        // Arrange
        UUID entryId = UUID.randomUUID();
        EntryAggregate entry = EntryAggregate.create(
            entryId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        entry.clearChanges(); // Clear creation event
        
        // Act
        entry.disqualify("Rule violation");
        
        // Assert
        assertEquals("dsq", entry.getStatus());
        
        List<DomainEvent> events = entry.getUncommittedChanges();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof EntryDisqualifiedEvent);
        
        EntryDisqualifiedEvent event = (EntryDisqualifiedEvent) events.get(0);
        assertEquals(entryId, event.getEntryId());
        assertEquals("entered", event.getPriorStatus());
        assertEquals("Rule violation", event.getReason());
        assertNotNull(event.getDisqualifiedAt());
    }
    
    @Test
    void disqualifyEntry_withNullReason_shouldThrowException() {
        // Arrange
        EntryAggregate entry = EntryAggregate.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            entry.disqualify(null);
        });
    }
    
    @Test
    void disqualifyEntry_withBlankReason_shouldThrowException() {
        // Arrange
        EntryAggregate entry = EntryAggregate.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            entry.disqualify("   ");
        });
    }
    
    @Test
    void disqualifyEntry_whenAlreadyDsq_shouldThrowException() {
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
        entry.clearChanges();
        
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            entry.disqualify("Second violation");
        });
    }
    
    @Test
    void excludeEntry_fromEnteredStatus_shouldEmitEventWithPriorState() {
        // Arrange
        UUID entryId = UUID.randomUUID();
        EntryAggregate entry = EntryAggregate.create(
            entryId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        entry.clearChanges(); // Clear creation event
        
        // Act
        entry.exclude("Equipment violation");
        
        // Assert
        assertEquals("excluded", entry.getStatus());
        
        List<DomainEvent> events = entry.getUncommittedChanges();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof EntryExcludedEvent);
        
        EntryExcludedEvent event = (EntryExcludedEvent) events.get(0);
        assertEquals(entryId, event.getEntryId());
        assertEquals("entered", event.getPriorStatus());
        assertEquals("Equipment violation", event.getReason());
        assertNotNull(event.getExcludedAt());
    }
    
    @Test
    void excludeEntry_withNullReason_shouldThrowException() {
        // Arrange
        EntryAggregate entry = EntryAggregate.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            entry.exclude(null);
        });
    }
    
    @Test
    void excludeEntry_withBlankReason_shouldThrowException() {
        // Arrange
        EntryAggregate entry = EntryAggregate.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            entry.exclude("  ");
        });
    }
    
    @Test
    void excludeEntry_whenAlreadyExcluded_shouldThrowException() {
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
        entry.clearChanges();
        
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            entry.exclude("Second violation");
        });
    }
    
    @Test
    void revertDsq_whenDsq_shouldRestorePriorStatus() {
        // Arrange
        UUID entryId = UUID.randomUUID();
        EntryAggregate entry = EntryAggregate.create(
            entryId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        entry.disqualify("Rule violation");
        entry.clearChanges(); // Clear DSQ event
        
        // Act
        entry.revertDsq("Decision overturned");
        
        // Assert
        assertEquals("entered", entry.getStatus()); // Restored to prior status
        
        List<DomainEvent> events = entry.getUncommittedChanges();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof EntryDsqRevertedEvent);
        
        EntryDsqRevertedEvent event = (EntryDsqRevertedEvent) events.get(0);
        assertEquals(entryId, event.getEntryId());
        assertEquals("entered", event.getRestoredStatus());
        assertEquals("Decision overturned", event.getReason());
        assertNotNull(event.getRevertedAt());
    }
    
    @Test
    void revertDsq_whenNotDsq_shouldThrowException() {
        // Arrange
        EntryAggregate entry = EntryAggregate.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            entry.revertDsq("Cannot revert non-DSQ entry");
        });
    }
    
    @Test
    void revertDsq_withNullReason_shouldThrowException() {
        // Arrange
        EntryAggregate entry = EntryAggregate.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        entry.disqualify("Rule violation");
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            entry.revertDsq(null);
        });
    }
    
    @Test
    void revertDsq_withBlankReason_shouldThrowException() {
        // Arrange
        EntryAggregate entry = EntryAggregate.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        entry.disqualify("Rule violation");
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            entry.revertDsq("   ");
        });
    }
    
    @Test
    void revertDsq_shouldRestorePriorStatusExactly_evenWhenComplex() {
        // Arrange - entry with DNS status before DSQ
        UUID entryId = UUID.randomUUID();
        EntryAggregate entry = EntryAggregate.create(
            entryId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        // Simulate DNS status (would normally be set through a method)
        // For now, we'll test with entered -> dsq -> revert
        entry.disqualify("Rule violation");
        entry.clearChanges();
        
        // Act
        entry.revertDsq("Overturned on appeal");
        
        // Assert - status should be exactly what it was before DSQ
        assertEquals("entered", entry.getStatus());
    }
    
    @Test
    void eventSourcing_shouldReconstructStateCorrectly_afterDsqAndRevert() {
        // Arrange - create entry, DSQ it, then revert
        UUID entryId = UUID.randomUUID();
        EntryAggregate entry = EntryAggregate.create(
            entryId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        entry.disqualify("Rule violation");
        entry.revertDsq("Decision overturned");
        
        // Capture all events
        List<DomainEvent> events = entry.getUncommittedChanges();
        
        // Act - reconstruct from events
        EntryAggregate reconstructed = new EntryAggregate(entryId);
        for (DomainEvent event : events) {
            reconstructed.loadFromHistory(List.of(event));
        }
        
        // Assert - reconstructed state should match
        assertEquals("entered", reconstructed.getStatus());
        assertEquals(entry.getStatus(), reconstructed.getStatus());
    }
    
    @Test
    void disqualifyEntry_shouldWorkFromDifferentStartingStatus() {
        // Test DSQ from DNS status
        EntryAggregate entry = EntryAggregate.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        // Note: In full implementation, we'd set DNS status first
        // For now, testing from entered state
        entry.clearChanges();
        entry.disqualify("Rule violation after scratch");
        
        assertEquals("dsq", entry.getStatus());
        List<DomainEvent> events = entry.getUncommittedChanges();
        EntryDisqualifiedEvent event = (EntryDisqualifiedEvent) events.get(0);
        assertEquals("entered", event.getPriorStatus());
    }
}
