package com.regattadesk.linescan.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MarkerUpdateRequest(
    @JsonProperty("frame_offset")
    Long frameOffset,

    @JsonProperty("timestamp_ms")
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
