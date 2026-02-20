package com.regattadesk.block;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public class BlockUpdatedEvent implements DomainEvent {
    private final UUID blockId;
    private final String name;
    private final Instant startTime;
    private final int eventIntervalSeconds;
    private final int crewIntervalSeconds;
    private final int displayOrder;

    @JsonCreator
    public BlockUpdatedEvent(
        @JsonProperty("blockId") UUID blockId,
        @JsonProperty("name") String name,
        @JsonProperty("startTime") Instant startTime,
        @JsonProperty("eventIntervalSeconds") int eventIntervalSeconds,
        @JsonProperty("crewIntervalSeconds") int crewIntervalSeconds,
        @JsonProperty("displayOrder") int displayOrder
    ) {
        this.blockId = blockId;
        this.name = name;
        this.startTime = startTime;
        this.eventIntervalSeconds = eventIntervalSeconds;
        this.crewIntervalSeconds = crewIntervalSeconds;
        this.displayOrder = displayOrder;
    }

    @Override
    public String getEventType() {
        return "BlockUpdated";
    }

    @Override
    public UUID getAggregateId() {
        return blockId;
    }

    public UUID getBlockId() { return blockId; }
    public String getName() { return name; }
    public Instant getStartTime() { return startTime; }
    public int getEventIntervalSeconds() { return eventIntervalSeconds; }
    public int getCrewIntervalSeconds() { return crewIntervalSeconds; }
    public int getDisplayOrder() { return displayOrder; }
}
