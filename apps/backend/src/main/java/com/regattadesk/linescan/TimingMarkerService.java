package com.regattadesk.linescan;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for marker CRUD/linking and marker-derived entry completion updates.
 */
@ApplicationScoped
public class TimingMarkerService {

    @Inject
    DataSource dataSource;

    public List<TimingMarker> listByRegatta(UUID regattaId, UUID captureSessionId) {
        String sql = """
            SELECT tm.id, tm.capture_session_id, tm.entry_id, tm.frame_offset, tm.timestamp_ms,
                   tm.is_linked, tm.is_approved, tm.tile_id, tm.tile_x, tm.tile_y
            FROM timing_markers tm
            JOIN capture_sessions cs ON cs.id = tm.capture_session_id
            WHERE cs.regatta_id = ?
            """ + (captureSessionId != null ? " AND tm.capture_session_id = ?" : "") +
            " ORDER BY tm.timestamp_ms ASC, tm.id ASC";

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            if (captureSessionId != null) {
                stmt.setObject(2, captureSessionId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                List<TimingMarker> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapMarker(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list markers", e);
        }
    }

    public Optional<TimingMarker> findById(UUID regattaId, UUID markerId) {
        String sql = """
            SELECT tm.id, tm.capture_session_id, tm.entry_id, tm.frame_offset, tm.timestamp_ms,
                   tm.is_linked, tm.is_approved, tm.tile_id, tm.tile_x, tm.tile_y
            FROM timing_markers tm
            JOIN capture_sessions cs ON cs.id = tm.capture_session_id
            WHERE cs.regatta_id = ? AND tm.id = ?
            """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            stmt.setObject(2, markerId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapMarker(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get marker", e);
        }
    }

    @Transactional
    public TimingMarker create(
        UUID regattaId,
        UUID captureSessionId,
        long frameOffset,
        long timestampMs,
        String tileId,
        Integer tileX,
        Integer tileY
    ) {
        if (!captureSessionBelongsToRegatta(captureSessionId, regattaId)) {
            throw new NotFoundException("Capture session not found in this regatta");
        }

        UUID markerId = UUID.randomUUID();
        String sql = """
            INSERT INTO timing_markers (
                id, capture_session_id, frame_offset, timestamp_ms, is_linked, is_approved, tile_id, tile_x, tile_y
            ) VALUES (?, ?, ?, ?, FALSE, FALSE, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, markerId);
            stmt.setObject(2, captureSessionId);
            stmt.setLong(3, frameOffset);
            stmt.setLong(4, timestampMs);
            stmt.setString(5, tileId);
            if (tileX == null) {
                stmt.setNull(6, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(6, tileX);
            }
            if (tileY == null) {
                stmt.setNull(7, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(7, tileY);
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create marker", e);
        }

        return findById(regattaId, markerId).orElseThrow();
    }

    @Transactional
    public TimingMarker update(
        UUID regattaId,
        UUID markerId,
        Long frameOffset,
        Long timestampMs,
        String tileId,
        Integer tileX,
        Integer tileY,
        Boolean isApproved
    ) {
        TimingMarker existing = findById(regattaId, markerId)
            .orElseThrow(() -> new NotFoundException("Marker not found"));

        long newFrameOffset = frameOffset != null ? frameOffset : existing.frameOffset();
        long newTimestampMs = timestampMs != null ? timestampMs : existing.timestampMs();
        String newTileId = tileId != null ? tileId : existing.tileId();
        Integer newTileX = tileX != null ? tileX : existing.tileX();
        Integer newTileY = tileY != null ? tileY : existing.tileY();

        boolean contentChanged = newFrameOffset != existing.frameOffset()
            || newTimestampMs != existing.timestampMs()
            || !stringEquals(newTileId, existing.tileId())
            || !intEquals(newTileX, existing.tileX())
            || !intEquals(newTileY, existing.tileY());

        boolean newApproval = isApproved != null ? isApproved : existing.isApproved();
        if (existing.isLinked() && contentChanged) {
            newApproval = false;
        }

        String sql = """
            UPDATE timing_markers
            SET frame_offset = ?, timestamp_ms = ?, tile_id = ?, tile_x = ?, tile_y = ?, is_approved = ?
            WHERE id = ?
            """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, newFrameOffset);
            stmt.setLong(2, newTimestampMs);
            stmt.setString(3, newTileId);
            if (newTileX == null) {
                stmt.setNull(4, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(4, newTileX);
            }
            if (newTileY == null) {
                stmt.setNull(5, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(5, newTileY);
            }
            stmt.setBoolean(6, newApproval);
            stmt.setObject(7, markerId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update marker", e);
        }

        if (existing.entryId() != null) {
            refreshEntryCompletion(existing.entryId());
        }

        return findById(regattaId, markerId).orElseThrow();
    }

    @Transactional
    public TimingMarker link(UUID regattaId, UUID markerId, UUID entryId) {
        if (!entryBelongsToRegatta(entryId, regattaId)) {
            throw new NotFoundException("Entry not found in this regatta");
        }

        TimingMarker existing = findById(regattaId, markerId)
            .orElseThrow(() -> new NotFoundException("Marker not found"));

        String sql = """
            UPDATE timing_markers
            SET entry_id = ?, is_linked = TRUE, is_approved = FALSE
            WHERE id = ?
            """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, entryId);
            stmt.setObject(2, markerId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to link marker", e);
        }

        if (existing.entryId() != null && !existing.entryId().equals(entryId)) {
            refreshEntryCompletion(existing.entryId());
        }
        refreshEntryCompletion(entryId);

        return findById(regattaId, markerId).orElseThrow();
    }

    @Transactional
    public TimingMarker unlink(UUID regattaId, UUID markerId) {
        TimingMarker existing = findById(regattaId, markerId)
            .orElseThrow(() -> new NotFoundException("Marker not found"));

        String sql = """
            UPDATE timing_markers
            SET entry_id = NULL, is_linked = FALSE, is_approved = FALSE
            WHERE id = ?
            """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, markerId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to unlink marker", e);
        }

        if (existing.entryId() != null) {
            refreshEntryCompletion(existing.entryId());
        }

        return findById(regattaId, markerId).orElseThrow();
    }

    @Transactional
    public void delete(UUID regattaId, UUID markerId) {
        TimingMarker existing = findById(regattaId, markerId)
            .orElseThrow(() -> new NotFoundException("Marker not found"));

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM timing_markers WHERE id = ?")) {
            stmt.setObject(1, markerId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete marker", e);
        }

        if (existing.entryId() != null) {
            refreshEntryCompletion(existing.entryId());
        }
    }

    private void refreshEntryCompletion(UUID entryId) {
        List<MarkerCompletionEvaluator.MarkerEvidence> linkedMarkers = linkedMarkersForEntry(entryId);
        MarkerCompletionEvaluator.CompletionResult result = MarkerCompletionEvaluator.evaluate(linkedMarkers);

        String sql = """
            UPDATE entries
            SET completion_status = ?, marker_start_time_ms = ?, marker_finish_time_ms = ?
            WHERE id = ?
            """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, result.completionStatus());
            if (result.markerStartTimeMs() == null) {
                stmt.setNull(2, java.sql.Types.BIGINT);
            } else {
                stmt.setLong(2, result.markerStartTimeMs());
            }
            if (result.markerFinishTimeMs() == null) {
                stmt.setNull(3, java.sql.Types.BIGINT);
            } else {
                stmt.setLong(3, result.markerFinishTimeMs());
            }
            stmt.setObject(4, entryId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to refresh entry completion", e);
        }
    }

    private List<MarkerCompletionEvaluator.MarkerEvidence> linkedMarkersForEntry(UUID entryId) {
        String sql = """
            SELECT timestamp_ms, is_approved
            FROM timing_markers
            WHERE entry_id = ? AND is_linked = TRUE
            ORDER BY timestamp_ms ASC
            """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<MarkerCompletionEvaluator.MarkerEvidence> markers = new ArrayList<>();
                while (rs.next()) {
                    markers.add(new MarkerCompletionEvaluator.MarkerEvidence(
                        rs.getLong("timestamp_ms"),
                        rs.getBoolean("is_approved")
                    ));
                }
                return markers;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load linked markers", e);
        }
    }

    private boolean captureSessionBelongsToRegatta(UUID captureSessionId, UUID regattaId) {
        String sql = "SELECT 1 FROM capture_sessions WHERE id = ? AND regatta_id = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, captureSessionId);
            stmt.setObject(2, regattaId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to validate capture session", e);
        }
    }

    private boolean entryBelongsToRegatta(UUID entryId, UUID regattaId) {
        String sql = "SELECT 1 FROM entries WHERE id = ? AND regatta_id = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, entryId);
            stmt.setObject(2, regattaId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to validate entry", e);
        }
    }

    private TimingMarker mapMarker(ResultSet rs) throws SQLException {
        return new TimingMarker(
            rs.getObject("id", UUID.class),
            rs.getObject("capture_session_id", UUID.class),
            rs.getObject("entry_id", UUID.class),
            rs.getLong("frame_offset"),
            rs.getLong("timestamp_ms"),
            rs.getBoolean("is_linked"),
            rs.getBoolean("is_approved"),
            rs.getString("tile_id"),
            (Integer) rs.getObject("tile_x"),
            (Integer) rs.getObject("tile_y")
        );
    }

    private boolean stringEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean intEquals(Integer left, Integer right) {
        return left == null ? right == null : left.equals(right);
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}
