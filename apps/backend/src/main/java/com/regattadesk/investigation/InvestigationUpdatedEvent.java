package com.regattadesk.investigation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.util.UUID;

/**
 * Domain event for investigation description update (BC07-001).
 * 
 * Emitted when an investigation's description is updated.
 */
public class InvestigationUpdatedEvent implements DomainEvent {
    private final UUID investigationId;
    private final String description;

    @JsonCreator
    public InvestigationUpdatedEvent(
        @JsonProperty("investigationId") UUID investigationId,
        @JsonProperty("description") String description
    ) {
        this.investigationId = investigationId;
        this.description = description;
    }

    @Override
    public String getEventType() {
        return "InvestigationUpdatedEvent";
    }

    @Override
    public UUID getAggregateId() {
        return investigationId;
    }

    public UUID getInvestigationId() { return investigationId; }
    public String getDescription() { return description; }
}
