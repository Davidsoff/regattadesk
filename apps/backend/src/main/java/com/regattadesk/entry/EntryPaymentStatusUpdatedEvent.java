package com.regattadesk.entry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event for entry payment status updates (BC08-001).
 * 
 * Emitted when an entry's payment status is changed. This event is auditable
 * and contains all payment metadata for compliance and reconciliation purposes.
 */
public class EntryPaymentStatusUpdatedEvent implements DomainEvent {
    private final UUID entryId;
    private final String paymentStatus;
    private final Instant paidAt;
    private final String paidBy;
    private final String paymentReference;

    @JsonCreator
    public EntryPaymentStatusUpdatedEvent(
        @JsonProperty("entryId") UUID entryId,
        @JsonProperty("paymentStatus") String paymentStatus,
        @JsonProperty("paidAt") Instant paidAt,
        @JsonProperty("paidBy") String paidBy,
        @JsonProperty("paymentReference") String paymentReference
    ) {
        this.entryId = entryId;
        this.paymentStatus = paymentStatus;
        this.paidAt = paidAt;
        this.paidBy = paidBy;
        this.paymentReference = paymentReference;
    }

    @Override
    public String getEventType() {
        return "EntryPaymentStatusUpdatedEvent";
    }

    @Override
    public UUID getAggregateId() {
        return entryId;
    }

    public UUID getEntryId() { return entryId; }
    public String getPaymentStatus() { return paymentStatus; }
    public Instant getPaidAt() { return paidAt; }
    public String getPaidBy() { return paidBy; }
    public String getPaymentReference() { return paymentReference; }
}
