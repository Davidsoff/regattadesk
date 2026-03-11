package com.regattadesk.linescan.repository;

import com.regattadesk.linescan.model.TimingMarker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JDBC implementation of TimingMarkerRepository for BC06-006.
 */
@ApplicationScoped
public class JdbcTimingMarkerRepository implements TimingMarkerRepository {
    
    private final DataSource dataSource;
    
    @Inject
    public JdbcTimingMarkerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public List<TimingMarker> findApprovedByRegattaId(UUID regattaId) {
        String sql = """
            SELECT m.id, m.capture_session_id, m.entry_id, m.frame_offset,
                   m.timestamp_ms, m.is_linked, m.is_approved, m.tile_id, m.tile_x, m.tile_y
            FROM timing_markers m
            INNER JOIN capture_sessions cs ON m.capture_session_id = cs.id
            WHERE cs.regatta_id = ?
              AND m.is_approved = true
              AND m.is_linked = true
            ORDER BY m.timestamp_ms
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, regattaId);
            ResultSet rs = stmt.executeQuery();
            
            List<TimingMarker> markers = new ArrayList<>();
            while (rs.next()) {
                markers.add(new TimingMarker(
                    rs.getObject("id", UUID.class),
                    rs.getObject("capture_session_id", UUID.class),
                    rs.getObject("entry_id", UUID.class),
                    rs.getLong("frame_offset"),
                    rs.getLong("timestamp_ms"),
                    rs.getBoolean("is_linked"),
                    rs.getBoolean("is_approved"),
                    rs.getString("tile_id"),
                    rs.getObject("tile_x", Integer.class),
                    rs.getObject("tile_y", Integer.class)
                ));
            }
            
            return markers;
            
        } catch (SQLException e) {
            throw new RuntimeException("Database error finding approved markers", e);
        }
    }
}
