package com.regattadesk.finance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceGeneratedEvent(
    @JsonProperty("invoice_id")
    UUID invoiceId,
    @JsonProperty("regatta_id")
    UUID regattaId,
    @JsonProperty("club_id")
    UUID clubId,
    @JsonProperty("invoice_number")
    String invoiceNumber,
    @JsonProperty("entry_ids")
    List<UUID> entryIds,
    @JsonProperty("total_amount")
    BigDecimal totalAmount,
    @JsonProperty("currency")
    String currency,
    @JsonProperty("generated_at")
    Instant generatedAt
) implements DomainEvent {
    @Override
    public String getEventType() {
        return "InvoiceGenerated";
    }

    @Override
    public UUID getAggregateId() {
        return invoiceId;
    }
}
