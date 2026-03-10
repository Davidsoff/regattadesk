package com.regattadesk.regatta;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.util.UUID;

public class DrawGeneratedEvent implements DomainEvent {
    private final UUID regattaId;
    private final long drawSeed;

    @JsonCreator
    public DrawGeneratedEvent(
        @JsonProperty("regattaId") UUID regattaId,
        @JsonProperty("drawSeed") long drawSeed
    ) {
        this.regattaId = regattaId;
        this.drawSeed = drawSeed;
    }

    @Override
    public String getEventType() {
        return "DrawGenerated";
    }

    @Override
    public UUID getAggregateId() {
        return regattaId;
    }

    public UUID getRegattaId() {
        return regattaId;
    }

    public long getDrawSeed() {
        return drawSeed;
    }
}
