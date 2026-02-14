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
 * JDBC-based implementation of LineScanTileRepository.
 */
@ApplicationScoped
public class JdbcLineScanTileRepository implements LineScanTileRepository {
    
    private final DataSource dataSource;
    
    @Inject
    public JdbcLineScanTileRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public LineScanTileMetadata save(LineScanTileMetadata metadata) {
        String sql = """
            INSERT INTO line_scan_tiles (
                id, manifest_id, tile_id, tile_x, tile_y, content_type,
                byte_size, minio_bucket, minio_object_key, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (manifest_id, tile_id) DO UPDATE SET
                tile_x = EXCLUDED.tile_x,
                tile_y = EXCLUDED.tile_y,
                content_type = EXCLUDED.content_type,
                byte_size = EXCLUDED.byte_size,
                minio_bucket = EXCLUDED.minio_bucket,
                minio_object_key = EXCLUDED.minio_object_key,
                updated_at = EXCLUDED.updated_at
            RETURNING id, manifest_id, tile_id, tile_x, tile_y, content_type,
                byte_size, minio_bucket, minio_object_key, created_at, updated_at
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            UUID id = metadata.getId() != null ? metadata.getId() : UUID.randomUUID();
            Instant now = Instant.now();
            Instant createdAt = metadata.getCreatedAt() != null ? metadata.getCreatedAt() : now;
            Instant updatedAt = now;
            
            stmt.setObject(1, id);
            stmt.setObject(2, metadata.getManifestId());
            stmt.setString(3, metadata.getTileId());
            stmt.setInt(4, metadata.getTileX());
            stmt.setInt(5, metadata.getTileY());
            stmt.setString(6, metadata.getContentType());
            stmt.setObject(7, metadata.getByteSize());
            stmt.setString(8, metadata.getMinioBucket());
            stmt.setString(9, metadata.getMinioObjectKey());
            stmt.setTimestamp(10, Timestamp.from(createdAt));
            stmt.setTimestamp(11, Timestamp.from(updatedAt));
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToMetadata(rs);
            }
            throw new RuntimeException("Failed to save tile metadata");
            
        } catch (SQLException e) {
            throw new RuntimeException("Database error saving tile metadata", e);
        }
    }
    
    @Override
    public Optional<LineScanTileMetadata> findByManifestAndTileId(UUID manifestId, String tileId) {
        String sql = """
            SELECT id, manifest_id, tile_id, tile_x, tile_y, content_type,
                byte_size, minio_bucket, minio_object_key, created_at, updated_at
            FROM line_scan_tiles
            WHERE manifest_id = ? AND tile_id = ?
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, manifestId);
            stmt.setString(2, tileId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return Optional.of(mapResultSetToMetadata(rs));
            }
            return Optional.empty();
            
        } catch (SQLException e) {
            throw new RuntimeException("Database error finding tile metadata", e);
        }
    }
    
    @Override
    public List<LineScanTileMetadata> findByManifestId(UUID manifestId) {
        String sql = """
            SELECT id, manifest_id, tile_id, tile_x, tile_y, content_type,
                byte_size, minio_bucket, minio_object_key, created_at, updated_at
            FROM line_scan_tiles
            WHERE manifest_id = ?
            ORDER BY tile_y, tile_x
            """;
        
        List<LineScanTileMetadata> tiles = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, manifestId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                tiles.add(mapResultSetToMetadata(rs));
            }
            return tiles;
            
        } catch (SQLException e) {
            throw new RuntimeException("Database error finding tiles for manifest", e);
        }
    }
    
    @Override
    public Optional<LineScanTileMetadata> findByRegattaAndTileId(UUID regattaId, String tileId) {
        String sql = """
            SELECT t.id, t.manifest_id, t.tile_id, t.tile_x, t.tile_y, t.content_type,
                t.byte_size, t.minio_bucket, t.minio_object_key, t.created_at, t.updated_at
            FROM line_scan_tiles t
            JOIN line_scan_manifests m ON t.manifest_id = m.id
            WHERE m.regatta_id = ? AND t.tile_id = ?
            LIMIT 1
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, regattaId);
            stmt.setString(2, tileId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return Optional.of(mapResultSetToMetadata(rs));
            }
            return Optional.empty();
            
        } catch (SQLException e) {
            throw new RuntimeException("Database error finding tile by regatta and tile_id", e);
        }
    }
    
    private LineScanTileMetadata mapResultSetToMetadata(ResultSet rs) throws SQLException {
        return LineScanTileMetadata.builder()
            .id(rs.getObject("id", UUID.class))
            .manifestId(rs.getObject("manifest_id", UUID.class))
            .tileId(rs.getString("tile_id"))
            .tileX(rs.getInt("tile_x"))
            .tileY(rs.getInt("tile_y"))
            .contentType(rs.getString("content_type"))
            .byteSize(rs.getObject("byte_size", Integer.class))
            .minioBucket(rs.getString("minio_bucket"))
            .minioObjectKey(rs.getString("minio_object_key"))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .updatedAt(rs.getTimestamp("updated_at").toInstant())
            .build();
    }
}
