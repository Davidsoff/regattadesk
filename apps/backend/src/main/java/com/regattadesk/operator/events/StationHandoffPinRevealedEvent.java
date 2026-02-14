package com.regattadesk.operator.events;

import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when a station handoff PIN is revealed.
 */
public record StationHandoffPinRevealedEvent(
    UUID handoffId,
    UUID regattaId,
    String station,
    String revealedBy,
    boolean adminReveal,
    Instant occurredAt,
    String actor
) implements DomainEvent {
    
    @Override
    public UUID getAggregateId() {
        return handoffId;
    }
    
    @Override
    public String getEventType() {
        return "StationHandoffPinRevealed";
    }
}
