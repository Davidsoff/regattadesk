package com.regattadesk.linescan.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;

public record MarkerUpdateRequest(
    @JsonProperty("frame_offset")
    @Min(value = 0, message = "frame_offset must be >= 0")
    Long frameOffset,

    @JsonProperty("timestamp_ms")
    @Min(value = 0, message = "timestamp_ms must be >= 0")
    Long timestampMs,

    @JsonProperty("tile_id")
    String tileId,

    @JsonProperty("tile_x")
    Integer tileX,

    @JsonProperty("tile_y")
    Integer tileY,

    @JsonProperty("is_approved")
    Boolean isApproved
) {
}
