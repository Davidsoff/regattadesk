package com.regattadesk.operator;

import com.regattadesk.linescan.model.LineScanManifest;
import com.regattadesk.linescan.model.LineScanTileMetadata;
import com.regattadesk.linescan.repository.LineScanTileRepository;
import com.regattadesk.linescan.service.LineScanManifestService;
import com.regattadesk.operator.api.CaptureSessionResponse;
import com.regattadesk.operator.api.OperatorEvidenceWorkspaceResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class OperatorEvidenceWorkspaceService {

    private final CaptureSessionService captureSessionService;
    private final LineScanManifestService lineScanManifestService;
    private final LineScanTileRepository lineScanTileRepository;
    private final DataSource dataSource;

    @Inject
    public OperatorEvidenceWorkspaceService(
        CaptureSessionService captureSessionService,
        LineScanManifestService lineScanManifestService,
        LineScanTileRepository lineScanTileRepository,
        DataSource dataSource
    ) {
        this.captureSessionService = captureSessionService;
        this.lineScanManifestService = lineScanManifestService;
        this.lineScanTileRepository = lineScanTileRepository;
        this.dataSource = dataSource;
    }

    public OperatorEvidenceWorkspaceResponse getWorkspace(UUID regattaId, UUID captureSessionId, UUID eventId) {
        if (captureSessionId == null && eventId == null) {
            throw new BadRequestException("Either capture_session_id or event_id is required");
        }

        if (eventId != null && !eventBelongsToRegatta(eventId, regattaId)) {
            throw new NotFoundException("Event not found in this regatta");
        }

        CaptureSession captureSession = resolveCaptureSession(regattaId, captureSessionId, eventId);
        Optional<LineScanManifest> manifest = lineScanManifestService.getManifestByCaptureSession(captureSession.getId());
        List<LineScanTileMetadata> tiles = manifest
            .map(saved -> lineScanTileRepository.findByManifestId(saved.getId()))
            .orElseGet(List::of);

        return new OperatorEvidenceWorkspaceResponse(
            regattaId,
            captureSession.getId(),
            eventId,
            new CaptureSessionResponse(captureSession),
            buildEvidence(manifest, tiles, regattaId, captureSession.getId()),
            loadMarkers(regattaId, captureSession.getId(), eventId)
        );
    }

    private CaptureSession resolveCaptureSession(UUID regattaId, UUID captureSessionId, UUID eventId) {
        UUID resolvedCaptureSessionId = captureSessionId != null
            ? captureSessionId
            : resolveCaptureSessionIdForEvent(regattaId, eventId);

        return captureSessionService.getSession(resolvedCaptureSessionId, regattaId)
            .orElseThrow(() -> new NotFoundException("Capture session not found in this regatta"));
    }

    private UUID resolveCaptureSessionIdForEvent(UUID regattaId, UUID eventId) {
        UUID blockId = findBlockIdForEvent(regattaId, eventId)
            .orElseThrow(() -> new NotFoundException("No capture session found for the requested event"));

        return captureSessionService.listSessionsByBlock(regattaId, blockId).stream()
            .filter(session -> "line-scan".equals(session.getStation()))
            .max(Comparator.comparing(CaptureSession::getUpdatedAt).thenComparing(CaptureSession::getCreatedAt))
            .map(CaptureSession::getId)
            .orElseThrow(() -> new NotFoundException("No capture session found for the requested event"));
    }

    private Optional<UUID> findBlockIdForEvent(UUID regattaId, UUID eventId) {
        String sql = """
            SELECT block_id
            FROM entries
            WHERE event_id = ? AND regatta_id = ?
            ORDER BY created_at ASC
            LIMIT 1
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, eventId);
            statement.setObject(2, regattaId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(resultSet.getObject("block_id", UUID.class));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to resolve event block", e);
        }
    }

    private boolean eventBelongsToRegatta(UUID eventId, UUID regattaId) {
        String sql = """
            SELECT 1
            FROM events
            WHERE id = ? AND regatta_id = ?
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, eventId);
            statement.setObject(2, regattaId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to resolve event scope", e);
        }
    }

    private OperatorEvidenceWorkspaceResponse.EvidenceResponse buildEvidence(
        Optional<LineScanManifest> manifest,
        List<LineScanTileMetadata> tiles,
        UUID regattaId,
        UUID captureSessionId
    ) {
        if (manifest.isEmpty()) {
            return new OperatorEvidenceWorkspaceResponse.EvidenceResponse(
                null,
                captureSessionId,
                "unavailable",
                "manifest_missing",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
            );
        }

        LineScanManifest savedManifest = manifest.get();
        String availabilityState = "ready";
        String availabilityReason = null;
        if (tiles.isEmpty()) {
            availabilityState = "unavailable";
            availabilityReason = "manifest_has_no_tiles";
        } else if (tiles.stream().anyMatch(tile -> tile.getUploadState() == LineScanTileMetadata.UploadState.FAILED)) {
            availabilityState = "degraded";
            availabilityReason = "tile_upload_failed";
        } else if (tiles.stream().anyMatch(tile -> tile.getUploadState() == LineScanTileMetadata.UploadState.PENDING)) {
            availabilityState = "degraded";
            availabilityReason = "tile_upload_pending";
        }

        OperatorEvidenceWorkspaceResponse.EvidenceSpanResponse span = buildSpan(savedManifest, tiles);
        List<OperatorEvidenceWorkspaceResponse.EvidenceTileResponse> evidenceTiles = tiles.stream()
            .map(tile -> new OperatorEvidenceWorkspaceResponse.EvidenceTileResponse(
                tile.getTileId(),
                tile.getTileX(),
                tile.getTileY(),
                tile.getContentType(),
                tile.getByteSize(),
                tile.getUploadState().getValue(),
                tile.getUploadAttempts(),
                tile.getLastUploadError(),
                tile.getLastUploadAttemptAt(),
                "/api/v1/regattas/" + regattaId + "/line_scan/tiles/" + tile.getTileId()
            ))
            .toList();

        return new OperatorEvidenceWorkspaceResponse.EvidenceResponse(
            savedManifest.getId(),
            captureSessionId,
            availabilityState,
            availabilityReason,
            savedManifest.getTileSizePx(),
            savedManifest.getPrimaryFormat(),
            savedManifest.getFallbackFormat(),
            savedManifest.getXOriginTimestampMs(),
            savedManifest.getMsPerPixel(),
            span,
            evidenceTiles
        );
    }

    private OperatorEvidenceWorkspaceResponse.EvidenceSpanResponse buildSpan(
        LineScanManifest manifest,
        List<LineScanTileMetadata> tiles
    ) {
        int minTileX = tiles.stream().mapToInt(LineScanTileMetadata::getTileX).min().orElse(0);
        int maxTileX = tiles.stream().mapToInt(LineScanTileMetadata::getTileX).max().orElse(0);
        int minTileY = tiles.stream().mapToInt(LineScanTileMetadata::getTileY).min().orElse(0);
        int maxTileY = tiles.stream().mapToInt(LineScanTileMetadata::getTileY).max().orElse(0);
        int tileColumns = maxTileX - minTileX + 1;
        int tileRows = maxTileY - minTileY + 1;
        int pixelWidth = tileColumns * manifest.getTileSizePx();
        int pixelHeight = tileRows * manifest.getTileSizePx();

        long startTimestampMs = manifest.getXOriginTimestampMs() + Math.round((double) minTileX * manifest.getTileSizePx() * manifest.getMsPerPixel());
        long endTimestampMs = manifest.getXOriginTimestampMs() + Math.round((double) (maxTileX + 1) * manifest.getTileSizePx() * manifest.getMsPerPixel());

        return new OperatorEvidenceWorkspaceResponse.EvidenceSpanResponse(
            startTimestampMs,
            endTimestampMs,
            minTileX,
            maxTileX,
            minTileY,
            maxTileY,
            tileColumns,
            tileRows,
            pixelWidth,
            pixelHeight
        );
    }

    private List<OperatorEvidenceWorkspaceResponse.WorkspaceMarkerResponse> loadMarkers(
        UUID regattaId,
        UUID captureSessionId,
        UUID eventId
    ) {
        String sql = """
            SELECT tm.id, tm.capture_session_id, tm.entry_id, tm.frame_offset, tm.timestamp_ms,
                   tm.is_linked, tm.is_approved, tm.tile_id, tm.tile_x, tm.tile_y,
                   e.event_id, e.completion_status
            FROM timing_markers tm
            JOIN capture_sessions cs ON cs.id = tm.capture_session_id
            LEFT JOIN entries e ON e.id = tm.entry_id
            WHERE cs.regatta_id = ?
              AND tm.capture_session_id = ?
            """ + (eventId != null ? " AND (tm.entry_id IS NULL OR e.event_id = ?)" : "") + """
            ORDER BY tm.timestamp_ms ASC, tm.id ASC
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, regattaId);
            statement.setObject(2, captureSessionId);
            if (eventId != null) {
                statement.setObject(3, eventId);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                List<OperatorEvidenceWorkspaceResponse.WorkspaceMarkerResponse> markers = new ArrayList<>();
                while (resultSet.next()) {
                    Map<String, Object> entrySummary = null;
                    UUID entryId = resultSet.getObject("entry_id", UUID.class);
                    if (entryId != null) {
                        entrySummary = Map.of(
                            "entry_id", entryId,
                            "event_id", resultSet.getObject("event_id", UUID.class),
                            "completion_status", resultSet.getString("completion_status")
                        );
                    }

                    markers.add(new OperatorEvidenceWorkspaceResponse.WorkspaceMarkerResponse(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("capture_session_id", UUID.class),
                        entryId,
                        resultSet.getLong("frame_offset"),
                        resultSet.getLong("timestamp_ms"),
                        resultSet.getBoolean("is_linked"),
                        resultSet.getBoolean("is_approved"),
                        resultSet.getString("tile_id"),
                        resultSet.getObject("tile_x", Integer.class),
                        resultSet.getObject("tile_y", Integer.class),
                        entrySummary
                    ));
                }
                return markers;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load evidence workspace markers", e);
        }
    }

    public static final class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    public static final class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}
