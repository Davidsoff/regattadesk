package com.regattadesk.finance.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record InvoiceGenerateRequest(
    @JsonProperty("club_ids")
    @Size(min = 1)
    List<UUID> clubIds,
    @JsonProperty("idempotency_key")
    @Size(max = 128)
    String idempotencyKey
) {
}
