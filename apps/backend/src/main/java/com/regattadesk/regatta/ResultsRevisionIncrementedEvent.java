package com.regattadesk.regatta;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.util.UUID;

/**
 * Event raised when the results revision is incremented (BC07-002).
 * 
 * Results revision changes affect public results pages and require cache invalidation.
 */
public class ResultsRevisionIncrementedEvent implements DomainEvent {
    private final UUID regattaId;
    private final int newResultsRevision;
    private final String reason;
    
    @JsonCreator
    public ResultsRevisionIncrementedEvent(
        @JsonProperty("regattaId") UUID regattaId,
        @JsonProperty("newResultsRevision") int newResultsRevision,
        @JsonProperty("reason") String reason
    ) {
        this.regattaId = regattaId;
        this.newResultsRevision = newResultsRevision;
        this.reason = reason;
    }
    
    @Override
    public String getEventType() {
        return "ResultsRevisionIncrementedEvent";
    }
    
    @Override
    public UUID getAggregateId() {
        return regattaId;
    }
    
    public UUID getRegattaId() {
        return regattaId;
    }
    
    public int getNewResultsRevision() {
        return newResultsRevision;
    }
    
    public String getReason() {
        return reason;
    }
}
