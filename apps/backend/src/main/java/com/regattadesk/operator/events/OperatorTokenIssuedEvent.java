package com.regattadesk.operator.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when an operator token is issued.
 */
public final class OperatorTokenIssuedEvent extends OperatorTokenEvent {
    
    private final UUID blockId;
    private final Instant validFrom;
    private final Instant validUntil;
    
    public OperatorTokenIssuedEvent(
            UUID tokenId,
            UUID regattaId,
            UUID blockId,
            String station,
            Instant validFrom,
            Instant validUntil,
            Instant occurredAt,
            String performedBy) {
        super(tokenId, regattaId, station, occurredAt, performedBy);
        this.blockId = blockId;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }
    
    public UUID getBlockId() {
        return blockId;
    }
    
    public Instant getValidFrom() {
        return validFrom;
    }
    
    public Instant getValidUntil() {
        return validUntil;
    }
    
    @Override
    public String getEventType() {
        return "OperatorTokenIssued";
    }
    
    @Override
    public String toString() {
        return "OperatorTokenIssuedEvent{" +
               "tokenId=" + getTokenId() +
               ", regattaId=" + getRegattaId() +
               ", blockId=" + blockId +
               ", station='" + getStation() + '\'' +
               ", validFrom=" + validFrom +
               ", validUntil=" + validUntil +
               ", occurredAt=" + getOccurredAt() +
               ", performedBy='" + getPerformedBy() + '\'' +
               '}';
    }
}
