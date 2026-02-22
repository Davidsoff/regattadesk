package com.regattadesk.linescan.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MarkerLinkRequest(
    @JsonProperty("entry_id")
    @NotNull(message = "entry_id is required")
    UUID entryId
) {
}
