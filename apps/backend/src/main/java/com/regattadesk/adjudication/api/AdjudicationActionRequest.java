package com.regattadesk.adjudication.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdjudicationActionRequest(
    @JsonProperty("reason") String reason,
    @JsonProperty("note") String note,
    @JsonProperty("penalty_seconds") Integer penaltySeconds
) {
}
