package com.regattadesk.ruleset;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.util.UUID;

/**
 * Event emitted when a draw is published against a ruleset.
 */
public class RulesetDrawPublishedEvent implements DomainEvent {
    private final UUID rulesetId;

    @JsonCreator
    public RulesetDrawPublishedEvent(@JsonProperty("rulesetId") UUID rulesetId) {
        this.rulesetId = rulesetId;
    }

    @Override
    public UUID getAggregateId() {
        return rulesetId;
    }

    @Override
    public String getEventType() {
        return "RulesetDrawPublished";
    }

    public UUID getRulesetId() {
        return rulesetId;
    }
}
