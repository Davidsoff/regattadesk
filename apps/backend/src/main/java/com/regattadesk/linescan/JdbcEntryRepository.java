package com.regattadesk.linescan;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * JDBC implementation of EntryRepository for BC06-006.
 */
@ApplicationScoped
public class JdbcEntryRepository implements EntryRepository {
    
    private final DataSource dataSource;
    
    @Inject
    public JdbcEntryRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public boolean areAllEntriesApprovedForRegatta(UUID regattaId) {
        String sql = """
            SELECT COUNT(*) as total_count,
                   SUM(CASE WHEN completion_status = 'completed' THEN 1 ELSE 0 END) as completed_count
            FROM entries
            WHERE regatta_id = ?
              AND status NOT IN ('withdrawn_before_draw', 'withdrawn_after_draw')
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, regattaId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                int totalCount = rs.getInt("total_count");
                int completedCount = rs.getInt("completed_count");
                
                // All entries approved means:
                // - Either no active entries (totalCount == 0), return true
                // - Or all active entries are completed
                return totalCount == 0 || totalCount == completedCount;
            }
            
            return false;
            
        } catch (SQLException e) {
            throw new RuntimeException("Database error checking entry approval status", e);
        }
    }
}
