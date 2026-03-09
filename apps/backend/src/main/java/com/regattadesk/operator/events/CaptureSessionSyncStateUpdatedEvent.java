package com.regattadesk.operator.events;

import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when the sync state of a capture session is updated.
 */
public record CaptureSessionSyncStateUpdatedEvent(
        UUID captureSessionId,
        UUID regattaId,
        boolean isSynced,
        boolean driftExceededThreshold,
        String unsyncedReason,
        Instant occurredAt,
        String actor
) implements DomainEvent {

    @Override
    public UUID getAggregateId() {
        return captureSessionId;
    }

    @Override
    public String getEventType() {
        return "CaptureSessionSyncStateUpdated";
    }
}
