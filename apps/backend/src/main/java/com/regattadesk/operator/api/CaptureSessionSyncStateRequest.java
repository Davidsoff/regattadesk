package com.regattadesk.operator.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Request DTO for updating the sync state of a capture session.
 *
 * <p>The {@code observed_at} field is request metadata and is not persisted
 * in the capture_sessions table in v0.1.
 */
public class CaptureSessionSyncStateRequest {

    @JsonProperty("is_synced")
    @NotNull(message = "is_synced is required")
    private Boolean isSynced;

    @JsonProperty("drift_exceeded_threshold")
    @NotNull(message = "drift_exceeded_threshold is required")
    private Boolean driftExceededThreshold;

    @JsonProperty("unsynced_reason")
    private String unsyncedReason;

    @JsonProperty("observed_at")
    private Instant observedAt;

    public CaptureSessionSyncStateRequest() {
    }

    public Boolean getIsSynced() { return isSynced; }
    public void setIsSynced(Boolean synced) { this.isSynced = synced; }

    public Boolean isDriftExceededThreshold() { return driftExceededThreshold; }
    public void setDriftExceededThreshold(Boolean driftExceededThreshold) {
        this.driftExceededThreshold = driftExceededThreshold;
    }

    public String getUnsyncedReason() { return unsyncedReason; }
    public void setUnsyncedReason(String unsyncedReason) { this.unsyncedReason = unsyncedReason; }

    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
}
