package com.regattadesk.finance.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FinanceListPaginationResponse(
    @JsonProperty("has_more")
    boolean hasMore,

    @JsonProperty("next_cursor")
    String nextCursor
) {
}
