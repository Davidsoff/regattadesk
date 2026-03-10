package com.regattadesk.operator.events;

import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when a new capture session is started.
 */
public record CaptureSessionStartedEvent(
        UUID captureSessionId,
        UUID regattaId,
        UUID blockId,
        String station,
        String deviceId,
        String sessionType,
        int fps,
        Instant occurredAt,
        String actor
) implements DomainEvent {

    @Override
    public UUID getAggregateId() {
        return captureSessionId;
    }

    @Override
    public String getEventType() {
        return "CaptureSessionStarted";
    }
}
