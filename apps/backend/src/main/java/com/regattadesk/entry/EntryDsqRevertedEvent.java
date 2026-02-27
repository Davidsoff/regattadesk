package com.regattadesk.entry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event raised when a DSQ is reverted (BC07-002).
 * 
 * Restores the entry to its prior status before disqualification.
 */
public class EntryDsqRevertedEvent implements DomainEvent {
    private final UUID entryId;
    private final String restoredStatus;
    private final String reason;
    private final Instant revertedAt;
    
    @JsonCreator
    public EntryDsqRevertedEvent(
        @JsonProperty("entryId") UUID entryId,
        @JsonProperty("restoredStatus") String restoredStatus,
        @JsonProperty("reason") String reason
    ) {
        this.entryId = entryId;
        this.restoredStatus = restoredStatus;
        this.reason = reason;
        this.revertedAt = Instant.now();
    }
    
    @Override
    public String getEventType() {
        return "EntryDsqRevertedEvent";
    }
    
    @Override
    public UUID getAggregateId() {
        return entryId;
    }
    
    public UUID getEntryId() {
        return entryId;
    }
    
    public String getRestoredStatus() {
        return restoredStatus;
    }
    
    public String getReason() {
        return reason;
    }
    
    public Instant getRevertedAt() {
        return revertedAt;
    }
}
