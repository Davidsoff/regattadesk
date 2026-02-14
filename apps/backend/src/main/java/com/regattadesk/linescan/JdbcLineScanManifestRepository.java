package com.regattadesk.linescan;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JDBC-based implementation of LineScanManifestRepository.
 */
@ApplicationScoped
public class JdbcLineScanManifestRepository implements LineScanManifestRepository {
    
    private final DataSource dataSource;
    private final LineScanTileRepository tileRepository;
    
    @Inject
    public JdbcLineScanManifestRepository(DataSource dataSource, LineScanTileRepository tileRepository) {
        this.dataSource = dataSource;
        this.tileRepository = tileRepository;
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
                return mapResultSetToManifest(rs);
            }
            throw new RuntimeException("Failed to save manifest");
            
        } catch (SQLException e) {
            throw new RuntimeException("Database error saving manifest", e);
        }
    }
    
    @Override
    public Optional<LineScanManifest> findById(UUID manifestId) {
        String sql = """
            SELECT id, regatta_id, capture_session_id, tile_size_px, primary_format,
                fallback_format, x_origin_timestamp_ms, ms_per_pixel, retention_days,
                prune_window_seconds, retention_state, prune_eligible_at, pruned_at,
                created_at, updated_at
            FROM line_scan_manifests
            WHERE id = ?
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, manifestId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return Optional.of(mapResultSetToManifest(rs));
            }
            return Optional.empty();
            
        } catch (SQLException e) {
            throw new RuntimeException("Database error finding manifest by ID", e);
        }
    }
    
    @Override
    public Optional<LineScanManifest> findByCaptureSessionId(UUID captureSessionId) {
        String sql = """
            SELECT id, regatta_id, capture_session_id, tile_size_px, primary_format,
                fallback_format, x_origin_timestamp_ms, ms_per_pixel, retention_days,
                prune_window_seconds, retention_state, prune_eligible_at, pruned_at,
                created_at, updated_at
            FROM line_scan_manifests
            WHERE capture_session_id = ?
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, captureSessionId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return Optional.of(mapResultSetToManifest(rs));
            }
            return Optional.empty();
            
        } catch (SQLException e) {
            throw new RuntimeException("Database error finding manifest by capture session", e);
        }
    }
    
    private LineScanManifest mapResultSetToManifest(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        
        // Get tiles for this manifest
        List<LineScanTileMetadata> tileMetadata = tileRepository.findByManifestId(id);
        List<LineScanManifestTile> tiles = tileMetadata.stream()
            .map(tm -> new LineScanManifestTile(
                tm.getTileId(),
                tm.getTileX(),
                tm.getTileY(),
                tm.getContentType(),
                tm.getByteSize()
            ))
            .collect(Collectors.toList());
        
        return LineScanManifest.builder()
            .id(id)
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
