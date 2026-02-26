package com.regattadesk.investigation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for investigation workflow (BC07-001).
 * 
 * Tests verify:
 * - Complete investigation lifecycle workflows
 * - Multiple investigations per entry
 * - Penalty application rules
 * - State transitions and validation
 */
class InvestigationWorkflowTest {

    @Test
    void completeInvestigationWorkflow_noAction() {
        // Arrange
        UUID investigationId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        String description = "False start investigation";
        
        // Act - Open investigation
        InvestigationAggregate investigation = InvestigationAggregate.open(
            investigationId,
            regattaId,
            entryId,
            description
        );
        
        assertTrue(investigation.isOpen());
        assertEquals(1, investigation.getUncommittedEvents().size());
        investigation.markEventsAsCommitted();
        
        // Act - Update description
        String updatedDescription = "False start investigation - reviewed video evidence";
        investigation.updateDescription(updatedDescription);
        
        assertEquals(updatedDescription, investigation.getDescription());
        assertEquals(1, investigation.getUncommittedEvents().size());
        investigation.markEventsAsCommitted();
        
        // Act - Close with no action
        investigation.close(InvestigationOutcome.NO_ACTION, null);
        
        // Assert
        assertFalse(investigation.isOpen());
        assertEquals(InvestigationOutcome.NO_ACTION, investigation.getOutcome());
        assertNull(investigation.getPenaltySeconds());
        assertNotNull(investigation.getClosedAt());
        assertEquals(1, investigation.getUncommittedEvents().size());
    }

    @Test
    void completeInvestigationWorkflow_withPenalty() {
        // Arrange
        UUID investigationId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        
        // Act - Open, update, and close with penalty
        InvestigationAggregate investigation = InvestigationAggregate.open(
            investigationId,
            regattaId,
            entryId,
            "Incorrect lane navigation"
        );
        investigation.markEventsAsCommitted();
        
        investigation.updateDescription("Incorrect lane navigation - confirmed by multiple witnesses");
        investigation.markEventsAsCommitted();
        
        Integer penaltySeconds = 30;
        investigation.close(InvestigationOutcome.PENALTY, penaltySeconds);
        
        // Assert
        assertFalse(investigation.isOpen());
        assertEquals(InvestigationOutcome.PENALTY, investigation.getOutcome());
        assertEquals(penaltySeconds, investigation.getPenaltySeconds());
        assertNotNull(investigation.getClosedAt());
    }

    @Test
    void tribunalEscalationWorkflow() {
        // Arrange - Create and close investigation
        UUID investigationId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        
        InvestigationAggregate investigation = InvestigationAggregate.open(
            investigationId,
            regattaId,
            entryId,
            "Unsportsmanlike conduct"
        );
        investigation.markEventsAsCommitted();
        
        investigation.close(InvestigationOutcome.PENALTY, 60);
        investigation.markEventsAsCommitted();
        
        assertFalse(investigation.isOpen());
        
        // Act - Reopen for tribunal (escalation)
        investigation.reopen();
        
        // Assert
        assertTrue(investigation.isOpen());
        assertNull(investigation.getOutcome());
        assertNull(investigation.getPenaltySeconds());
        assertNull(investigation.getClosedAt());
        
        // Can update after reopening
        investigation.updateDescription("Unsportsmanlike conduct - escalated to tribunal review");
        investigation.markEventsAsCommitted();
        
        // Close with different outcome
        investigation.close(InvestigationOutcome.DSQ, null);
        
        assertFalse(investigation.isOpen());
        assertEquals(InvestigationOutcome.DSQ, investigation.getOutcome());
    }

