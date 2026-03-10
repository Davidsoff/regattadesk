package com.regattadesk.finance.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FinanceClubListResponse(
    @JsonProperty("clubs")
    List<FinanceClubSummaryResponse> clubs
) {
}
