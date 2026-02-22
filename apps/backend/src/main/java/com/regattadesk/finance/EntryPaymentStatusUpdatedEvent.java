package com.regattadesk.finance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public class EntryPaymentStatusUpdatedEvent implements DomainEvent {
    private final UUID entryId;
    private final UUID regattaId;
    private final UUID clubId;
    private final String previousStatus;
    private final String newStatus;
    private final Instant paidAt;
    private final String paidBy;
    private final String paymentReference;
    private final String reason;

    @JsonCreator
    public EntryPaymentStatusUpdatedEvent(
        @JsonProperty("entryId") UUID entryId,
        @JsonProperty("regattaId") UUID regattaId,
        @JsonProperty("clubId") UUID clubId,
        @JsonProperty("previousStatus") String previousStatus,
        @JsonProperty("newStatus") String newStatus,
        @JsonProperty("paidAt") Instant paidAt,
        @JsonProperty("paidBy") String paidBy,
        @JsonProperty("paymentReference") String paymentReference,
        @JsonProperty("reason") String reason
    ) {
        this.entryId = entryId;
        this.regattaId = regattaId;
        this.clubId = clubId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.paidAt = paidAt;
        this.paidBy = paidBy;
        this.paymentReference = paymentReference;
        this.reason = reason;
    }

    @Override
    public String getEventType() {
        return "EntryPaymentStatusUpdated";
    }

    @Override
    public UUID getAggregateId() {
        return entryId;
    }

    public UUID getEntryId() {
        return entryId;
    }

    public UUID getRegattaId() {
        return regattaId;
    }

    public UUID getClubId() {
        return clubId;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public String getPaidBy() {
        return paidBy;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public String getReason() {
        return reason;
    }
}
