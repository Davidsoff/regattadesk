package com.regattadesk.regatta;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.util.UUID;

public class DrawPublishedEvent implements DomainEvent {
    private final UUID regattaId;
    private final long drawSeed;
    private final int drawRevision;

    @JsonCreator
    public DrawPublishedEvent(
        @JsonProperty("regattaId") UUID regattaId,
        @JsonProperty("drawSeed") long drawSeed,
        @JsonProperty("drawRevision") int drawRevision
    ) {
        this.regattaId = regattaId;
        this.drawSeed = drawSeed;
        this.drawRevision = drawRevision;
    }

    @Override
    public String getEventType() {
        return "DrawPublished";
    }

    @Override
    public UUID getAggregateId() {
        return regattaId;
    }

    public UUID getRegattaId() { return regattaId; }
    public long getDrawSeed() { return drawSeed; }
    public int getDrawRevision() { return drawRevision; }
}
