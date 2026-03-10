package com.regattadesk.adjudication.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AdjudicationActionRequest(
    @JsonProperty("reason")
    @NotBlank(message = "reason is required")
    String reason,
    @JsonProperty("note") String note,
    @JsonProperty("penalty_seconds")
    @Min(value = 1, message = "penalty_seconds must be at least 1")
    Integer penaltySeconds
) {
}
