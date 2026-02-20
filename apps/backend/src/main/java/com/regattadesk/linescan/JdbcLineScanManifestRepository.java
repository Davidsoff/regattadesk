package com.regattadesk.linescan;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC-based implementation of LineScanManifestRepository.
 */
@ApplicationScoped
public class JdbcLineScanManifestRepository implements LineScanManifestRepository {
    
    private final DataSource dataSource;
    
    @Inject
    public JdbcLineScanManifestRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public LineScanManifest save(LineScanManifest manifest) {
        String sql = """
            INSERT INTO line_scan_manifests (
                id, regatta_id, capture_session_id, tile_size_px, primary_format,
                fallback_format, x_origin_timestamp_ms, ms_per_pixel, retention_days,
                prune_window_seconds, retention_state, prune_eligible_at, pruned_at,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (capture_session_id) DO UPDATE SET
                tile_size_px = EXCLUDED.tile_size_px,
                primary_format = EXCLUDED.primary_format,
                fallback_format = EXCLUDED.fallback_format,
                x_origin_timestamp_ms = EXCLUDED.x_origin_timestamp_ms,
                ms_per_pixel = EXCLUDED.ms_per_pixel,
                retention_days = EXCLUDED.retention_days,
                prune_window_seconds = EXCLUDED.prune_window_seconds,
                retention_state = EXCLUDED.retention_state,
                prune_eligible_at = EXCLUDED.prune_eligible_at,
                pruned_at = EXCLUDED.pruned_at,
                updated_at = EXCLUDED.updated_at
            RETURNING id, regatta_id, capture_session_id, tile_size_px, primary_format,
                fallback_format, x_origin_timestamp_ms, ms_per_pixel, retention_days,
                prune_window_seconds, retention_state, prune_eligible_at, pruned_at,
                created_at, updated_at
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            UUID id = manifest.getId() != null ? manifest.getId() : UUID.randomUUID();
            Instant now = Instant.now();
            Instant createdAt = manifest.getCreatedAt() != null ? manifest.getCreatedAt() : now;
            Instant updatedAt = now;
            
            stmt.setObject(1, id);
            stmt.setObject(2, manifest.getRegattaId());
            stmt.setObject(3, manifest.getCaptureSessionId());
            stmt.setInt(4, manifest.getTileSizePx());
            stmt.setString(5, manifest.getPrimaryFormat());
            stmt.setString(6, manifest.getFallbackFormat());
            stmt.setLong(7, manifest.getXOriginTimestampMs());
            stmt.setDouble(8, manifest.getMsPerPixel());
            stmt.setInt(9, manifest.getRetentionDays());
            stmt.setInt(10, manifest.getPruneWindowSeconds());
            stmt.setString(11, manifest.getRetentionState().getValue());
            stmt.setTimestamp(12, manifest.getPruneEligibleAt() != null ? 
                Timestamp.from(manifest.getPruneEligibleAt()) : null);
            stmt.setTimestamp(13, manifest.getPrunedAt() != null ? 
                Timestamp.from(manifest.getPrunedAt()) : null);
            stmt.setTimestamp(14, Timestamp.from(createdAt));
            stmt.setTimestamp(15, Timestamp.from(updatedAt));
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToManifest(rs, List.of());
            }
            throw new RuntimeException("Failed to save manifest");
            
        } catch (SQLException e) {
            throw new RuntimeException("Database error saving manifest", e);
        }
    }
    
    @Override
    public Optional<LineScanManifest> findById(UUID manifestId) {
        String sql = """
            SELECT m.id, m.regatta_id, m.capture_session_id, m.tile_size_px, m.primary_format,
                m.fallback_format, m.x_origin_timestamp_ms, m.ms_per_pixel, m.retention_days,
                m.prune_window_seconds, m.retention_state, m.prune_eligible_at, m.pruned_at,
                m.created_at, m.updated_at, t.tile_id, t.tile_x, t.tile_y, t.content_type, t.byte_size
            FROM line_scan_manifests m
            LEFT JOIN line_scan_tiles t ON m.id = t.manifest_id
            WHERE m.id = ?
            ORDER BY t.tile_y, t.tile_x
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, manifestId);
            ResultSet rs = stmt.executeQuery();
            
