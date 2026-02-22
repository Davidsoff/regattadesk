package com.regattadesk.finance.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.finance.EntryPaymentStatusDetails;

import java.time.Instant;
import java.util.UUID;

public record EntryPaymentStatusResponse(
    @JsonProperty("entry_id")
    UUID entryId,
    @JsonProperty("billing_club_id")
    UUID billingClubId,
    @JsonProperty("payment_status")
    String paymentStatus,
    @JsonProperty("paid_at")
    Instant paidAt,
    @JsonProperty("paid_by")
    String paidBy,
    @JsonProperty("payment_reference")
    String paymentReference
) {
    public static EntryPaymentStatusResponse from(EntryPaymentStatusDetails details) {
        return new EntryPaymentStatusResponse(
            details.entryId(),
            details.billingClubId(),
            details.paymentStatus().value(),
            details.paidAt(),
            details.paidBy(),
            details.paymentReference()
        );
    }
}
