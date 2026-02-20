package com.regattadesk.bibpool;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.util.UUID;

public class BibPoolDeletedEvent implements DomainEvent {
    private final UUID poolId;

    @JsonCreator
    public BibPoolDeletedEvent(@JsonProperty("poolId") UUID poolId) {
        this.poolId = poolId;
    }

    @Override
    public String getEventType() {
        return "BibPoolDeleted";
    }

    @Override
    public UUID getAggregateId() {
        return poolId;
    }

    public UUID getPoolId() { return poolId; }
}