            return mapManifestWithTiles(rs);
            
        } catch (SQLException e) {
            throw new RuntimeException("Database error finding manifest by ID", e);
        }
    }
    
    @Override
    public Optional<LineScanManifest> findByCaptureSessionId(UUID captureSessionId) {
        String sql = """
            SELECT m.id, m.regatta_id, m.capture_session_id, m.tile_size_px, m.primary_format,
                m.fallback_format, m.x_origin_timestamp_ms, m.ms_per_pixel, m.retention_days,
                m.prune_window_seconds, m.retention_state, m.prune_eligible_at, m.pruned_at,
                m.created_at, m.updated_at, t.tile_id, t.tile_x, t.tile_y, t.content_type, t.byte_size
            FROM line_scan_manifests m
            LEFT JOIN line_scan_tiles t ON m.id = t.manifest_id
            WHERE m.capture_session_id = ?
            ORDER BY t.tile_y, t.tile_x
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, captureSessionId);
            ResultSet rs = stmt.executeQuery();
            
            return mapManifestWithTiles(rs);
            
        } catch (SQLException e) {
            throw new RuntimeException("Database error finding manifest by capture session", e);
        }
    }
    
    private Optional<LineScanManifest> mapManifestWithTiles(ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return Optional.empty();
        }
        List<LineScanManifestTile> tiles = new ArrayList<>();
        LineScanManifest manifest = mapResultSetToManifest(rs, tiles);
        String tileId = rs.getString("tile_id");
        if (tileId != null) {
            tiles.add(new LineScanManifestTile(
                tileId,
                rs.getInt("tile_x"),
                rs.getInt("tile_y"),
                rs.getString("content_type"),
                rs.getObject("byte_size", Integer.class)
            ));
        }
        while (rs.next()) {
            String nextTileId = rs.getString("tile_id");
            if (nextTileId == null) {
                continue;
            }
            tiles.add(new LineScanManifestTile(
                nextTileId,
                rs.getInt("tile_x"),
                rs.getInt("tile_y"),
                rs.getString("content_type"),
                rs.getObject("byte_size", Integer.class)
            ));
        }
        return Optional.of(manifest);
    }

    private LineScanManifest mapResultSetToManifest(ResultSet rs, List<LineScanManifestTile> tiles) throws SQLException {
        return LineScanManifest.builder()
            .id(rs.getObject("id", UUID.class))
            .regattaId(rs.getObject("regatta_id", UUID.class))
            .captureSessionId(rs.getObject("capture_session_id", UUID.class))
            .tileSizePx(rs.getInt("tile_size_px"))
            .primaryFormat(rs.getString("primary_format"))
            .fallbackFormat(rs.getString("fallback_format"))
            .xOriginTimestampMs(rs.getLong("x_origin_timestamp_ms"))
            .msPerPixel(rs.getDouble("ms_per_pixel"))
            .tiles(tiles)
            .retentionDays(rs.getInt("retention_days"))
            .pruneWindowSeconds(rs.getInt("prune_window_seconds"))
            .retentionState(LineScanManifest.RetentionState.fromValue(rs.getString("retention_state")))
            .pruneEligibleAt(rs.getTimestamp("prune_eligible_at") != null ? 
                rs.getTimestamp("prune_eligible_at").toInstant() : null)
            .prunedAt(rs.getTimestamp("pruned_at") != null ? 
                rs.getTimestamp("pruned_at").toInstant() : null)
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .updatedAt(rs.getTimestamp("updated_at").toInstant())
            .build();
    }
}
