package com.regattadesk.finance.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FinanceEntryListResponse(
    @JsonProperty("entries")
    List<FinanceEntrySummaryResponse> entries,

    @JsonProperty("pagination")
    FinanceListPaginationResponse pagination
) {
}
