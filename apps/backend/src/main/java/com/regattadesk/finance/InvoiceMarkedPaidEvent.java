package com.regattadesk.finance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record InvoiceMarkedPaidEvent(
    @JsonProperty("invoice_id")
    UUID invoiceId,
    @JsonProperty("regatta_id")
    UUID regattaId,
    @JsonProperty("club_id")
    UUID clubId,
    @JsonProperty("paid_at")
    Instant paidAt,
    @JsonProperty("paid_by")
    String paidBy,
    @JsonProperty("payment_reference")
    String paymentReference
) implements DomainEvent {
    @Override
    public String getEventType() {
        return "InvoiceMarkedPaid";
    }

    @Override
    public UUID getAggregateId() {
        return invoiceId;
    }
}
