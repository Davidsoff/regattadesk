package com.regattadesk.finance;

import java.util.List;

public record BulkPaymentMarkResult(
    boolean success,
    String message,
    int totalRequested,
    int processedCount,
    int updatedCount,
    int unchangedCount,
    int failedCount,
    List<BulkPaymentFailure> failures,
    String idempotencyKey,
    boolean idempotentReplay
) {
}
