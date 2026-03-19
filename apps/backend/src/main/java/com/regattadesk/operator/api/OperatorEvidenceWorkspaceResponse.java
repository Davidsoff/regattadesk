package com.regattadesk.operator.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperatorEvidenceWorkspaceResponse(
    @JsonProperty("regatta_id")
    UUID regattaId,

    @JsonProperty("capture_session_id")
    UUID captureSessionId,

    @JsonProperty("event_id")
    UUID eventId,

    @JsonProperty("capture_session")
    CaptureSessionResponse captureSession,

    EvidenceResponse evidence,

    List<WorkspaceMarkerResponse> markers
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EvidenceResponse(
        @JsonProperty("manifest_id")
        UUID manifestId,

        @JsonProperty("capture_session_id")
        UUID captureSessionId,

        @JsonProperty("availability_state")
        String availabilityState,

        @JsonProperty("availability_reason")
        String availabilityReason,

        @JsonProperty("tile_size_px")
        Integer tileSizePx,

        @JsonProperty("primary_format")
        String primaryFormat,

        @JsonProperty("fallback_format")
        String fallbackFormat,

        @JsonProperty("x_origin_timestamp_ms")
        Long xOriginTimestampMs,

        @JsonProperty("ms_per_pixel")
        Double msPerPixel,

        @JsonProperty("span")
        EvidenceSpanResponse span,

        List<EvidenceTileResponse> tiles
    ) {}

    public record EvidenceSpanResponse(
        @JsonProperty("start_timestamp_ms")
        long startTimestampMs,

        @JsonProperty("end_timestamp_ms")
        long endTimestampMs,

        @JsonProperty("min_tile_x")
        int minTileX,

        @JsonProperty("max_tile_x")
        int maxTileX,

        @JsonProperty("min_tile_y")
        int minTileY,

        @JsonProperty("max_tile_y")
        int maxTileY,

        @JsonProperty("tile_columns")
        int tileColumns,

        @JsonProperty("tile_rows")
        int tileRows,

        @JsonProperty("pixel_width")
        int pixelWidth,

        @JsonProperty("pixel_height")
        int pixelHeight
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EvidenceTileResponse(
        @JsonProperty("tile_id")
        String tileId,

        @JsonProperty("tile_x")
        int tileX,

        @JsonProperty("tile_y")
        int tileY,

        @JsonProperty("content_type")
        String contentType,

        @JsonProperty("byte_size")
        Integer byteSize,

        @JsonProperty("upload_state")
        String uploadState,

        @JsonProperty("upload_attempts")
        Integer uploadAttempts,

        @JsonProperty("last_upload_error")
        String lastUploadError,

        @JsonProperty("last_upload_attempt_at")
        Instant lastUploadAttemptAt,

        @JsonProperty("tile_href")
        String tileHref
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WorkspaceMarkerResponse(
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
        Integer tileY,

        @JsonProperty("entry_summary")
        Map<String, Object> entrySummary
    ) {}
}
