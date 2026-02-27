package com.regattadesk.entry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event raised when an entry is disqualified (DSQ) (BC07-002).
 * 
 * Captures the prior status before DSQ for reversibility.
 */
public class EntryDisqualifiedEvent implements DomainEvent {
    private final UUID entryId;
    private final String priorStatus;
    private final String reason;
    private final Instant disqualifiedAt;
    
    @JsonCreator
    public EntryDisqualifiedEvent(
        @JsonProperty("entryId") UUID entryId,
        @JsonProperty("priorStatus") String priorStatus,
        @JsonProperty("reason") String reason
    ) {
        this.entryId = entryId;
        this.priorStatus = priorStatus;
        this.reason = reason;
        this.disqualifiedAt = Instant.now();
    }
    
    @Override
    public String getEventType() {
        return "EntryDisqualifiedEvent";
    }
    
    @Override
    public UUID getAggregateId() {
        return entryId;
    }
    
    public UUID getEntryId() {
        return entryId;
    }
    
    public String getPriorStatus() {
        return priorStatus;
    }
    
    public String getReason() {
        return reason;
    }
    
    public Instant getDisqualifiedAt() {
        return disqualifiedAt;
    }
}
