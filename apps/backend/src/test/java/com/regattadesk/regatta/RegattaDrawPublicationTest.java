package com.regattadesk.regatta;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for draw publication functionality in RegattaAggregate.
 * 
 * Tests verify:
 * - Draw publication increments draw_revision
 * - Draw seed is stored with publication
 * - Post-publication insertion ban is enforced
 * - Draw can only be published once per revision cycle
 */
class RegattaDrawPublicationTest {
    
    @Test
    void testPublishDrawIncrementsRevision() {
        UUID regattaId = UUID.randomUUID();
        RegattaAggregate regatta = RegattaAggregate.create(
            regattaId,
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            new BigDecimal("50.00"),
            "EUR", null, null
        );
        regatta.markEventsAsCommitted();
        
        long drawSeed = 123456L;
        regatta.publishDraw(drawSeed);
        
        assertEquals(1, regatta.getUncommittedEvents().size());
        DrawPublishedEvent event = (DrawPublishedEvent) regatta.getUncommittedEvents().get(0);
        assertEquals("DrawPublished", event.getEventType());
        assertEquals(regattaId, event.getRegattaId());
        assertEquals(drawSeed, event.getDrawSeed());
        assertEquals(1, event.getDrawRevision());
    }
    
    @Test
    void testPublishDrawRequiresUnpublishBeforeRepublish() {
        UUID regattaId = UUID.randomUUID();
        RegattaAggregate regatta = RegattaAggregate.create(
            regattaId,
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            new BigDecimal("50.00"),
            "EUR", null, null
        );
        regatta.markEventsAsCommitted();
        
        // First publication
        regatta.publishDraw(111L);
        regatta.markEventsAsCommitted();

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> regatta.publishDraw(222L));
        assertEquals("Draw is already published", exception.getMessage());
    }
    
    @Test
    void testGetDrawRevisionAfterPublication() {
        UUID regattaId = UUID.randomUUID();
        RegattaAggregate regatta = RegattaAggregate.create(
            regattaId,
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            new BigDecimal("50.00"),
            "EUR", null, null
        );
        
        assertEquals(0, regatta.getDrawRevision());
        
        regatta.markEventsAsCommitted();
        regatta.publishDraw(123L);
        regatta.markEventsAsCommitted();
        
        assertEquals(1, regatta.getDrawRevision());
    }
    
    @Test
    void testEnforceNoInsertionAfterDraw() {
        UUID regattaId = UUID.randomUUID();
        RegattaAggregate regatta = RegattaAggregate.create(
            regattaId,
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            new BigDecimal("50.00"),
            "EUR", null, null
        );
        regatta.markEventsAsCommitted();
        
        // Publish draw
        regatta.publishDraw(123L);
        regatta.markEventsAsCommitted();
        
        // Attempt to add entry should throw exception
        UUID entryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID crewId = UUID.randomUUID();
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            regatta.addEntry(entryId, eventId, blockId, crewId, null);
        });
        assertEquals("Cannot add entry after draw publication in v0.1", exception.getMessage());
    }
    
    @Test
    void testAddEntryBeforeDrawPublicationIsAllowed() {
        UUID regattaId = UUID.randomUUID();
        RegattaAggregate regatta = RegattaAggregate.create(
            regattaId,
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            new BigDecimal("50.00"),
            "EUR", null, null
        );
        regatta.markEventsAsCommitted();
        
        // Add entry before draw publication should work
        UUID entryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID crewId = UUID.randomUUID();
        
        // This should not throw
        assertDoesNotThrow(() -> {
            regatta.addEntry(entryId, eventId, blockId, crewId, null);
        });
    }
    
    @Test
    void testDrawRevisionTrackingAcrossPublishUnpublishCycles() {
        UUID regattaId = UUID.randomUUID();
        RegattaAggregate regatta = RegattaAggregate.create(
            regattaId,
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            new BigDecimal("50.00"),
            "EUR", null, null
        );
        
        // Initial revision
        assertEquals(0, regatta.getDrawRevision());
        
        // After first publication
        regatta.markEventsAsCommitted();
        regatta.publishDraw(111L);
        regatta.markEventsAsCommitted();
        assertEquals(1, regatta.getDrawRevision());

        // After unpublish, the setup returns to editable draft state.
        regatta.unpublishDraw();
        regatta.markEventsAsCommitted();
        assertEquals(1, regatta.getDrawRevision());
        assertFalse(regatta.isDrawPublished());

        // Re-publish advances the public revision instead of reusing v1.
        regatta.publishDraw(222L);
        regatta.markEventsAsCommitted();
        assertEquals(2, regatta.getDrawRevision());
        assertTrue(regatta.isDrawPublished());
    }
    
    @Test
    void testPublishDrawWithNegativeSeedStillWorks() {
        // Negative seeds should be allowed as they're valid for Java Random
        UUID regattaId = UUID.randomUUID();
        RegattaAggregate regatta = RegattaAggregate.create(
            regattaId,
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            new BigDecimal("50.00"),
            "EUR", null, null
        );
        regatta.markEventsAsCommitted();
        
        long negativeSeed = -123456L;
        regatta.publishDraw(negativeSeed);
        
        DrawPublishedEvent event = (DrawPublishedEvent) regatta.getUncommittedEvents().get(0);
        assertEquals(negativeSeed, event.getDrawSeed());
    }
}
