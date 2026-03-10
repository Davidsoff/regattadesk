package com.regattadesk.finance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceGenerationJob(
    UUID jobId,
    UUID regattaId,
    InvoiceGenerationJobStatus status,
    List<UUID> invoiceIds,
    String errorMessage,
    Instant createdAt,
    Instant completedAt
) {
}
