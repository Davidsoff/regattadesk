package com.regattadesk.finance.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record PaymentBulkMarkRequest(
    @JsonProperty("entry_ids")
    List<UUID> entryIds,
    @JsonProperty("club_ids")
    List<UUID> clubIds,
    @JsonProperty("payment_status")
    String paymentStatus,
    @JsonProperty("payment_reference")
    String paymentReference,
    @JsonProperty("idempotency_key")
    String idempotencyKey
) {
}
