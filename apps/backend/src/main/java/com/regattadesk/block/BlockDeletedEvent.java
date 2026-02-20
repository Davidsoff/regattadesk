package com.regattadesk.block;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.util.UUID;

public class BlockDeletedEvent implements DomainEvent {
    private final UUID blockId;

    @JsonCreator
    public BlockDeletedEvent(@JsonProperty("blockId") UUID blockId) {
        this.blockId = blockId;
    }

    @Override
    public String getEventType() {
        return "BlockDeleted";
    }

    @Override
    public UUID getAggregateId() {
        return blockId;
    }

    public UUID getBlockId() { return blockId; }
}
