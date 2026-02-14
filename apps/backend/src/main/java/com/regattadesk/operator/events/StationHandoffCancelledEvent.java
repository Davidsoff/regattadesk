package com.regattadesk.operator.events;

import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when a station handoff is cancelled.
 */
public record StationHandoffCancelledEvent(
    UUID handoffId,
    UUID regattaId,
    String station,
    String reason,
    Instant occurredAt,
    String actor
) implements DomainEvent {
    
    @Override
    public UUID getAggregateId() {
        return handoffId;
    }
    
    @Override
    public String getEventType() {
        return "StationHandoffCancelled";
    }
}
