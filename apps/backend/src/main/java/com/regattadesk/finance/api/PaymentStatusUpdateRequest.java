package com.regattadesk.finance.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentStatusUpdateRequest(
    @JsonProperty("payment_status")
    String paymentStatus,
    @JsonProperty("payment_reference")
    String paymentReference
) {
}
