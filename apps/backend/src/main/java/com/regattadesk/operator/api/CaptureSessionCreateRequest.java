package com.regattadesk.operator.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/**
 * Request DTO for starting a new capture session.
 */
public class CaptureSessionCreateRequest {

    @NotNull(message = "block_id is required")
    @JsonProperty("block_id")
    private UUID blockId;

    @NotBlank(message = "station is required")
    @JsonProperty("station")
    private String station;

    @NotBlank(message = "device_id is required")
    @JsonProperty("device_id")
    private String deviceId;

    @NotNull(message = "session_type is required")
    @Pattern(regexp = "start|finish", message = "session_type must be 'start' or 'finish'")
    @JsonProperty("session_type")
    private String sessionType;

    @JsonProperty("server_time_at_start")
    private Instant serverTimeAtStart;

    @JsonProperty("device_monotonic_offset_ms")
    private Long deviceMonotonicOffsetMs;

    @NotNull(message = "fps is required")
    @Min(value = 1, message = "fps must be at least 1")
    @JsonProperty("fps")
    private Integer fps;

    public CaptureSessionCreateRequest() {
    }

    public UUID getBlockId() { return blockId; }
    public void setBlockId(UUID blockId) { this.blockId = blockId; }

    public String getStation() { return station; }
    public void setStation(String station) { this.station = station; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getSessionType() { return sessionType; }
    public void setSessionType(String sessionType) { this.sessionType = sessionType; }

    public Instant getServerTimeAtStart() { return serverTimeAtStart; }
    public void setServerTimeAtStart(Instant serverTimeAtStart) { this.serverTimeAtStart = serverTimeAtStart; }

    public Long getDeviceMonotonicOffsetMs() { return deviceMonotonicOffsetMs; }
    public void setDeviceMonotonicOffsetMs(Long deviceMonotonicOffsetMs) { this.deviceMonotonicOffsetMs = deviceMonotonicOffsetMs; }

    public Integer getFps() { return fps; }
    public void setFps(Integer fps) { this.fps = fps; }
}
