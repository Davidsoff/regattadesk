package com.regattadesk.finance.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.finance.ClubPaymentStatusDetails;

import java.util.UUID;

public record ClubPaymentStatusResponse(
    @JsonProperty("regatta_id")
    UUID regattaId,
    @JsonProperty("club_id")
    UUID clubId,
    @JsonProperty("payment_status")
    String paymentStatus,
    @JsonProperty("billable_entry_count")
    int billableEntryCount,
    @JsonProperty("paid_entry_count")
    int paidEntryCount
) {
    public static ClubPaymentStatusResponse from(ClubPaymentStatusDetails details) {
        return new ClubPaymentStatusResponse(
            details.regattaId(),
            details.clubId(),
            details.paymentStatus().value(),
            details.billableEntryCount(),
            details.paidEntryCount()
        );
    }
}
