package com.regattadesk.operator.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for operator token lifecycle events.
 */
public abstract class OperatorTokenEvent {
    
    private final UUID tokenId;
    private final UUID regattaId;
    private final String station;
    private final Instant occurredAt;
    private final String performedBy;
    
    protected OperatorTokenEvent(
            UUID tokenId,
            UUID regattaId,
            String station,
            Instant occurredAt,
            String performedBy) {
        this.tokenId = tokenId;
        this.regattaId = regattaId;
        this.station = station;
        this.occurredAt = occurredAt;
        this.performedBy = performedBy;
    }
    
    public UUID getTokenId() {
        return tokenId;
    }
    
    public UUID getRegattaId() {
        return regattaId;
    }
    
    public String getStation() {
        return station;
    }
    
    public Instant getOccurredAt() {
        return occurredAt;
    }
    
    public String getPerformedBy() {
        return performedBy;
    }
    
    /**
     * Returns the event type identifier.
     */
    public abstract String getEventType();
}
