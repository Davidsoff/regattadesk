package com.regattadesk.operator.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when an operator token is validated (successfully or unsuccessfully).
 */
public final class OperatorTokenValidatedEvent extends OperatorTokenEvent {
    
    private final boolean validationSucceeded;
    private final String validationReason;
    
    public OperatorTokenValidatedEvent(
            UUID tokenId,
            UUID regattaId,
            String station,
            boolean validationSucceeded,
            String validationReason,
            Instant occurredAt,
            String performedBy) {
        super(tokenId, regattaId, station, occurredAt, performedBy);
        this.validationSucceeded = validationSucceeded;
        this.validationReason = validationReason;
    }
    
    public boolean isValidationSucceeded() {
        return validationSucceeded;
    }
    
    public String getValidationReason() {
        return validationReason;
    }
    
    @Override
    public String getEventType() {
        return "OperatorTokenValidated";
    }
    
    @Override
    public String toString() {
        return "OperatorTokenValidatedEvent{" +
               "tokenId=" + getTokenId() +
               ", regattaId=" + getRegattaId() +
               ", station='" + getStation() + '\'' +
               ", validationSucceeded=" + validationSucceeded +
               ", validationReason='" + validationReason + '\'' +
               ", occurredAt=" + getOccurredAt() +
               ", performedBy='" + getPerformedBy() + '\'' +
               '}';
    }
}
