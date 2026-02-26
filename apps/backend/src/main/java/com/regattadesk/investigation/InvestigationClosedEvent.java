package com.regattadesk.investigation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event for investigation closure (BC07-001).
 * 
 * Emitted when an investigation is closed with an outcome.
 */
public class InvestigationClosedEvent implements DomainEvent {
    private final UUID investigationId;
    private final InvestigationOutcome outcome;
    private final Integer penaltySeconds;
    private final Instant closedAt;

    @JsonCreator
    public InvestigationClosedEvent(
        @JsonProperty("investigationId") UUID investigationId,
        @JsonProperty("outcome") InvestigationOutcome outcome,
        @JsonProperty("penaltySeconds") Integer penaltySeconds,
        @JsonProperty("closedAt") Instant closedAt
    ) {
        this.investigationId = investigationId;
        this.outcome = outcome;
        this.penaltySeconds = penaltySeconds;
        this.closedAt = closedAt;
    }

    @Override
    public String getEventType() {
        return "InvestigationClosedEvent";
    }

    @Override
    public UUID getAggregateId() {
        return investigationId;
    }

    public UUID getInvestigationId() { return investigationId; }
    public InvestigationOutcome getOutcome() { return outcome; }
    public Integer getPenaltySeconds() { return penaltySeconds; }
    public Instant getClosedAt() { return closedAt; }
}
