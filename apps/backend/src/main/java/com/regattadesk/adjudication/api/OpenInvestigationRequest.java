package com.regattadesk.adjudication.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OpenInvestigationRequest(
    @JsonProperty("entry_id")
    @NotNull(message = "entry_id is required")
    UUID entryId,
    @JsonProperty("description")
    @NotBlank(message = "description is required")
    String description
) {
}
