package com.regattadesk.regatta;

import com.regattadesk.eventstore.DomainEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RegattaAggregate.
 * 
 * Tests verify:
 * - Aggregate creation and validation
 * - Event emission and state updates
 * - State reconstruction from events
 */
class RegattaAggregateTest {
    
    @Test
    void testCreateRegatta() {
        UUID id = UUID.randomUUID();
        String name = "Amsterdam Head Race 2026";
        String description = "Annual head race in Amsterdam";
        String timeZone = "Europe/Amsterdam";
        BigDecimal entryFee = new BigDecimal("25.00");
        String currency = "EUR";
        
        RegattaAggregate regatta = RegattaAggregate.create(
                id, name, description, timeZone, entryFee, currency
        );
        
        assertEquals(id, regatta.getId());
        assertEquals(name, regatta.getName());
        assertEquals(description, regatta.getDescription());
        assertEquals(timeZone, regatta.getTimeZone());
        assertEquals(entryFee, regatta.getEntryFee());
        assertEquals(currency, regatta.getCurrency());
        assertEquals("draft", regatta.getStatus());
        assertEquals(0, regatta.getDrawRevision());
        assertEquals(0, regatta.getResultsRevision());
        
        // Verify event was emitted
        List<DomainEvent> events = regatta.getUncommittedEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof RegattaCreatedEvent);
        
        RegattaCreatedEvent event = (RegattaCreatedEvent) events.get(0);
        assertEquals(id, event.getRegattaId());
        assertEquals(name, event.getName());
        assertEquals(description, event.getDescription());
        assertEquals(timeZone, event.getTimeZone());
        assertEquals(entryFee, event.getEntryFee());
        assertEquals(currency, event.getCurrency());
    }
    
    @Test
    void testCreateRegattaWithNullNameThrowsException() {
        UUID id = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            RegattaAggregate.create(id, null, "Description", "Europe/Amsterdam", 
                    new BigDecimal("25.00"), "EUR");
        });
    }
    
    @Test
    void testCreateRegattaWithBlankNameThrowsException() {
        UUID id = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            RegattaAggregate.create(id, "  ", "Description", "Europe/Amsterdam", 
                    new BigDecimal("25.00"), "EUR");
        });
    }
    
    @Test
    void testCreateRegattaWithNullTimeZoneThrowsException() {
        UUID id = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            RegattaAggregate.create(id, "Test Regatta", "Description", null, 
                    new BigDecimal("25.00"), "EUR");
        });
    }
    
    @Test
    void testCreateRegattaWithNullEntryFeeThrowsException() {
        UUID id = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            RegattaAggregate.create(id, "Test Regatta", "Description", "Europe/Amsterdam", 
                    null, "EUR");
        });
    }
    
    @Test
    void testCreateRegattaWithNegativeEntryFeeThrowsException() {
        UUID id = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            RegattaAggregate.create(id, "Test Regatta", "Description", "Europe/Amsterdam", 
                    new BigDecimal("-10.00"), "EUR");
        });
    }
    
    @Test
    void testCreateRegattaWithNullCurrencyThrowsException() {
        UUID id = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            RegattaAggregate.create(id, "Test Regatta", "Description", "Europe/Amsterdam", 
                    new BigDecimal("25.00"), null);
        });
    }
    
    @Test
    void testLoadRegattaFromHistory() {
        UUID id = UUID.randomUUID();
        String name = "Test Regatta";
        String description = "Test Description";
        String timeZone = "Europe/Amsterdam";
        BigDecimal entryFee = new BigDecimal("30.00");
        String currency = "EUR";
        
        // Create event
        RegattaCreatedEvent event = new RegattaCreatedEvent(id, name, description, timeZone, entryFee, currency);
        
        // Load aggregate from event history
        RegattaAggregate regatta = new RegattaAggregate(id);
        regatta.loadFromHistory(List.of(event));
        
        // Verify state was reconstructed
        assertEquals(name, regatta.getName());
        assertEquals(description, regatta.getDescription());
        assertEquals(timeZone, regatta.getTimeZone());
        assertEquals(entryFee, regatta.getEntryFee());
        assertEquals(currency, regatta.getCurrency());
        assertEquals("draft", regatta.getStatus());
        
        // No uncommitted events after loading from history
        assertTrue(regatta.getUncommittedEvents().isEmpty());
    }
    
    @Test
    void testAggregateType() {
        UUID id = UUID.randomUUID();
        RegattaAggregate regatta = new RegattaAggregate(id);
        
        assertEquals("Regatta", regatta.getAggregateType());
    }
}
