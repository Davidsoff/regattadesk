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
 * JDBC implementation of RegattaRepository for BC06-006.
 */
@ApplicationScoped
public class JdbcRegattaRepository implements RegattaRepository {
    
    private final DataSource dataSource;
    
    @Inject
    public JdbcRegattaRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public boolean isArchived(UUID regattaId) {
        String sql = """
            SELECT status FROM regattas WHERE id = ?
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, regattaId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                String status = rs.getString("status");
                return "archived".equals(status);
            }
            
            return false;
            
        } catch (SQLException e) {
            throw new RuntimeException("Database error checking regatta archived status", e);
        }
    }
}
