package com.regattadesk.operator.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for updating the sync state of a capture session.
 *
 * <p>The {@code observed_at} field is request metadata and is not persisted
 * in the capture_sessions table in v0.1.
 */
public class CaptureSessionSyncStateRequest {

    @JsonProperty("is_synced")
    private boolean isSynced;

    @JsonProperty("drift_exceeded_threshold")
    private boolean driftExceededThreshold;

    @JsonProperty("unsynced_reason")
    private String unsyncedReason;

    @JsonProperty("observed_at")
    private String observedAt;

    public CaptureSessionSyncStateRequest() {
    }

    public boolean isSynced() { return isSynced; }
    public void setSynced(boolean synced) { this.isSynced = synced; }

    public boolean isDriftExceededThreshold() { return driftExceededThreshold; }
    public void setDriftExceededThreshold(boolean driftExceededThreshold) {
        this.driftExceededThreshold = driftExceededThreshold;
    }

    public String getUnsyncedReason() { return unsyncedReason; }
    public void setUnsyncedReason(String unsyncedReason) { this.unsyncedReason = unsyncedReason; }

    public String getObservedAt() { return observedAt; }
    public void setObservedAt(String observedAt) { this.observedAt = observedAt; }
}