    @Test
    void multipleInvestigationsPerEntry_scenario() {
        // Scenario: Entry has two separate investigations
        UUID regattaId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        
        // Investigation 1: False start - resolved with penalty
        InvestigationAggregate investigation1 = InvestigationAggregate.open(
            UUID.randomUUID(),
            regattaId,
            entryId,
            "False start"
        );
        investigation1.markEventsAsCommitted();
        investigation1.close(InvestigationOutcome.PENALTY, 30);
        
        // Investigation 2: Equipment violation - resolved with no action
        InvestigationAggregate investigation2 = InvestigationAggregate.open(
            UUID.randomUUID(),
            regattaId,
            entryId,
            "Equipment violation complaint"
        );
        investigation2.markEventsAsCommitted();
        investigation2.close(InvestigationOutcome.NO_ACTION, null);
        
        // Assert both investigations are independent
        assertFalse(investigation1.isOpen());
        assertEquals(InvestigationOutcome.PENALTY, investigation1.getOutcome());
        assertEquals(30, investigation1.getPenaltySeconds());
        
        assertFalse(investigation2.isOpen());
        assertEquals(InvestigationOutcome.NO_ACTION, investigation2.getOutcome());
        assertNull(investigation2.getPenaltySeconds());
    }

    @Test
    void eventSourcingReplay_reconstructsStateCorrectly() {
        // Arrange - Create investigation and generate events
        UUID investigationId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        
        InvestigationAggregate original = InvestigationAggregate.open(
            investigationId,
            regattaId,
            entryId,
            "Initial description"
        );
        
        // Collect all events as they're generated
        var allEvents = new java.util.ArrayList<>(original.getUncommittedEvents());
        original.markEventsAsCommitted();
        
        original.updateDescription("Updated description");
        allEvents.addAll(original.getUncommittedEvents());
        original.markEventsAsCommitted();
        
        original.close(InvestigationOutcome.PENALTY, 45);
        allEvents.addAll(original.getUncommittedEvents());
        
        // Act - Replay all events to reconstruct state
        InvestigationAggregate replayed = new InvestigationAggregate(investigationId);
        replayed.loadFromHistory(allEvents);
        
        // Assert - Replayed state matches original
        assertEquals(original.getRegattaId(), replayed.getRegattaId());
        assertEquals(original.getEntryId(), replayed.getEntryId());
        assertEquals(original.getDescription(), replayed.getDescription());
        assertEquals(original.getOutcome(), replayed.getOutcome());
        assertEquals(original.getPenaltySeconds(), replayed.getPenaltySeconds());
        assertNotNull(replayed.getClosedAt());
        assertFalse(replayed.isOpen());
    }

    @Test
    void penaltyValidation_enforcesBusinessRules() {
        // Test that penalty business rules are enforced
        UUID investigationId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        
        InvestigationAggregate investigation = InvestigationAggregate.open(
            investigationId,
            regattaId,
            entryId,
            "Test investigation"
        );
        
        // Penalty outcome requires penalty seconds
        assertThrows(IllegalArgumentException.class, () -> {
            investigation.close(InvestigationOutcome.PENALTY, null);
        });
        
        // Penalty seconds must be positive
        assertThrows(IllegalArgumentException.class, () -> {
            investigation.close(InvestigationOutcome.PENALTY, 0);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            investigation.close(InvestigationOutcome.PENALTY, -30);
        });
        
        // Non-penalty outcomes should not have penalty seconds (though allowed to be null)
        // This is allowed in the current implementation - no validation that penaltySeconds
        // must be null for non-PENALTY outcomes
    }

    @Test
    void investigationStateTransition_validation() {
        // Test state transition rules are enforced
        UUID investigationId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        
        InvestigationAggregate investigation = InvestigationAggregate.open(
            investigationId,
            regattaId,
            entryId,
            "Test investigation"
        );
        
        // Cannot reopen when already open
        assertThrows(IllegalStateException.class, () -> {
            investigation.reopen();
        });
        
        // Close investigation
        investigation.close(InvestigationOutcome.NO_ACTION, null);
        
        // Cannot update when closed
        assertThrows(IllegalStateException.class, () -> {
            investigation.updateDescription("New description");
        });
        
        // Cannot close when already closed
        assertThrows(IllegalStateException.class, () -> {
            investigation.close(InvestigationOutcome.PENALTY, 30);
        });
        
        // Can reopen after closing
        investigation.reopen();
        assertTrue(investigation.isOpen());
        
        // Can update after reopening
        investigation.updateDescription("Updated after reopening");
        assertEquals("Updated after reopening", investigation.getDescription());
    }
}
