package com.regattadesk.regatta;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.util.UUID;

public class EntryAddedEvent implements DomainEvent {
    private final UUID regattaId;
    private final UUID entryId;
    private final UUID eventId;
    private final UUID blockId;
    private final UUID crewId;
    private final UUID billingClubId;

    @JsonCreator
    public EntryAddedEvent(
        @JsonProperty("regattaId") UUID regattaId,
        @JsonProperty("entryId") UUID entryId,
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("blockId") UUID blockId,
        @JsonProperty("crewId") UUID crewId,
        @JsonProperty("billingClubId") UUID billingClubId
    ) {
        this.regattaId = regattaId;
        this.entryId = entryId;
        this.eventId = eventId;
        this.blockId = blockId;
        this.crewId = crewId;
        this.billingClubId = billingClubId;
    }

    @Override
    public String getEventType() {
        return "EntryAdded";
    }

    @Override
    public UUID getAggregateId() {
        return regattaId;
    }

    public UUID getRegattaId() { return regattaId; }
    public UUID getEntryId() { return entryId; }
    public UUID getEventId() { return eventId; }
    public UUID getBlockId() { return blockId; }
    public UUID getCrewId() { return crewId; }
    public UUID getBillingClubId() { return billingClubId; }
}
