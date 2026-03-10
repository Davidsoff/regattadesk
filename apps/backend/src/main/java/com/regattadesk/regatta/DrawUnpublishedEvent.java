package com.regattadesk.regatta;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.util.UUID;

public class DrawUnpublishedEvent implements DomainEvent {
    private final UUID regattaId;

    @JsonCreator
    public DrawUnpublishedEvent(@JsonProperty("regattaId") UUID regattaId) {
        this.regattaId = regattaId;
    }

    @Override
    public String getEventType() {
        return "DrawUnpublished";
    }

    @Override
    public UUID getAggregateId() {
        return regattaId;
    }

    public UUID getRegattaId() {
        return regattaId;
    }
}
