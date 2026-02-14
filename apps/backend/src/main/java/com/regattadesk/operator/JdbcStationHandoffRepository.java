package com.regattadesk.operator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.regattadesk.eventstore.DomainEvent;
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
 * JDBC implementation of StationHandoffRepository.
 */
@ApplicationScoped
public class JdbcStationHandoffRepository implements StationHandoffRepository {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());
    
    private final DataSource dataSource;
    
    @Inject
    public JdbcStationHandoffRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public StationHandoff save(StationHandoff handoff) {
        String sql = """
            INSERT INTO station_handoffs (
                id, regatta_id, token_id, station, requesting_device_id,
                pin, status, created_at, expires_at, completed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, handoff.getId());
            stmt.setObject(2, handoff.getRegattaId());
            stmt.setObject(3, handoff.getTokenId());
            stmt.setString(4, handoff.getStation());
            stmt.setString(5, handoff.getRequestingDeviceId());
            stmt.setString(6, handoff.getPin());
            stmt.setString(7, handoff.getStatus().name());
            stmt.setTimestamp(8, Timestamp.from(handoff.getCreatedAt()));
            stmt.setTimestamp(9, Timestamp.from(handoff.getExpiresAt()));
            stmt.setTimestamp(10, handoff.getCompletedAt() != null ? 
                Timestamp.from(handoff.getCompletedAt()) : null);
            
            stmt.executeUpdate();
            return handoff;
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save station handoff", e);
        }
    }
    
    @Override
    public StationHandoff update(StationHandoff handoff) {
        String sql = """
            UPDATE station_handoffs 
            SET status = ?, completed_at = ?
            WHERE id = ?
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, handoff.getStatus().name());
            stmt.setTimestamp(2, handoff.getCompletedAt() != null ? 
                Timestamp.from(handoff.getCompletedAt()) : null);
            stmt.setObject(3, handoff.getId());
            
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                throw new RuntimeException("Station handoff not found: " + handoff.getId());
            }
            
            return handoff;
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update station handoff", e);
        }
    }
    
    @Override
    public Optional<StationHandoff> findById(UUID id) {
        String sql = """
            SELECT id, regatta_id, token_id, station, requesting_device_id,
                   pin, status, created_at, expires_at, completed_at
            FROM station_handoffs
            WHERE id = ?
            """;
        
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
            throw new RuntimeException("Failed to find station handoff", e);
        }
    }
    
    @Override
    public List<StationHandoff> findPendingByRegattaAndStation(UUID regattaId, String station) {
        String sql = """
            SELECT id, regatta_id, token_id, station, requesting_device_id,
                   pin, status, created_at, expires_at, completed_at
            FROM station_handoffs
            WHERE regatta_id = ? AND station = ? AND status = 'PENDING'
            ORDER BY created_at DESC
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, regattaId);
            stmt.setString(2, station);
            
            List<StationHandoff> handoffs = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    handoffs.add(mapResultSet(rs));
                }
            }
            return handoffs;
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find pending handoffs", e);
        }
    }
    
    @Override
    public List<StationHandoff> findPendingByToken(UUID tokenId) {
        String sql = """
            SELECT id, regatta_id, token_id, station, requesting_device_id,
                   pin, status, created_at, expires_at, completed_at
            FROM station_handoffs
            WHERE token_id = ? AND status = 'PENDING'
            ORDER BY created_at DESC
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, tokenId);
            
            List<StationHandoff> handoffs = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    handoffs.add(mapResultSet(rs));
                }
            }
            return handoffs;
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find pending handoffs by token", e);
        }
    }
    
    @Override
    public void appendEvent(DomainEvent event) {
        String aggregateSql = """
            INSERT INTO aggregates (id, aggregate_type, version, created_at, updated_at)
            VALUES (?, ?, 0, now(), now())
            """;
        String sequenceSql = "SELECT COALESCE(MAX(sequence_number), 0) + 1 FROM event_store WHERE aggregate_id = ?";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Ensure aggregate row exists for this handoff stream.
                try (PreparedStatement stmt = conn.prepareStatement(aggregateSql)) {
                    stmt.setObject(1, event.getAggregateId());
                    stmt.setString(2, "StationHandoff");
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    // Ignore duplicate aggregate insert attempts.
                    if (!isUniqueConstraintViolation(e)) {
                        throw e;
                    }
                }

                long nextSequence;
                try (PreparedStatement stmt = conn.prepareStatement(sequenceSql)) {
                    stmt.setObject(1, event.getAggregateId());
                    try (ResultSet rs = stmt.executeQuery()) {
                        rs.next();
                        nextSequence = rs.getLong(1);
                    }
                }

                String eventSql = """
                    INSERT INTO event_store (
                        aggregate_id, aggregate_type, sequence_number, event_type, 
                        event_data, occurred_at, stream_position
                    ) VALUES (?, ?, ?, ?, ?::jsonb, now(), 
                        (SELECT COALESCE(MAX(stream_position), 0) + 1 FROM event_store))
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(eventSql)) {
                    stmt.setObject(1, event.getAggregateId());
                    stmt.setString(2, "StationHandoff");
                    stmt.setLong(3, nextSequence);
                    stmt.setString(4, event.getEventType());

                    // Serialize event to JSON
                    var eventData = new LinkedHashMap<String, Object>();
                    eventData.put("type", event.getEventType());
                    eventData.put("data", event);
                    String json = OBJECT_MAPPER.writeValueAsString(eventData);
                    stmt.setString(5, json);

                    stmt.executeUpdate();
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Failed to append handoff event", e);
        }
    }
    
    private boolean isUniqueConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("23505") || message.toLowerCase().contains("unique"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
    
    private StationHandoff mapResultSet(ResultSet rs) throws SQLException {
        Timestamp completedAtTs = rs.getTimestamp("completed_at");
        Instant completedAt = completedAtTs != null ? completedAtTs.toInstant() : null;
        
        return new StationHandoff(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("regatta_id"),
            (UUID) rs.getObject("token_id"),
            rs.getString("station"),
            rs.getString("requesting_device_id"),
            rs.getString("pin"),
            StationHandoff.HandoffStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant(),
            completedAt
        );
    }
}
