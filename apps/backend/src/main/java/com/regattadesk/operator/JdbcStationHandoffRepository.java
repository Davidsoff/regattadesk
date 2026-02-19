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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            VALUES (?, ?, 0, ?, ?)
            """;
        String sequenceSql = "SELECT COALESCE(MAX(sequence_number), 0) + 1 FROM event_store WHERE aggregate_id = ?";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Instant occurredAt = extractOccurredAt(event);

                // Ensure aggregate row exists for this handoff stream.
                try (PreparedStatement stmt = conn.prepareStatement(aggregateSql)) {
                    stmt.setObject(1, event.getAggregateId());
                    stmt.setString(2, "StationHandoff");
                    stmt.setTimestamp(3, Timestamp.from(occurredAt));
                    stmt.setTimestamp(4, Timestamp.from(occurredAt));
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

                String payloadJson = OBJECT_MAPPER.writeValueAsString(toPayload(event));
                String metadataJson = OBJECT_MAPPER.writeValueAsString(toMetadata(event));

                String eventSql = isPostgreSql(conn)
                    ? "INSERT INTO event_store (id, aggregate_id, event_type, sequence_number, payload, metadata, correlation_id, causation_id, created_at) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)"
                    : "INSERT INTO event_store (id, aggregate_id, event_type, sequence_number, payload, metadata, correlation_id, causation_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement stmt = conn.prepareStatement(eventSql)) {
                    stmt.setObject(1, UUID.randomUUID());
                    stmt.setObject(2, event.getAggregateId());
                    stmt.setString(3, event.getEventType());
                    stmt.setLong(4, nextSequence);
                    stmt.setString(5, payloadJson);
                    stmt.setString(6, metadataJson);
                    stmt.setObject(7, null);
                    stmt.setObject(8, null);
                    stmt.setTimestamp(9, Timestamp.from(occurredAt));
                    stmt.executeUpdate();
                }

                conn.commit();
            } catch (SQLException | JsonProcessingException e) {
                conn.rollback();
                throw new RuntimeException("Failed to append handoff event", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to append handoff event", e);
        }
    }
    
    private boolean isUniqueConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("23505") || message.toLowerCase().contains("unique"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isPostgreSql(Connection conn) throws SQLException {
        return conn.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
    }

    private Instant extractOccurredAt(DomainEvent event) {
        return switch (event) {
            case com.regattadesk.operator.events.StationHandoffRequestedEvent e -> e.occurredAt();
            case com.regattadesk.operator.events.StationHandoffPinRevealedEvent e -> e.occurredAt();
            case com.regattadesk.operator.events.StationHandoffCompletedEvent e -> e.occurredAt();
            case com.regattadesk.operator.events.StationHandoffCancelledEvent e -> e.occurredAt();
            default -> Instant.now();
        };
    }

    private LinkedHashMap<String, Object> toPayload(DomainEvent event) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", event.getEventType());
        payload.put("aggregateId", event.getAggregateId());
        payload.put("event", event);
        return payload;
    }

    private Map<String, Object> toMetadata(DomainEvent event) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "station-handoff-service");
        String actor = extractActor(event);
        if (actor != null) {
            metadata.put("actor", actor);
        }
        return metadata;
    }

    private String extractActor(DomainEvent event) {
        return switch (event) {
            case com.regattadesk.operator.events.StationHandoffRequestedEvent e -> e.actor();
            case com.regattadesk.operator.events.StationHandoffPinRevealedEvent e -> e.actor();
            case com.regattadesk.operator.events.StationHandoffCompletedEvent e -> e.actor();
            case com.regattadesk.operator.events.StationHandoffCancelledEvent e -> e.actor();
            default -> null;
        };
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
