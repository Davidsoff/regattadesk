package com.regattadesk.investigation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.util.UUID;

/**
 * Domain event for investigation reopening (BC07-001).
 * 
 * Emitted when a closed investigation is reopened (tribunal escalation).
 */
public class InvestigationReopenedEvent implements DomainEvent {
    private final UUID investigationId;

    @JsonCreator
    public InvestigationReopenedEvent(
        @JsonProperty("investigationId") UUID investigationId
    ) {
        this.investigationId = investigationId;
    }

    @Override
    public String getEventType() {
        return "InvestigationReopenedEvent";
    }

    @Override
    public UUID getAggregateId() {
        return investigationId;
    }

    public UUID getInvestigationId() { return investigationId; }
}
