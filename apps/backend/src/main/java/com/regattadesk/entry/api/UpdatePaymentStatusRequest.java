package com.regattadesk.entry.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

/**
 * Request DTO for updating entry payment status (BC08-001).
 */
public record UpdatePaymentStatusRequest(
    @JsonProperty("payment_status")
    @NotBlank(message = "Payment status is required")
    @Pattern(regexp = "paid|unpaid", message = "Payment status must be 'paid' or 'unpaid'")
    String paymentStatus,
    
    @JsonProperty("paid_at")
    Instant paidAt,
    
    @JsonProperty("paid_by")
    String paidBy,
    
    @JsonProperty("payment_reference")
    String paymentReference
) {
}
