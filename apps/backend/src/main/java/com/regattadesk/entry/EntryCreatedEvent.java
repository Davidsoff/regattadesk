package com.regattadesk.entry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.util.UUID;

/**
 * Domain event for entry creation (BC03-004).
 * 
 * Emitted when a new entry is registered for an event.
 */
public class EntryCreatedEvent implements DomainEvent {
    private final UUID entryId;
    private final UUID regattaId;
    private final UUID eventId;
    private final UUID blockId;
    private final UUID crewId;
    private final UUID billingClubId;

    @JsonCreator
    public EntryCreatedEvent(
        @JsonProperty("entryId") UUID entryId,
        @JsonProperty("regattaId") UUID regattaId,
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("blockId") UUID blockId,
        @JsonProperty("crewId") UUID crewId,
        @JsonProperty("billingClubId") UUID billingClubId
    ) {
        this.entryId = entryId;
        this.regattaId = regattaId;
        this.eventId = eventId;
        this.blockId = blockId;
        this.crewId = crewId;
        this.billingClubId = billingClubId;
    }

    @Override
    public String getEventType() {
        return "EntryCreated";
    }

    @Override
    public UUID getAggregateId() {
        return entryId;
    }

    public UUID getEntryId() { return entryId; }
    public UUID getRegattaId() { return regattaId; }
    public UUID getEventId() { return eventId; }
    public UUID getBlockId() { return blockId; }
    public UUID getCrewId() { return crewId; }
    public UUID getBillingClubId() { return billingClubId; }
}
