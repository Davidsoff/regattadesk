package com.regattadesk.investigation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.util.UUID;

/**
 * Domain event for investigation opening (BC07-001).
 * 
 * Emitted when a new investigation is opened for an entry.
 */
public class InvestigationOpenedEvent implements DomainEvent {
    private final UUID investigationId;
    private final UUID regattaId;
    private final UUID entryId;
    private final String description;

    @JsonCreator
    public InvestigationOpenedEvent(
        @JsonProperty("investigationId") UUID investigationId,
        @JsonProperty("regattaId") UUID regattaId,
        @JsonProperty("entryId") UUID entryId,
        @JsonProperty("description") String description
    ) {
        this.investigationId = investigationId;
        this.regattaId = regattaId;
        this.entryId = entryId;
        this.description = description;
    }

    @Override
    public String getEventType() {
        return "InvestigationOpened";
    }

    @Override
    public UUID getAggregateId() {
        return investigationId;
    }

    public UUID getInvestigationId() { return investigationId; }
    public UUID getRegattaId() { return regattaId; }
    public UUID getEntryId() { return entryId; }
    public String getDescription() { return description; }
}
