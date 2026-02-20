package com.regattadesk.athlete.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AthleteListResponse(
    @JsonProperty("data")
    List<AthleteResponse> data,

    @JsonProperty("pagination")
    PaginationInfo pagination
) {
    public record PaginationInfo(
        @JsonProperty("has_more")
        boolean hasMore,

        @JsonProperty("next_cursor")
        String nextCursor
    ) {
    }
}
