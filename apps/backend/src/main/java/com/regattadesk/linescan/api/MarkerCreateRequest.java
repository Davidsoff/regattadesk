package com.regattadesk.linescan.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MarkerCreateRequest(
    @JsonProperty("capture_session_id")
    @NotNull(message = "capture_session_id is required")
    UUID captureSessionId,

    @JsonProperty("frame_offset")
    @NotNull(message = "frame_offset is required")
    @Min(value = 0, message = "frame_offset must be >= 0")
    Long frameOffset,

    @JsonProperty("timestamp_ms")
    @NotNull(message = "timestamp_ms is required")
    @Min(value = 0, message = "timestamp_ms must be >= 0")
    Long timestampMs,

    @JsonProperty("tile_id")
    String tileId,

    @JsonProperty("tile_x")
    Integer tileX,

    @JsonProperty("tile_y")
    Integer tileY
) {
}
