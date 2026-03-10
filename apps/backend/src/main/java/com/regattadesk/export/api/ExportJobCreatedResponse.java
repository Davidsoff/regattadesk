package com.regattadesk.export.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

/**
 * Response body returned when a new printable export job is created.
 */
@Schema(name = "ExportJobCreatedResponse", description = "Response returned when a new printable export job is accepted.")
public class ExportJobCreatedResponse {

    @JsonProperty("job_id")
    @Schema(required = true)
    private final UUID jobId;

    public ExportJobCreatedResponse(UUID jobId) {
        this.jobId = jobId;
    }

    public UUID getJobId() {
        return jobId;
    }
}
