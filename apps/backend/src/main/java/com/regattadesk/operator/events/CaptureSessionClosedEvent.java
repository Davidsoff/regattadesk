package com.regattadesk.operator.events;

import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when a capture session is closed.
 */
public record CaptureSessionClosedEvent(
        UUID captureSessionId,
        UUID regattaId,
        String closeReason,
        Instant closedAt,
        String actor
) implements DomainEvent {

    @Override
    public UUID getAggregateId() {
        return captureSessionId;
    }

    @Override
    public String getEventType() {
        return "CaptureSessionClosed";
    }
}
