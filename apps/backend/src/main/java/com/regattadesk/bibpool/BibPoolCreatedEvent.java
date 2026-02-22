package com.regattadesk.bibpool;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.util.List;
import java.util.UUID;

public class BibPoolCreatedEvent implements DomainEvent {
    private final UUID poolId;
    private final UUID regattaId;
    private final UUID blockId;
    private final String name;
    private final String allocationMode;
    private final Integer startBib;
    private final Integer endBib;
    private final List<Integer> bibNumbers;
    private final int priority;
    private final boolean isOverflow;

    @JsonCreator
    public BibPoolCreatedEvent(
        @JsonProperty("poolId") UUID poolId,
        @JsonProperty("regattaId") UUID regattaId,
        @JsonProperty("blockId") UUID blockId,
        @JsonProperty("name") String name,
        @JsonProperty("allocationMode") String allocationMode,
        @JsonProperty("startBib") Integer startBib,
        @JsonProperty("endBib") Integer endBib,
        @JsonProperty("bibNumbers") List<Integer> bibNumbers,
        @JsonProperty("priority") int priority,
        @JsonProperty("isOverflow") boolean isOverflow
    ) {
        this.poolId = poolId;
        this.regattaId = regattaId;
        this.blockId = blockId;
        this.name = name;
        this.allocationMode = allocationMode;
        this.startBib = startBib;
        this.endBib = endBib;
        this.bibNumbers = bibNumbers;
        this.priority = priority;
        this.isOverflow = isOverflow;
    }

    @Override
    public String getEventType() {
        return "BibPoolCreated";
    }

    @Override
    public UUID getAggregateId() {
        return poolId;
    }

    public UUID getPoolId() { return poolId; }
    public UUID getRegattaId() { return regattaId; }
    public UUID getBlockId() { return blockId; }
    public String getName() { return name; }
    public String getAllocationMode() { return allocationMode; }
    public Integer getStartBib() { return startBib; }
    public Integer getEndBib() { return endBib; }
    public List<Integer> getBibNumbers() { return bibNumbers; }
    public int getPriority() { return priority; }
    public boolean isOverflow() { return isOverflow; }
}
