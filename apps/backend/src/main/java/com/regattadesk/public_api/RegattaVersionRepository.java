package com.regattadesk.public_api;

import jakarta.enterprise.context.ApplicationScoped;
import javax.sql.DataSource;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Repository for fetching regatta version information.
 * 
 * Used by versioned public resources to verify regatta existence 
 * and retrieve current draw/results revisions.
 */
@ApplicationScoped
public class RegattaVersionRepository {
    
    @Inject
    DataSource dataSource;
    
    /**
     * Fetches the current draw and results revisions for a regatta.
     * 
     * @param regattaId the regatta UUID
     * @return the version information, or null if regatta not found
     * @throws SQLException if database query fails
     */
    public VersionInfo fetchVersionInfo(UUID regattaId) throws SQLException {
        String sql = "SELECT draw_revision, results_revision FROM regattas WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, regattaId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new VersionInfo(
                        rs.getInt("draw_revision"),
                        rs.getInt("results_revision")
                    );
                }
                return null;
            }
        }
    }
    
    /**
     * Version information for a regatta.
     */
    public record VersionInfo(int drawRevision, int resultsRevision) {}
}
