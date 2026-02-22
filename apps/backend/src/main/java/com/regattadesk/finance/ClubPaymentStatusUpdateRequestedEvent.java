package com.regattadesk.finance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.util.UUID;

public class ClubPaymentStatusUpdateRequestedEvent implements DomainEvent {
    private final UUID clubId;
    private final UUID regattaId;
    private final String targetStatus;
    private final int changedEntriesCount;
    private final String requestedBy;
    private final String paymentReference;

    @JsonCreator
    public ClubPaymentStatusUpdateRequestedEvent(
        @JsonProperty("clubId") UUID clubId,
        @JsonProperty("regattaId") UUID regattaId,
        @JsonProperty("targetStatus") String targetStatus,
        @JsonProperty("changedEntriesCount") int changedEntriesCount,
        @JsonProperty("requestedBy") String requestedBy,
        @JsonProperty("paymentReference") String paymentReference
    ) {
        this.clubId = clubId;
        this.regattaId = regattaId;
        this.targetStatus = targetStatus;
        this.changedEntriesCount = changedEntriesCount;
        this.requestedBy = requestedBy;
        this.paymentReference = paymentReference;
    }

    @Override
    public String getEventType() {
        return "ClubPaymentStatusUpdateRequested";
    }

    @Override
    public UUID getAggregateId() {
        return clubId;
    }

    public UUID getClubId() {
        return clubId;
    }

    public UUID getRegattaId() {
        return regattaId;
    }

    public String getTargetStatus() {
        return targetStatus;
    }

    public int getChangedEntriesCount() {
        return changedEntriesCount;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getPaymentReference() {
        return paymentReference;
    }
}
