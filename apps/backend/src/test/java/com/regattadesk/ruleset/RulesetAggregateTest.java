package com.regattadesk.ruleset;

import com.regattadesk.eventstore.DomainEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RulesetAggregate.
 * 
 * Tests verify:
 * - Aggregate creation and validation
 * - Event emission and state updates
 * - State reconstruction from events
 * - Duplication logic
 * - Update operations
 */
class RulesetAggregateTest {
    
    @Test
    void testCreateRuleset() {
        UUID id = UUID.randomUUID();
        String name = "FISA Standard Rules 2026";
        String version = "v2026.1";
        String description = "Standard FISA rules for 2026 season";
        String ageCalculationType = "actual_at_start";
        
        RulesetAggregate ruleset = RulesetAggregate.create(
                id, name, version, description, ageCalculationType
        );
        
        assertEquals(id, ruleset.getId());
        assertEquals(name, ruleset.getName());
        assertEquals(version, ruleset.getRulesetVersion());
        assertEquals(description, ruleset.getDescription());
        assertEquals(ageCalculationType, ruleset.getAgeCalculationType());
        assertFalse(ruleset.isGlobal());
        
        // Verify event was emitted
        List<DomainEvent> events = ruleset.getUncommittedEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof RulesetCreatedEvent);
        
