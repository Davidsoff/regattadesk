package com.regattadesk.finance.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.finance.FinanceClubSummary;

import java.util.UUID;

public record FinanceClubSummaryResponse(
    @JsonProperty("club_id")
    UUID clubId,
    @JsonProperty("club_name")
    String clubName,
    @JsonProperty("payment_status")
    String paymentStatus,
    @JsonProperty("paid_entries")
    int paidEntries,
    @JsonProperty("unpaid_entries")
    int unpaidEntries
) {
    public static FinanceClubSummaryResponse from(FinanceClubSummary summary) {
        return new FinanceClubSummaryResponse(
            summary.clubId(),
            summary.clubName(),
            summary.paymentStatus(),
            summary.paidEntries(),
            summary.unpaidEntries()
        );
    }
}
