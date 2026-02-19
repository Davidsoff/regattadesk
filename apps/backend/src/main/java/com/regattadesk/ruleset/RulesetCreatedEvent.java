package com.regattadesk.ruleset;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.util.UUID;

/**
 * Event emitted when a new ruleset is created.
 */
public class RulesetCreatedEvent implements DomainEvent {
    
    private final UUID rulesetId;
    private final String name;
    private final String version;
    private final String description;
    private final String ageCalculationType;
    private final boolean isGlobal;
    
    @JsonCreator
    public RulesetCreatedEvent(@JsonProperty("rulesetId") UUID rulesetId,
                              @JsonProperty("name") String name,
                              @JsonProperty("version") String version,
                              @JsonProperty("description") String description,
                              @JsonProperty("ageCalculationType") String ageCalculationType,
                              @JsonProperty("isGlobal") boolean isGlobal) {
        this.rulesetId = rulesetId;
        this.name = name;
        this.version = version;
        this.description = description;
        this.ageCalculationType = ageCalculationType;
        this.isGlobal = isGlobal;
    }
    
    @Override
    public String getEventType() {
        return "RulesetCreated";
    }
    
    @Override
    public UUID getAggregateId() {
        return rulesetId;
    }
    
    public UUID getRulesetId() {
        return rulesetId;
    }
    
    public String getName() {
        return name;
    }
    
    public String getVersion() {
        return version;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getAgeCalculationType() {
        return ageCalculationType;
    }
    
    public boolean isGlobal() {
        return isGlobal;
    }
}
