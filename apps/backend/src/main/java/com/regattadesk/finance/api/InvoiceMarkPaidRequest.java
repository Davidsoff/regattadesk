package com.regattadesk.finance.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record InvoiceMarkPaidRequest(
    @JsonProperty("paid_by")
    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = ".*\\S.*")
    String paidBy,
    @JsonProperty("paid_at")
    Instant paidAt,
    @JsonProperty("payment_reference")
    String paymentReference
) {
}
