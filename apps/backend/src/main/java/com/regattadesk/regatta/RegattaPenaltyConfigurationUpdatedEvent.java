package com.regattadesk.regatta;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.util.UUID;

/**
 * Event emitted when regatta penalty configuration is updated (BC07-001).
 */
public class RegattaPenaltyConfigurationUpdatedEvent implements DomainEvent {
    
    private final UUID regattaId;
    private final Integer defaultPenaltySeconds;
    private final Boolean allowCustomPenaltySeconds;
    
    @JsonCreator
    public RegattaPenaltyConfigurationUpdatedEvent(
            @JsonProperty("regattaId") UUID regattaId,
            @JsonProperty("defaultPenaltySeconds") Integer defaultPenaltySeconds,
            @JsonProperty("allowCustomPenaltySeconds") Boolean allowCustomPenaltySeconds) {
        this.regattaId = regattaId;
        this.defaultPenaltySeconds = defaultPenaltySeconds;
        this.allowCustomPenaltySeconds = allowCustomPenaltySeconds;
    }
    
    @Override
    public String getEventType() {
        return "RegattaPenaltyConfigurationUpdated";
    }
    
    @Override
    public UUID getAggregateId() {
        return regattaId;
    }
    
    public UUID getRegattaId() {
        return regattaId;
    }
    
    public Integer getDefaultPenaltySeconds() {
        return defaultPenaltySeconds;
    }
    
    public Boolean getAllowCustomPenaltySeconds() {
        return allowCustomPenaltySeconds;
    }
}
