package com.regattadesk.adjudication.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record OpenInvestigationRequest(
    @JsonProperty("entry_id") UUID entryId,
    @JsonProperty("description") String description
) {
}
