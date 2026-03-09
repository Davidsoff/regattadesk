package com.regattadesk.export.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Response body returned when a new printable export job is created.
 */
public class ExportJobCreatedResponse {

    @JsonProperty("job_id")
    private final UUID jobId;

    public ExportJobCreatedResponse(UUID jobId) {
        this.jobId = jobId;
    }

    public UUID getJobId() {
        return jobId;
    }
}
