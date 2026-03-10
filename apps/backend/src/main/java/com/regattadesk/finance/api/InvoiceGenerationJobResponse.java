package com.regattadesk.finance.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.finance.InvoiceGenerationJob;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "InvoiceGenerationJobResponse")
public record InvoiceGenerationJobResponse(
    @JsonProperty("job_id")
    UUID jobId,
    @Schema(enumeration = {"pending", "running", "completed", "failed"})
    String status,
    @JsonProperty("invoice_ids")
    List<UUID> invoiceIds,
    @JsonProperty("error_message")
    String errorMessage,
    @JsonProperty("created_at")
    Instant createdAt,
    @JsonProperty("completed_at")
    Instant completedAt
) {
    public static InvoiceGenerationJobResponse from(InvoiceGenerationJob job) {
        return new InvoiceGenerationJobResponse(
            job.jobId(),
            job.status().value(),
            job.invoiceIds(),
            job.errorMessage(),
            job.createdAt(),
            job.completedAt()
        );
    }
}
