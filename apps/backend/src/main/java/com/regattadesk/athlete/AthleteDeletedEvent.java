package com.regattadesk.athlete;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.util.UUID;

public class AthleteDeletedEvent implements DomainEvent {
    private final UUID athleteId;

    @JsonCreator
    public AthleteDeletedEvent(@JsonProperty("athleteId") UUID athleteId) {
        this.athleteId = athleteId;
    }

    @Override
    public String getEventType() {
        return "AthleteDeleted";
    }

    @Override
    public UUID getAggregateId() {
        return athleteId;
    }

    public UUID getAthleteId() { return athleteId; }
}