        RulesetCreatedEvent event = (RulesetCreatedEvent) events.get(0);
        assertEquals(id, event.getRulesetId());
        assertEquals(name, event.getName());
        assertEquals(version, event.getVersion());
        assertEquals(description, event.getDescription());
        assertEquals(ageCalculationType, event.getAgeCalculationType());
        assertFalse(event.isGlobal());
    }
    
    @Test
    void testCreateRulesetWithAgeAsOfJan1() {
        UUID id = UUID.randomUUID();
        String name = "Dutch Rules 2026";
        String version = "v1.0";
        String ageCalculationType = "age_as_of_jan_1";
        
        RulesetAggregate ruleset = RulesetAggregate.create(
                id, name, version, null, ageCalculationType
        );
        
        assertEquals(ageCalculationType, ruleset.getAgeCalculationType());
        assertNull(ruleset.getDescription());
    }
    
    @Test
    void testCreateRulesetNormalizesAgeCalculationType() {
        UUID id = UUID.randomUUID();
        String ageCalculationType = "ACTUAL_AT_START";  // Uppercase
        
        RulesetAggregate ruleset = RulesetAggregate.create(
                id, "Test Ruleset", "v1.0", null, ageCalculationType
        );
        
        assertEquals("actual_at_start", ruleset.getAgeCalculationType());
    }
    
    @Test
    void testCreateRulesetWithNullNameThrowsException() {
        UUID id = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            RulesetAggregate.create(id, null, "v1.0", "Description", "actual_at_start");
        });
    }
    
    @Test
    void testCreateRulesetWithBlankNameThrowsException() {
        UUID id = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            RulesetAggregate.create(id, "  ", "v1.0", "Description", "actual_at_start");
        });
    }
    
    @Test
    void testCreateRulesetWithNullVersionThrowsException() {
        UUID id = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            RulesetAggregate.create(id, "Test Ruleset", null, "Description", "actual_at_start");
        });
    }
    
    @Test
    void testCreateRulesetWithBlankVersionThrowsException() {
        UUID id = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            RulesetAggregate.create(id, "Test Ruleset", "", "Description", "actual_at_start");
        });
    }
    
    @Test
    void testCreateRulesetWithNullAgeCalculationTypeThrowsException() {
        UUID id = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            RulesetAggregate.create(id, "Test Ruleset", "v1.0", "Description", null);
        });
    }
    
    @Test
    void testCreateRulesetWithInvalidAgeCalculationTypeThrowsException() {
        UUID id = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            RulesetAggregate.create(id, "Test Ruleset", "v1.0", "Description", "invalid_type");
        });
    }
    
    @Test
    void testDuplicateRuleset() {
        UUID sourceId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        
        // Create source ruleset
        RulesetAggregate source = RulesetAggregate.create(
                sourceId, 
                "Original Rules", 
                "v1.0", 
                "Original description",
                "actual_at_start"
        );
        
        // Duplicate with new name and version
        RulesetAggregate duplicated = RulesetAggregate.duplicate(
                newId,
                source,
                "Modified Rules",
                "v2.0"
        );
        
        assertEquals(newId, duplicated.getId());
        assertEquals("Modified Rules", duplicated.getName());
        assertEquals("v2.0", duplicated.getRulesetVersion());
        assertEquals("Original description", duplicated.getDescription());
        assertEquals("actual_at_start", duplicated.getAgeCalculationType());
        assertFalse(duplicated.isGlobal());
        
        // Verify event was emitted
        List<DomainEvent> events = duplicated.getUncommittedEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof RulesetDuplicatedEvent);
        
        RulesetDuplicatedEvent event = (RulesetDuplicatedEvent) events.get(0);
        assertEquals(newId, event.getRulesetId());
        assertEquals(sourceId, event.getSourceRulesetId());
        assertEquals("Modified Rules", event.getName());
        assertEquals("v2.0", event.getVersion());
    }
    
    @Test
    void testDuplicateRulesetWithNullNameThrowsException() {
        UUID sourceId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        
        RulesetAggregate source = RulesetAggregate.create(
                sourceId, "Original Rules", "v1.0", "Description", "actual_at_start"
        );
        
        assertThrows(IllegalArgumentException.class, () -> {
            RulesetAggregate.duplicate(newId, source, null, "v2.0");
        });
    }
    
    @Test
    void testDuplicateRulesetWithNullVersionThrowsException() {
        UUID sourceId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        
        RulesetAggregate source = RulesetAggregate.create(
                sourceId, "Original Rules", "v1.0", "Description", "actual_at_start"
        );
        
        assertThrows(IllegalArgumentException.class, () -> {
            RulesetAggregate.duplicate(newId, source, "Modified Rules", null);
        });
    }
    
    @Test
    void testUpdateRuleset() {
        UUID id = UUID.randomUUID();
        
        RulesetAggregate ruleset = RulesetAggregate.create(
                id, "Original Name", "v1.0", "Original description", "actual_at_start"
        );
        
        ruleset.markEventsAsCommitted();
        
        ruleset.update(
                "Updated Name",
                "v1.1",
                "Updated description",
                "age_as_of_jan_1"
        );
        
        assertEquals("Updated Name", ruleset.getName());
        assertEquals("v1.1", ruleset.getRulesetVersion());
        assertEquals("Updated description", ruleset.getDescription());
        assertEquals("age_as_of_jan_1", ruleset.getAgeCalculationType());
        
        // Verify event was emitted
        List<DomainEvent> events = ruleset.getUncommittedEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof RulesetUpdatedEvent);
        
        RulesetUpdatedEvent event = (RulesetUpdatedEvent) events.get(0);
        assertEquals(id, event.getRulesetId());
        assertEquals("Updated Name", event.getName());
        assertEquals("v1.1", event.getVersion());
        assertEquals("Updated description", event.getDescription());
        assertEquals("age_as_of_jan_1", event.getAgeCalculationType());
    }
    
    @Test
    void testUpdateRulesetWithNullNameThrowsException() {
        UUID id = UUID.randomUUID();
        
        RulesetAggregate ruleset = RulesetAggregate.create(
                id, "Original Name", "v1.0", "Description", "actual_at_start"
        );
        
        assertThrows(IllegalArgumentException.class, () -> {
            ruleset.update(null, "v1.1", "Description", "actual_at_start");
        });
    }
    
    @Test
    void testUpdateRulesetWithInvalidAgeCalculationTypeThrowsException() {
        UUID id = UUID.randomUUID();
        
        RulesetAggregate ruleset = RulesetAggregate.create(
                id, "Original Name", "v1.0", "Description", "actual_at_start"
        );
        
        assertThrows(IllegalArgumentException.class, () -> {
            ruleset.update("Updated Name", "v1.1", "Description", "invalid_type");
        });
    }
    
    @Test
    void testLoadRulesetFromHistory() {
        UUID id = UUID.randomUUID();
        String name = "Test Ruleset";
        String version = "v1.0";
        String description = "Test Description";
        String ageCalculationType = "actual_at_start";
        
        // Create event
        RulesetCreatedEvent event = new RulesetCreatedEvent(
                id, name, version, description, ageCalculationType, false
        );
        
        // Load aggregate from event history
        RulesetAggregate ruleset = new RulesetAggregate(id);
        ruleset.loadFromHistory(List.of(event));
        
        // Verify state was reconstructed
        assertEquals(name, ruleset.getName());
        assertEquals(version, ruleset.getRulesetVersion());
        assertEquals(description, ruleset.getDescription());
        assertEquals(ageCalculationType, ruleset.getAgeCalculationType());
        assertFalse(ruleset.isGlobal());
        
        // No uncommitted events after loading from history
        assertTrue(ruleset.getUncommittedEvents().isEmpty());
    }
    
    @Test
    void testLoadRulesetFromHistoryWithDuplication() {
        UUID sourceId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        
        // Create duplication event
        RulesetDuplicatedEvent event = new RulesetDuplicatedEvent(
                id, 
                sourceId,
                "Duplicated Ruleset",
                "v2.0",
                "Duplicated description",
                "age_as_of_jan_1",
                false
        );
        
        // Load aggregate from event history
        RulesetAggregate ruleset = new RulesetAggregate(id);
        ruleset.loadFromHistory(List.of(event));
        
        // Verify state was reconstructed
        assertEquals("Duplicated Ruleset", ruleset.getName());
        assertEquals("v2.0", ruleset.getRulesetVersion());
        assertEquals("Duplicated description", ruleset.getDescription());
        assertEquals("age_as_of_jan_1", ruleset.getAgeCalculationType());
        assertFalse(ruleset.isGlobal());
    }
    
    @Test
    void testLoadRulesetFromHistoryWithUpdate() {
        UUID id = UUID.randomUUID();
        
        // Create event history
        RulesetCreatedEvent created = new RulesetCreatedEvent(
                id, "Original Name", "v1.0", "Original description", "actual_at_start", false
        );
        RulesetUpdatedEvent updated = new RulesetUpdatedEvent(
                id, "Updated Name", "v1.1", "Updated description", "age_as_of_jan_1"
        );
        
        // Load aggregate from event history
        RulesetAggregate ruleset = new RulesetAggregate(id);
        ruleset.loadFromHistory(List.of(created, updated));
        
        // Verify state reflects latest update
        assertEquals("Updated Name", ruleset.getName());
        assertEquals("v1.1", ruleset.getRulesetVersion());
        assertEquals("Updated description", ruleset.getDescription());
        assertEquals("age_as_of_jan_1", ruleset.getAgeCalculationType());
        
        // No uncommitted events after loading from history
        assertTrue(ruleset.getUncommittedEvents().isEmpty());
    }
    
    @Test
    void testAggregateType() {
        UUID id = UUID.randomUUID();
        RulesetAggregate ruleset = new RulesetAggregate(id);
        
        assertEquals("Ruleset", ruleset.getAggregateType());
    }
}
