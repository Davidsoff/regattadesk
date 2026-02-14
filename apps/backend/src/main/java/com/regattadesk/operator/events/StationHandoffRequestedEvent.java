package com.regattadesk.operator.events;

import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when a station handoff is requested.
 */
public record StationHandoffRequestedEvent(
    UUID handoffId,
    UUID regattaId,
    UUID tokenId,
    String station,
    String requestingDeviceId,
    Instant expiresAt,
    Instant occurredAt,
    String actor
) implements DomainEvent {
    
    @Override
    public UUID getAggregateId() {
        return handoffId;
    }
    
    @Override
    public String getEventType() {
        return "StationHandoffRequested";
    }
}
