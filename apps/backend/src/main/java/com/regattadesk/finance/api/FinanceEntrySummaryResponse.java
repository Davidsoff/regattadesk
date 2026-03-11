package com.regattadesk.finance.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.finance.FinanceEntrySummary;

import java.util.UUID;

public record FinanceEntrySummaryResponse(
    @JsonProperty("entry_id")
    UUID entryId,
    @JsonProperty("crew_name")
    String crewName,
    @JsonProperty("club_name")
    String clubName,
    @JsonProperty("payment_status")
    String paymentStatus
) {
    public static FinanceEntrySummaryResponse from(FinanceEntrySummary summary) {
        return new FinanceEntrySummaryResponse(
            summary.entryId(),
            summary.crewName(),
            summary.clubName(),
            summary.paymentStatus()
        );
    }
}
