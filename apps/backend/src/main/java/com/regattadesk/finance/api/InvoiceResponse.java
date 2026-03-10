package com.regattadesk.finance.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.finance.InvoiceRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
    UUID id,
    @JsonProperty("regatta_id")
    UUID regattaId,
    @JsonProperty("club_id")
    UUID clubId,
    @JsonProperty("invoice_number")
    String invoiceNumber,
    List<InvoiceEntryLineResponse> entries,
    @JsonProperty("total_amount")
    BigDecimal totalAmount,
    String currency,
    String status,
    @JsonProperty("generated_at")
    Instant generatedAt,
    @JsonProperty("sent_at")
    Instant sentAt,
    @JsonProperty("paid_at")
    Instant paidAt,
    @JsonProperty("paid_by")
    String paidBy,
    @JsonProperty("payment_reference")
    String paymentReference
) {
    public static InvoiceResponse from(InvoiceRecord invoice) {
        return new InvoiceResponse(
            invoice.id(),
            invoice.regattaId(),
            invoice.clubId(),
            invoice.invoiceNumber(),
            invoice.entries().stream().map(InvoiceEntryLineResponse::from).toList(),
            invoice.totalAmount(),
            invoice.currency(),
            invoice.status().value(),
            invoice.generatedAt(),
            invoice.sentAt(),
            invoice.paidAt(),
            invoice.paidBy(),
            invoice.paymentReference()
        );
    }
}
