package com.regattadesk.linescan.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.linescan.model.TimingMarker;

import java.util.UUID;

public record MarkerResponse(
    UUID id,

    @JsonProperty("capture_session_id")
    UUID captureSessionId,

    @JsonProperty("entry_id")
    UUID entryId,

    @JsonProperty("frame_offset")
    long frameOffset,

    @JsonProperty("timestamp_ms")
    long timestampMs,

    @JsonProperty("is_linked")
    boolean isLinked,

    @JsonProperty("is_approved")
    boolean isApproved,

    @JsonProperty("tile_id")
    String tileId,

    @JsonProperty("tile_x")
    Integer tileX,

    @JsonProperty("tile_y")
    Integer tileY
) {
    public static MarkerResponse from(TimingMarker marker) {
        return new MarkerResponse(
            marker.id(),
            marker.captureSessionId(),
            marker.entryId(),
            marker.frameOffset(),
            marker.timestampMs(),
            marker.isLinked(),
            marker.isApproved(),
            marker.tileId(),
            marker.tileX(),
            marker.tileY()
        );
    }
}
