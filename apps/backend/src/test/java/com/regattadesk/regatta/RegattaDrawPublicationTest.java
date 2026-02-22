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
            "EUR"
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
    void testPublishDrawMultipleTimes() {
        UUID regattaId = UUID.randomUUID();
        RegattaAggregate regatta = RegattaAggregate.create(
            regattaId,
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            new BigDecimal("50.00"),
            "EUR"
        );
        regatta.markEventsAsCommitted();
        
        // First publication
        regatta.publishDraw(111L);
        regatta.markEventsAsCommitted();
        
        // Second publication
        regatta.publishDraw(222L);
        
        DrawPublishedEvent event = (DrawPublishedEvent) regatta.getUncommittedEvents().get(0);
        assertEquals(2, event.getDrawRevision());
        assertEquals(222L, event.getDrawSeed());
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
            "EUR"
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
            "EUR"
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
        
        assertThrows(IllegalStateException.class, () -> {
            regatta.addEntry(entryId, eventId, blockId, crewId, null);
        }, "Cannot add entry after draw publication in v0.1");
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
            "EUR"
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
    void testDrawRevisionTracking() {
        UUID regattaId = UUID.randomUUID();
        RegattaAggregate regatta = RegattaAggregate.create(
            regattaId,
            "Test Regatta",
            "Description",
            "Europe/Amsterdam",
            new BigDecimal("50.00"),
            "EUR"
        );
        
        // Initial revision
        assertEquals(0, regatta.getDrawRevision());
        
        // After first publication
        regatta.markEventsAsCommitted();
        regatta.publishDraw(111L);
        regatta.markEventsAsCommitted();
        assertEquals(1, regatta.getDrawRevision());
        
        // After second publication
        regatta.publishDraw(222L);
        regatta.markEventsAsCommitted();
        assertEquals(2, regatta.getDrawRevision());
        
        // After third publication
        regatta.publishDraw(333L);
        regatta.markEventsAsCommitted();
        assertEquals(3, regatta.getDrawRevision());
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
            "EUR"
        );
        regatta.markEventsAsCommitted();
        
        long negativeSeed = -123456L;
        regatta.publishDraw(negativeSeed);
        
        DrawPublishedEvent event = (DrawPublishedEvent) regatta.getUncommittedEvents().get(0);
        assertEquals(negativeSeed, event.getDrawSeed());
    }
}
