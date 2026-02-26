package com.regattadesk.investigation;

import com.regattadesk.eventstore.DomainEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InvestigationAggregate (BC07-001).
 * 
 * Tests verify:
 * - Investigation lifecycle (open, update, close, reopen)
 * - Penalty application and configuration validation
 * - Event emission and state updates
 * - State reconstruction from events
 * - Validation rules and invariants
 */
class InvestigationAggregateTest {

    @Test
    void openInvestigation_shouldEmitEvent() {
        // Arrange
        UUID investigationId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        String description = "Crew started before whistle";
        
        // Act
        InvestigationAggregate investigation = InvestigationAggregate.open(
            investigationId,
            regattaId,
            entryId,
            description
        );
        
        // Assert
        assertEquals(investigationId, investigation.getId());
        assertEquals(regattaId, investigation.getRegattaId());
        assertEquals(entryId, investigation.getEntryId());
        assertEquals(description, investigation.getDescription());
        assertNull(investigation.getOutcome());
        assertNull(investigation.getPenaltySeconds());
        assertNull(investigation.getClosedAt());
        assertTrue(investigation.isOpen());
        
        // Verify event
        List<DomainEvent> events = investigation.getUncommittedEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof InvestigationOpenedEvent);
        
        InvestigationOpenedEvent event = (InvestigationOpenedEvent) events.get(0);
        assertEquals(investigationId, event.getInvestigationId());
        assertEquals(regattaId, event.getRegattaId());
        assertEquals(entryId, event.getEntryId());
        assertEquals(description, event.getDescription());
    }

    @Test
    void openInvestigation_withNullRegattaId_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> {
            InvestigationAggregate.open(
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                "Description"
            );
        });
    }

    @Test
    void openInvestigation_withNullEntryId_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> {
            InvestigationAggregate.open(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "Description"
            );
        });
    }

    @Test
    void openInvestigation_withNullDescription_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> {
            InvestigationAggregate.open(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null
            );
        });
    }

    @Test
    void openInvestigation_withBlankDescription_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> {
            InvestigationAggregate.open(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "   "
            );
        });
    }

    @Test
    void updateDescription_shouldEmitEvent() {
        // Arrange
        InvestigationAggregate investigation = InvestigationAggregate.open(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Initial description"
        );
        investigation.markEventsAsCommitted();
        
        // Act
        String newDescription = "Updated description with more details";
        investigation.updateDescription(newDescription);
        
        // Assert
        assertEquals(newDescription, investigation.getDescription());
        
        List<DomainEvent> events = investigation.getUncommittedEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof InvestigationUpdatedEvent);
        
        InvestigationUpdatedEvent event = (InvestigationUpdatedEvent) events.get(0);
        assertEquals(investigation.getId(), event.getInvestigationId());
        assertEquals(newDescription, event.getDescription());
    }

    @Test
    void updateDescription_whenClosed_shouldThrowException() {
        // Arrange
        InvestigationAggregate investigation = InvestigationAggregate.open(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Description"
        );
        investigation.close(InvestigationOutcome.NO_ACTION, null);
        
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            investigation.updateDescription("New description");
        });
    }

    @Test
    void closeWithNoAction_shouldEmitEventAndMarkClosed() {
        // Arrange
        InvestigationAggregate investigation = InvestigationAggregate.open(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Description"
        );
        investigation.markEventsAsCommitted();
        
        // Act
        investigation.close(InvestigationOutcome.NO_ACTION, null);
        
        // Assert
        assertEquals(InvestigationOutcome.NO_ACTION, investigation.getOutcome());
        assertNull(investigation.getPenaltySeconds());
        assertNotNull(investigation.getClosedAt());
        assertFalse(investigation.isOpen());
        
        List<DomainEvent> events = investigation.getUncommittedEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof InvestigationClosedEvent);
        
        InvestigationClosedEvent event = (InvestigationClosedEvent) events.get(0);
        assertEquals(investigation.getId(), event.getInvestigationId());
        assertEquals(InvestigationOutcome.NO_ACTION, event.getOutcome());
        assertNull(event.getPenaltySeconds());
        assertNotNull(event.getClosedAt());
    }

    @Test
    void closeWithPenalty_shouldEmitEventWithPenaltySeconds() {
        // Arrange
        InvestigationAggregate investigation = InvestigationAggregate.open(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Description"
        );
        investigation.markEventsAsCommitted();
        
        // Act
        Integer penaltySeconds = 30;
        investigation.close(InvestigationOutcome.PENALTY, penaltySeconds);
        
        // Assert
        assertEquals(InvestigationOutcome.PENALTY, investigation.getOutcome());
        assertEquals(penaltySeconds, investigation.getPenaltySeconds());
        assertNotNull(investigation.getClosedAt());
        assertFalse(investigation.isOpen());
        
        List<DomainEvent> events = investigation.getUncommittedEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof InvestigationClosedEvent);
        
        InvestigationClosedEvent event = (InvestigationClosedEvent) events.get(0);
        assertEquals(InvestigationOutcome.PENALTY, event.getOutcome());
        assertEquals(penaltySeconds, event.getPenaltySeconds());
    }

    @Test
    void closeWithPenalty_withoutPenaltySeconds_shouldThrowException() {
        // Arrange
        InvestigationAggregate investigation = InvestigationAggregate.open(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Description"
        );
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            investigation.close(InvestigationOutcome.PENALTY, null);
        });
    }

    @Test
    void closeWithPenalty_withNegativePenaltySeconds_shouldThrowException() {
        // Arrange
        InvestigationAggregate investigation = InvestigationAggregate.open(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Description"
        );
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            investigation.close(InvestigationOutcome.PENALTY, -10);
        });
    }

    @Test
    void closeWithPenalty_withZeroPenaltySeconds_shouldThrowException() {
        // Arrange
        InvestigationAggregate investigation = InvestigationAggregate.open(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Description"
        );
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            investigation.close(InvestigationOutcome.PENALTY, 0);
        });
    }

    @Test
    void closeWithExcluded_shouldEmitEvent() {
        // Arrange
        InvestigationAggregate investigation = InvestigationAggregate.open(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Description"
        );
        investigation.markEventsAsCommitted();
        
        // Act
        investigation.close(InvestigationOutcome.EXCLUDED, null);
        
        // Assert
        assertEquals(InvestigationOutcome.EXCLUDED, investigation.getOutcome());
        assertNull(investigation.getPenaltySeconds());
        assertFalse(investigation.isOpen());
    }

    @Test
    void closeWithDSQ_shouldEmitEvent() {
        // Arrange
        InvestigationAggregate investigation = InvestigationAggregate.open(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Description"
        );
        investigation.markEventsAsCommitted();
        
        // Act
        investigation.close(InvestigationOutcome.DSQ, null);
        
        // Assert
        assertEquals(InvestigationOutcome.DSQ, investigation.getOutcome());
        assertNull(investigation.getPenaltySeconds());
        assertFalse(investigation.isOpen());
    }

    @Test
    void close_whenAlreadyClosed_shouldThrowException() {
        // Arrange
        InvestigationAggregate investigation = InvestigationAggregate.open(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Description"
        );
        investigation.close(InvestigationOutcome.NO_ACTION, null);
        
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            investigation.close(InvestigationOutcome.PENALTY, 30);
        });
    }

    @Test
    void reopen_whenClosed_shouldEmitEventAndMarkOpen() {
        // Arrange
        InvestigationAggregate investigation = InvestigationAggregate.open(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Description"
        );
        investigation.close(InvestigationOutcome.NO_ACTION, null);
        investigation.markEventsAsCommitted();
        
        // Act
        investigation.reopen();
        
        // Assert
        assertTrue(investigation.isOpen());
        assertNull(investigation.getOutcome());
        assertNull(investigation.getPenaltySeconds());
        assertNull(investigation.getClosedAt());
        
        List<DomainEvent> events = investigation.getUncommittedEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof InvestigationReopenedEvent);
        
        InvestigationReopenedEvent event = (InvestigationReopenedEvent) events.get(0);
        assertEquals(investigation.getId(), event.getInvestigationId());
    }

    @Test
    void reopen_whenAlreadyOpen_shouldThrowException() {
        // Arrange
        InvestigationAggregate investigation = InvestigationAggregate.open(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Description"
        );
        
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            investigation.reopen();
        });
    }

    @Test
    void loadFromHistory_shouldReconstructState() {
        // Arrange
        UUID investigationId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        String description = "Original description";
        String updatedDescription = "Updated description";
        Integer penaltySeconds = 30;
        Instant closedAt = Instant.parse("2026-02-26T10:00:00Z");
        
        InvestigationOpenedEvent openedEvent = new InvestigationOpenedEvent(
            investigationId, regattaId, entryId, description
        );
        InvestigationUpdatedEvent updatedEvent = new InvestigationUpdatedEvent(
            investigationId, updatedDescription
        );
        InvestigationClosedEvent closedEvent = new InvestigationClosedEvent(
            investigationId, InvestigationOutcome.PENALTY, penaltySeconds, closedAt
        );
        
        // Act
        InvestigationAggregate investigation = new InvestigationAggregate(investigationId);
        investigation.loadFromHistory(List.of(openedEvent, updatedEvent, closedEvent));
        
        // Assert
        assertEquals(investigationId, investigation.getId());
        assertEquals(regattaId, investigation.getRegattaId());
        assertEquals(entryId, investigation.getEntryId());
        assertEquals(updatedDescription, investigation.getDescription());
        assertEquals(InvestigationOutcome.PENALTY, investigation.getOutcome());
        assertEquals(penaltySeconds, investigation.getPenaltySeconds());
        assertEquals(closedAt, investigation.getClosedAt());
        assertFalse(investigation.isOpen());
        
        // No uncommitted events after loading from history
        assertTrue(investigation.getUncommittedEvents().isEmpty());
    }

    @Test
    void loadFromHistory_withReopenEvent_shouldMarkAsOpen() {
        // Arrange
        UUID investigationId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        
        InvestigationOpenedEvent openedEvent = new InvestigationOpenedEvent(
            investigationId, regattaId, entryId, "Description"
        );
        InvestigationClosedEvent closedEvent = new InvestigationClosedEvent(
            investigationId, InvestigationOutcome.NO_ACTION, null, Instant.now()
        );
        InvestigationReopenedEvent reopenedEvent = new InvestigationReopenedEvent(
            investigationId
        );
        
        // Act
        InvestigationAggregate investigation = new InvestigationAggregate(investigationId);
        investigation.loadFromHistory(List.of(openedEvent, closedEvent, reopenedEvent));
        
        // Assert
        assertTrue(investigation.isOpen());
        assertNull(investigation.getOutcome());
        assertNull(investigation.getPenaltySeconds());
        assertNull(investigation.getClosedAt());
    }

    @Test
    void getAggregateType_shouldReturnInvestigation() {
        // Arrange
        InvestigationAggregate investigation = new InvestigationAggregate(UUID.randomUUID());
        
        // Act & Assert
        assertEquals("Investigation", investigation.getAggregateType());
    }
}
