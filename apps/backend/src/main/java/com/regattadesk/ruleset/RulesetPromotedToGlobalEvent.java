package com.regattadesk.ruleset;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a ruleset is promoted to the global catalog.
 * 
 * This event captures the promotion action along with immutable provenance metadata
 * including the actor who performed the promotion.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RulesetPromotedToGlobalEvent implements DomainEvent {
    
    private final UUID rulesetId;
    private final String promotedBy;
    private final Instant promotedAt;
    
    @JsonCreator
    public RulesetPromotedToGlobalEvent(@JsonProperty("rulesetId") UUID rulesetId,
                                       @JsonProperty("promotedBy") String promotedBy,
                                       @JsonProperty("promotedAt") Instant promotedAt) {
        this.rulesetId = rulesetId;
        this.promotedBy = promotedBy;
        this.promotedAt = promotedAt;
    }
    
    @Override
    public String getEventType() {
        return "RulesetPromotedToGlobal";
    }
    
    @Override
    public UUID getAggregateId() {
        return rulesetId;
    }
    
    public UUID getRulesetId() {
        return rulesetId;
    }
    
    public String getPromotedBy() {
        return promotedBy;
    }
    
    public Instant getPromotedAt() {
        return promotedAt;
    }
}
