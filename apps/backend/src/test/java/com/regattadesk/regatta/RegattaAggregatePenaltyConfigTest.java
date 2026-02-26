package com.regattadesk.regatta;

import com.regattadesk.eventstore.DomainEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RegattaAggregate penalty configuration (BC07-001).
 * 
 * Tests verify penalty configuration within regatta aggregate.
 */
class RegattaAggregatePenaltyConfigTest {

    @Test
    void createRegatta_shouldIncludePenaltyConfiguration() {
        // Arrange
        UUID id = UUID.randomUUID();
        String name = "Amsterdam Head Race 2026";
        String description = "Annual head race";
        String timeZone = "Europe/Amsterdam";
        BigDecimal entryFee = new BigDecimal("25.00");
        String currency = "EUR";
        Integer defaultPenaltySeconds = 30;
        Boolean allowCustomPenaltySeconds = true;
        
        // Act
        RegattaAggregate regatta = RegattaAggregate.create(
            id, name, description, timeZone, entryFee, currency,
            defaultPenaltySeconds, allowCustomPenaltySeconds
        );
        
        // Assert
        assertEquals(defaultPenaltySeconds, regatta.getDefaultPenaltySeconds());
        assertEquals(allowCustomPenaltySeconds, regatta.getAllowCustomPenaltySeconds());
        
        // Verify event
        List<DomainEvent> events = regatta.getUncommittedEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof RegattaCreatedEvent);
        
        RegattaCreatedEvent event = (RegattaCreatedEvent) events.get(0);
        assertEquals(defaultPenaltySeconds, event.getDefaultPenaltySeconds());
        assertEquals(allowCustomPenaltySeconds, event.getAllowCustomPenaltySeconds());
    }

    @Test
    void createRegatta_withNullDefaultPenalty_shouldUseDefault() {
        // Arrange
        UUID id = UUID.randomUUID();
        
        // Act
        RegattaAggregate regatta = RegattaAggregate.create(
            id, "Test", "Desc", "Europe/Amsterdam",
            new BigDecimal("25.00"), "EUR",
            null, null
        );
        
        // Assert
        assertEquals(60, regatta.getDefaultPenaltySeconds()); // Default 60 seconds
        assertFalse(regatta.getAllowCustomPenaltySeconds()); // Default false
    }

    @Test
    void createRegatta_withZeroDefaultPenalty_shouldBeAllowed() {
        // Arrange
        UUID id = UUID.randomUUID();
        
        // Act
        RegattaAggregate regatta = RegattaAggregate.create(
            id, "Test", "Desc", "Europe/Amsterdam",
            new BigDecimal("25.00"), "EUR",
            0, false
        );
        
        // Assert
        assertEquals(0, regatta.getDefaultPenaltySeconds());
    }

    @Test
    void createRegatta_withNegativeDefaultPenalty_shouldThrowException() {
        // Arrange
        UUID id = UUID.randomUUID();
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            RegattaAggregate.create(
                id, "Test", "Desc", "Europe/Amsterdam",
                new BigDecimal("25.00"), "EUR",
                -10, false
            );
        });
    }

    @Test
    void updatePenaltyConfiguration_shouldEmitEvent() {
        // Arrange
        RegattaAggregate regatta = RegattaAggregate.create(
            UUID.randomUUID(), "Test", "Desc", "Europe/Amsterdam",
            new BigDecimal("25.00"), "EUR",
            30, false
        );
        regatta.markEventsAsCommitted();
        
        // Act
        Integer newDefaultPenalty = 45;
        Boolean newAllowCustom = true;
        regatta.updatePenaltyConfiguration(newDefaultPenalty, newAllowCustom);
        
        // Assert
        assertEquals(newDefaultPenalty, regatta.getDefaultPenaltySeconds());
        assertEquals(newAllowCustom, regatta.getAllowCustomPenaltySeconds());
        
        List<DomainEvent> events = regatta.getUncommittedEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof RegattaPenaltyConfigurationUpdatedEvent);
        
        RegattaPenaltyConfigurationUpdatedEvent event = 
            (RegattaPenaltyConfigurationUpdatedEvent) events.get(0);
        assertEquals(regatta.getId(), event.getRegattaId());
        assertEquals(newDefaultPenalty, event.getDefaultPenaltySeconds());
        assertEquals(newAllowCustom, event.getAllowCustomPenaltySeconds());
    }

    @Test
    void updatePenaltyConfiguration_withNegativeValue_shouldThrowException() {
        // Arrange
        RegattaAggregate regatta = RegattaAggregate.create(
            UUID.randomUUID(), "Test", "Desc", "Europe/Amsterdam",
            new BigDecimal("25.00"), "EUR",
            30, false
        );
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            regatta.updatePenaltyConfiguration(-10, false);
        });
    }

    @Test
    void loadFromHistory_shouldReconstructPenaltyConfiguration() {
        // Arrange
        UUID id = UUID.randomUUID();
        Integer defaultPenalty = 30;
        Boolean allowCustom = true;
        
        RegattaCreatedEvent createdEvent = new RegattaCreatedEvent(
            id, "Test", "Desc", "Europe/Amsterdam",
            new BigDecimal("25.00"), "EUR",
            defaultPenalty, allowCustom
        );
        
        // Act
        RegattaAggregate regatta = new RegattaAggregate(id);
        regatta.loadFromHistory(List.of(createdEvent));
        
        // Assert
        assertEquals(defaultPenalty, regatta.getDefaultPenaltySeconds());
        assertEquals(allowCustom, regatta.getAllowCustomPenaltySeconds());
    }

    @Test
    void loadFromHistory_withPenaltyUpdate_shouldReflectLatestConfiguration() {
        // Arrange
        UUID id = UUID.randomUUID();
        
        RegattaCreatedEvent createdEvent = new RegattaCreatedEvent(
            id, "Test", "Desc", "Europe/Amsterdam",
            new BigDecimal("25.00"), "EUR",
            30, false
        );
        
        RegattaPenaltyConfigurationUpdatedEvent updatedEvent = 
            new RegattaPenaltyConfigurationUpdatedEvent(id, 45, true);
        
        // Act
        RegattaAggregate regatta = new RegattaAggregate(id);
        regatta.loadFromHistory(List.of(createdEvent, updatedEvent));
        
        // Assert
        assertEquals(45, regatta.getDefaultPenaltySeconds());
        assertTrue(regatta.getAllowCustomPenaltySeconds());
    }
}
