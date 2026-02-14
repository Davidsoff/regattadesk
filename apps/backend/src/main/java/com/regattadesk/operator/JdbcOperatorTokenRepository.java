package com.regattadesk.operator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.regattadesk.operator.events.OperatorTokenEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC-based implementation of OperatorTokenRepository.
 */
@ApplicationScoped
public class JdbcOperatorTokenRepository implements OperatorTokenRepository {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());
    private final DataSource dataSource;
    
    @Inject
    public JdbcOperatorTokenRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public OperatorToken save(OperatorToken token) {
        String sql = """
            INSERT INTO operator_tokens (
                id, regatta_id, block_id, station, token, pin,
                valid_from, valid_until, is_active, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, token.getId());
            stmt.setObject(2, token.getRegattaId());
            stmt.setObject(3, token.getBlockId());
            stmt.setString(4, token.getStation());
            stmt.setString(5, token.getToken());
            stmt.setString(6, token.getPin());
            stmt.setTimestamp(7, Timestamp.from(token.getValidFrom()));
            stmt.setTimestamp(8, Timestamp.from(token.getValidUntil()));
            stmt.setBoolean(9, token.isActive());
            stmt.setTimestamp(10, Timestamp.from(token.getCreatedAt()));
            stmt.setTimestamp(11, Timestamp.from(token.getUpdatedAt()));
            
            stmt.executeUpdate();
            return token;
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save operator token", e);
        }
    }
    
    @Override
    public OperatorToken update(OperatorToken token) {
        String sql = """
            UPDATE operator_tokens
            SET regatta_id = ?, block_id = ?, station = ?, token = ?, pin = ?,
                valid_from = ?, valid_until = ?, is_active = ?
            WHERE id = ?
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, token.getRegattaId());
            stmt.setObject(2, token.getBlockId());
            stmt.setString(3, token.getStation());
            stmt.setString(4, token.getToken());
            stmt.setString(5, token.getPin());
            stmt.setTimestamp(6, Timestamp.from(token.getValidFrom()));
            stmt.setTimestamp(7, Timestamp.from(token.getValidUntil()));
            stmt.setBoolean(8, token.isActive());
            stmt.setObject(9, token.getId());
            
            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated == 0) {
                throw new RuntimeException("Token not found: " + token.getId());
            }
            
            return findById(token.getId()).orElseThrow();
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update operator token", e);
        }
    }
    
    @Override
    public Optional<OperatorToken> findById(UUID id) {
        String sql = "SELECT * FROM operator_tokens WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
                return Optional.empty();
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find operator token by ID", e);
        }
    }
    
    @Override
    public Optional<OperatorToken> findByToken(String token) {
        String sql = "SELECT * FROM operator_tokens WHERE token = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, token);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
                return Optional.empty();
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find operator token by token string", e);
        }
    }
    
    @Override
    public List<OperatorToken> findByRegattaId(UUID regattaId) {
        String sql = "SELECT * FROM operator_tokens WHERE regatta_id = ? ORDER BY created_at DESC";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, regattaId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                List<OperatorToken> tokens = new ArrayList<>();
                while (rs.next()) {
                    tokens.add(mapResultSet(rs));
                }
                return tokens;
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find operator tokens by regatta ID", e);
        }
    }

    @Override
    public List<OperatorToken> findByRegattaId(UUID regattaId, int limit, int offset) {
        String sql = """
            SELECT * FROM operator_tokens
            WHERE regatta_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, regattaId);
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);

            try (ResultSet rs = stmt.executeQuery()) {
                List<OperatorToken> tokens = new ArrayList<>();
                while (rs.next()) {
                    tokens.add(mapResultSet(rs));
                }
                return tokens;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find paginated operator tokens by regatta ID", e);
        }
    }
    
    @Override
    public List<OperatorToken> findActiveByRegattaIdAndStation(UUID regattaId, String station) {
        String sql = """
            SELECT * FROM operator_tokens
            WHERE regatta_id = ? AND station = ? AND is_active = true
            ORDER BY created_at DESC
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, regattaId);
            stmt.setString(2, station);
            
            try (ResultSet rs = stmt.executeQuery()) {
                List<OperatorToken> tokens = new ArrayList<>();
                while (rs.next()) {
                    tokens.add(mapResultSet(rs));
                }
                return tokens;
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find active tokens by regatta and station", e);
        }
    }

    @Override
    public List<OperatorToken> findActiveByRegattaId(UUID regattaId, int limit, int offset) {
        String sql = """
            SELECT * FROM operator_tokens
            WHERE regatta_id = ? AND is_active = true
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, regattaId);
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);

            try (ResultSet rs = stmt.executeQuery()) {
                List<OperatorToken> tokens = new ArrayList<>();
                while (rs.next()) {
                    tokens.add(mapResultSet(rs));
                }
                return tokens;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find paginated active tokens by regatta ID", e);
        }
    }
    
    @Override
    public List<OperatorToken> findValidTokens(UUID regattaId, Instant checkTime) {
        String sql = """
            SELECT * FROM operator_tokens
            WHERE regatta_id = ?
              AND is_active = true
              AND valid_from <= ?
              AND valid_until > ?
            ORDER BY created_at DESC
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, regattaId);
            stmt.setTimestamp(2, Timestamp.from(checkTime));
            stmt.setTimestamp(3, Timestamp.from(checkTime));
            
            try (ResultSet rs = stmt.executeQuery()) {
                List<OperatorToken> tokens = new ArrayList<>();
                while (rs.next()) {
                    tokens.add(mapResultSet(rs));
                }
                return tokens;
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find valid tokens", e);
        }
    }
    
    @Override
    public boolean revoke(UUID tokenId) {
        String sql = "UPDATE operator_tokens SET is_active = false WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, tokenId);
            
            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to revoke operator token", e);
        }
    }
    
    @Override
    public boolean existsByToken(String token) {
        String sql = "SELECT COUNT(*) FROM operator_tokens WHERE token = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, token);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
                return false;
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check token existence", e);
        }
    }

    @Override
    public void appendEvent(OperatorTokenEvent event) {
        String aggregateSql = """
            INSERT INTO aggregates (id, aggregate_type, version, created_at, updated_at)
            VALUES (?, ?, 0, ?, ?)
            """;
        String sequenceSql = "SELECT COALESCE(MAX(sequence_number), 0) + 1 FROM event_store WHERE aggregate_id = ?";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Ensure aggregate row exists for this token stream.
                try (PreparedStatement stmt = conn.prepareStatement(aggregateSql)) {
                    stmt.setObject(1, event.getTokenId());
                    stmt.setString(2, "OperatorToken");
                    stmt.setTimestamp(3, Timestamp.from(event.getOccurredAt()));
                    stmt.setTimestamp(4, Timestamp.from(event.getOccurredAt()));
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    // Ignore duplicate aggregate insert attempts.
                    if (!isUniqueConstraintViolation(e)) {
                        throw e;
                    }
                }

                long nextSequence;
                try (PreparedStatement stmt = conn.prepareStatement(sequenceSql)) {
                    stmt.setObject(1, event.getTokenId());
                    try (ResultSet rs = stmt.executeQuery()) {
                        rs.next();
                        nextSequence = rs.getLong(1);
                    }
                }

                String payloadJson = OBJECT_MAPPER.writeValueAsString(toPayload(event));
                String metadataJson = "{\"source\":\"operator-token-service\"}";

                String eventSql = isPostgreSql(conn)
                    ? "INSERT INTO event_store (id, aggregate_id, event_type, sequence_number, payload, metadata, created_at) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)"
                    : "INSERT INTO event_store (id, aggregate_id, event_type, sequence_number, payload, metadata, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement stmt = conn.prepareStatement(eventSql)) {
                    stmt.setObject(1, UUID.randomUUID());
                    stmt.setObject(2, event.getTokenId());
                    stmt.setString(3, event.getEventType());
                    stmt.setLong(4, nextSequence);
                    stmt.setString(5, payloadJson);
                    stmt.setString(6, metadataJson);
                    stmt.setTimestamp(7, Timestamp.from(event.getOccurredAt()));
                    stmt.executeUpdate();
                }

                conn.commit();
            } catch (SQLException | JsonProcessingException e) {
                conn.rollback();
                throw new RuntimeException("Failed to append operator token event", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to append operator token event", e);
        }
    }
    
    private OperatorToken mapResultSet(ResultSet rs) throws SQLException {
        Instant validFrom = getRequiredInstant(rs, "valid_from");
        Instant validUntil = getRequiredInstant(rs, "valid_until");
        Instant createdAt = getRequiredInstant(rs, "created_at");
        Instant updatedAt = getRequiredInstant(rs, "updated_at");

        return new OperatorToken(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("regatta_id"),
            (UUID) rs.getObject("block_id"),
            rs.getString("station"),
            rs.getString("token"),
            rs.getString("pin"),
            validFrom,
            validUntil,
            rs.getBoolean("is_active"),
            createdAt,
            updatedAt
        );
    }

    private Instant getRequiredInstant(ResultSet rs, String columnName) throws SQLException {
        Timestamp ts = rs.getTimestamp(columnName);
        if (ts == null) {
            throw new IllegalStateException("Database column '" + columnName + "' must not be null");
        }
        return ts.toInstant();
    }

    private boolean isPostgreSql(Connection conn) throws SQLException {
        return conn.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
    }

    private boolean isUniqueConstraintViolation(SQLException e) {
        return "23505".equals(e.getSQLState());
    }

    private LinkedHashMap<String, Object> toPayload(OperatorTokenEvent event) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("tokenId", event.getTokenId());
        payload.put("regattaId", event.getRegattaId());
        payload.put("station", event.getStation());
        payload.put("occurredAt", event.getOccurredAt());
        payload.put("performedBy", event.getPerformedBy());
        payload.put("event", event.toString());
        return payload;
    }
}
