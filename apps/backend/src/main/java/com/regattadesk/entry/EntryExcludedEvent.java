package com.regattadesk.entry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event raised when an entry is excluded from the race (BC07-002).
 * 
 * Captures the prior status before exclusion for reversibility.
 */
public class EntryExcludedEvent implements DomainEvent {
    private final UUID entryId;
    private final String priorStatus;
    private final String reason;
    private final Instant excludedAt;
    
    @JsonCreator
    public EntryExcludedEvent(
        @JsonProperty("entryId") UUID entryId,
        @JsonProperty("priorStatus") String priorStatus,
        @JsonProperty("reason") String reason
    ) {
        this.entryId = entryId;
        this.priorStatus = priorStatus;
        this.reason = reason;
        this.excludedAt = Instant.now();
    }
    
    @Override
    public String getEventType() {
        return "EntryExcludedEvent";
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
    
    public Instant getExcludedAt() {
        return excludedAt;
    }
}
