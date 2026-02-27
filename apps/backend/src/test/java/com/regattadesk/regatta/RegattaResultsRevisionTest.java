package com.regattadesk.regatta;

import com.regattadesk.eventstore.DomainEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Regatta results revision operations (BC07-002).
 * 
 * Tests verify:
 * - Results revision increment with reason
 * - Event emission
 * - State updates after increment
 * - Event sourcing replay
 */
class RegattaResultsRevisionTest {

    @Test
    void incrementResultsRevision_shouldEmitEventAndIncrementCounter() {
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
        regatta.markEventsAsCommitted(); // Clear creation event
        
        assertEquals(0, regatta.getResultsRevision());
        
        // Act
        regatta.incrementResultsRevision("DSQ applied to entry 123");
        
        // Assert
        assertEquals(1, regatta.getResultsRevision());
        
        List<DomainEvent> events = regatta.getUncommittedEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof ResultsRevisionIncrementedEvent);
        
        ResultsRevisionIncrementedEvent event = (ResultsRevisionIncrementedEvent) events.get(0);
        assertEquals(regattaId, event.getRegattaId());
        assertEquals(1, event.getNewResultsRevision());
        assertEquals("DSQ applied to entry 123", event.getReason());
    }
    
    @Test
    void incrementResultsRevision_withNullReason_shouldThrowException() {
        // Arrange
        RegattaAggregate regatta = RegattaAggregate.create(
            UUID.randomUUID(),
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            BigDecimal.valueOf(50.00),
            "EUR",
            60,
            false
        );
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            regatta.incrementResultsRevision(null);
        });
    }
    
    @Test
    void incrementResultsRevision_withBlankReason_shouldThrowException() {
        // Arrange
        RegattaAggregate regatta = RegattaAggregate.create(
            UUID.randomUUID(),
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            BigDecimal.valueOf(50.00),
            "EUR",
            60,
            false
        );
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            regatta.incrementResultsRevision("   ");
        });
    }
    
    @Test
    void incrementResultsRevision_multipleTimes_shouldIncrementSequentially() {
        // Arrange
        RegattaAggregate regatta = RegattaAggregate.create(
            UUID.randomUUID(),
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            BigDecimal.valueOf(50.00),
            "EUR",
            60,
            false
        );
        
        // Act
        regatta.incrementResultsRevision("First change");
        assertEquals(1, regatta.getResultsRevision());
        
        regatta.incrementResultsRevision("Second change");
        assertEquals(2, regatta.getResultsRevision());
        
        regatta.incrementResultsRevision("Third change");
        assertEquals(3, regatta.getResultsRevision());
    }
    
    @Test
    void eventSourcing_shouldReconstructResultsRevisionCorrectly() {
        // Arrange - create regatta and increment revision twice
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
        regatta.incrementResultsRevision("First change");
        regatta.incrementResultsRevision("Second change");
        
        // Capture all events
        List<DomainEvent> events = regatta.getUncommittedEvents();
        
        // Act - reconstruct from events
        RegattaAggregate reconstructed = new RegattaAggregate(regattaId);
        for (DomainEvent event : events) {
            reconstructed.loadFromHistory(List.of(event));
        }
        
        // Assert - reconstructed state should match
        assertEquals(2, reconstructed.getResultsRevision());
        assertEquals(regatta.getResultsRevision(), reconstructed.getResultsRevision());
    }
    
    @Test
    void incrementResultsRevision_shouldNotAffectDrawRevision() {
        // Arrange
        RegattaAggregate regatta = RegattaAggregate.create(
            UUID.randomUUID(),
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            BigDecimal.valueOf(50.00),
            "EUR",
            60,
            false
        );
        regatta.publishDraw(12345L); // Increment draw revision
        regatta.markEventsAsCommitted();
        
        int initialDrawRevision = regatta.getDrawRevision();
        
        // Act
        regatta.incrementResultsRevision("Results change");
        
        // Assert - draw revision should be unchanged
        assertEquals(initialDrawRevision, regatta.getDrawRevision());
        assertEquals(1, regatta.getResultsRevision());
    }
    
    @Test
    void resultsRevision_shouldStartAtZero() {
        // Arrange & Act
        RegattaAggregate regatta = RegattaAggregate.create(
            UUID.randomUUID(),
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            BigDecimal.valueOf(50.00),
            "EUR",
            60,
            false
        );
        
        // Assert
        assertEquals(0, regatta.getResultsRevision());
    }
}
