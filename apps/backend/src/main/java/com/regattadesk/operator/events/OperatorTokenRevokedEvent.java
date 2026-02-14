package com.regattadesk.operator.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when an operator token is revoked.
 */
public final class OperatorTokenRevokedEvent extends OperatorTokenEvent {
    
    private final String reason;
    
    public OperatorTokenRevokedEvent(
            UUID tokenId,
            UUID regattaId,
            String station,
            String reason,
            Instant occurredAt,
            String performedBy) {
        super(tokenId, regattaId, station, occurredAt, performedBy);
        this.reason = reason;
    }
    
    public String getReason() {
        return reason;
    }
    
    @Override
    public String getEventType() {
        return "OperatorTokenRevoked";
    }
    
    @Override
    public String toString() {
        return "OperatorTokenRevokedEvent{" +
               "tokenId=" + getTokenId() +
               ", regattaId=" + getRegattaId() +
               ", station='" + getStation() + '\'' +
               ", reason='" + reason + '\'' +
               ", occurredAt=" + getOccurredAt() +
               ", performedBy='" + getPerformedBy() + '\'' +
               '}';
    }
}
