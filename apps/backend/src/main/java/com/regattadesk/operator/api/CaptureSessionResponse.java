package com.regattadesk.operator.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.operator.CaptureSession;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single capture session.
 */
@Schema(name = "CaptureSessionResponse")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CaptureSessionResponse {

    @JsonProperty("capture_session_id")
    private final UUID captureSessionId;

    @JsonProperty("regatta_id")
    private final UUID regattaId;

    @JsonProperty("block_id")
    private final UUID blockId;

    @JsonProperty("station")
    private final String station;

    @JsonProperty("device_id")
    private final String deviceId;

    @JsonProperty("session_type")
    private final String sessionType;

    @JsonProperty("state")
    private final String state;

    @JsonProperty("server_time_at_start")
    private final Instant serverTimeAtStart;

    @JsonProperty("device_monotonic_offset_ms")
    private final Long deviceMonotonicOffsetMs;

    @JsonProperty("fps")
    private final int fps;

    @JsonProperty("is_synced")
    private final boolean isSynced;

    @JsonProperty("drift_exceeded_threshold")
    private final boolean driftExceededThreshold;

    @JsonProperty("unsynced_reason")
    private final String unsyncedReason;

    @JsonProperty("closed_at")
    private final Instant closedAt;

    @JsonProperty("close_reason")
    private final String closeReason;

    @JsonProperty("created_at")
    private final Instant createdAt;

    @JsonProperty("updated_at")
    private final Instant updatedAt;

    public CaptureSessionResponse(CaptureSession session) {
        this.captureSessionId = session.getId();
        this.regattaId = session.getRegattaId();
        this.blockId = session.getBlockId();
        this.station = session.getStation();
        this.deviceId = session.getDeviceId();
        this.sessionType = session.getSessionType().name();
        this.state = session.getState().name();
        this.serverTimeAtStart = session.getServerTimeAtStart();
        this.deviceMonotonicOffsetMs = session.getDeviceMonotonicOffsetMs();
        this.fps = session.getFps();
        this.isSynced = session.isSynced();
        this.driftExceededThreshold = session.isDriftExceededThreshold();
        this.unsyncedReason = session.getUnsyncedReason();
        this.closedAt = session.getClosedAt();
        this.closeReason = session.getCloseReason();
        this.createdAt = session.getCreatedAt();
        this.updatedAt = session.getUpdatedAt();
    }

    public UUID getCaptureSessionId() { return captureSessionId; }
    public UUID getRegattaId() { return regattaId; }
    public UUID getBlockId() { return blockId; }
    public String getStation() { return station; }
    public String getDeviceId() { return deviceId; }
    public String getSessionType() { return sessionType; }
    public String getState() { return state; }
    public Instant getServerTimeAtStart() { return serverTimeAtStart; }
    public Long getDeviceMonotonicOffsetMs() { return deviceMonotonicOffsetMs; }
    public int getFps() { return fps; }
    public boolean isSynced() { return isSynced; }
    public boolean isDriftExceededThreshold() { return driftExceededThreshold; }
    public String getUnsyncedReason() { return unsyncedReason; }
    public Instant getClosedAt() { return closedAt; }
    public String getCloseReason() { return closeReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
