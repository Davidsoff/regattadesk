package com.regattadesk.finance.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.finance.model.BulkPaymentMarkResult;

import java.util.List;

public record PaymentBulkMarkResponse(
    boolean success,
    String message,
    @JsonProperty("total_requested")
    int totalRequested,
    @JsonProperty("processed_count")
    int processedCount,
    @JsonProperty("updated_count")
    int updatedCount,
    @JsonProperty("unchanged_count")
    int unchangedCount,
    @JsonProperty("failed_count")
    int failedCount,
    List<BulkPaymentFailureResponse> failures,
    @JsonProperty("idempotency_key")
    String idempotencyKey,
    @JsonProperty("idempotent_replay")
    boolean idempotentReplay
) {
    public static PaymentBulkMarkResponse from(BulkPaymentMarkResult result) {
        return new PaymentBulkMarkResponse(
            result.success(),
            result.message(),
            result.totalRequested(),
            result.processedCount(),
            result.updatedCount(),
            result.unchangedCount(),
            result.failedCount(),
            result.failures().stream().map(BulkPaymentFailureResponse::from).toList(),
            result.idempotencyKey(),
            result.idempotentReplay()
        );
    }
}
