package com.regattadesk.operator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.regattadesk.eventstore.DomainEvent;
import com.regattadesk.operator.events.CaptureSessionClosedEvent;
import com.regattadesk.operator.events.CaptureSessionStartedEvent;
import com.regattadesk.operator.events.CaptureSessionSyncStateUpdatedEvent;
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
 * JDBC implementation of {@link CaptureSessionRepository}.
 */
@ApplicationScoped
public class JdbcCaptureSessionRepository implements CaptureSessionRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final DataSource dataSource;

    @Inject
    public JdbcCaptureSessionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public CaptureSession save(CaptureSession session) {
        String sql = """
                INSERT INTO capture_sessions (
                    id, regatta_id, block_id, station, device_id,
                    session_type, state, server_time_at_start,
                    device_monotonic_offset_ms, fps,
                    is_synced, drift_exceeded_threshold, unsynced_reason,
                    closed_at, close_reason, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, session.getId());
            stmt.setObject(2, session.getRegattaId());
            stmt.setObject(3, session.getBlockId());
            stmt.setString(4, session.getStation());
            stmt.setString(5, session.getDeviceId());
            stmt.setString(6, session.getSessionType().name());
            stmt.setString(7, session.getState().name());
            stmt.setTimestamp(8, Timestamp.from(session.getServerTimeAtStart()));
            if (session.getDeviceMonotonicOffsetMs() != null) {
                stmt.setLong(9, session.getDeviceMonotonicOffsetMs());
            } else {
                stmt.setNull(9, Types.BIGINT);
            }
            stmt.setInt(10, session.getFps());
            stmt.setBoolean(11, session.isSynced());
            stmt.setBoolean(12, session.isDriftExceededThreshold());
            stmt.setString(13, session.getUnsyncedReason());
            stmt.setTimestamp(14, session.getClosedAt() != null ? Timestamp.from(session.getClosedAt()) : null);
            stmt.setString(15, session.getCloseReason());
            stmt.setTimestamp(16, Timestamp.from(session.getCreatedAt()));
            stmt.setTimestamp(17, Timestamp.from(session.getUpdatedAt()));

            stmt.executeUpdate();
            return session;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save capture session", e);
        }
    }

    @Override
    public CaptureSession update(CaptureSession session) {
        String sql = """
                UPDATE capture_sessions
                SET state = ?,
                    is_synced = ?,
                    drift_exceeded_threshold = ?,
                    unsynced_reason = ?,
                    closed_at = ?,
                    close_reason = ?,
                    updated_at = ?
                WHERE id = ?
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, session.getState().name());
            stmt.setBoolean(2, session.isSynced());
            stmt.setBoolean(3, session.isDriftExceededThreshold());
            stmt.setString(4, session.getUnsyncedReason());
            stmt.setTimestamp(5, session.getClosedAt() != null ? Timestamp.from(session.getClosedAt()) : null);
            stmt.setString(6, session.getCloseReason());
            stmt.setTimestamp(7, Timestamp.from(session.getUpdatedAt()));
            stmt.setObject(8, session.getId());

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new RuntimeException("Capture session not found: " + session.getId());
            }
            return session;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update capture session", e);
        }
    }

    @Override
    public Optional<CaptureSession> findById(UUID id) {
        String sql = """
                SELECT id, regatta_id, block_id, station, device_id,
                       session_type, state, server_time_at_start,
                       device_monotonic_offset_ms, fps,
                       is_synced, drift_exceeded_threshold, unsynced_reason,
                       closed_at, close_reason, created_at, updated_at
                FROM capture_sessions
                WHERE id = ?
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find capture session", e);
        }
    }

    @Override
    public List<CaptureSession> findByRegattaId(UUID regattaId) {
        String sql = """
                SELECT id, regatta_id, block_id, station, device_id,
                       session_type, state, server_time_at_start,
                       device_monotonic_offset_ms, fps,
                       is_synced, drift_exceeded_threshold, unsynced_reason,
                       closed_at, close_reason, created_at, updated_at
                FROM capture_sessions
                WHERE regatta_id = ?
                ORDER BY created_at DESC
                """;

        return queryList(sql, stmt -> stmt.setObject(1, regattaId));
    }

    @Override
    public List<CaptureSession> findByRegattaAndBlock(UUID regattaId, UUID blockId) {
        String sql = """
                SELECT id, regatta_id, block_id, station, device_id,
                       session_type, state, server_time_at_start,
                       device_monotonic_offset_ms, fps,
                       is_synced, drift_exceeded_threshold, unsynced_reason,
                       closed_at, close_reason, created_at, updated_at
                FROM capture_sessions
                WHERE regatta_id = ? AND block_id = ?
                ORDER BY created_at DESC
                """;

        return queryList(sql, stmt -> {
            stmt.setObject(1, regattaId);
            stmt.setObject(2, blockId);
        });
    }

    @Override
    public List<CaptureSession> findOpenByRegattaId(UUID regattaId, String station) {
        String sql;
        if (station != null && !station.isBlank()) {
            sql = """
                    SELECT id, regatta_id, block_id, station, device_id,
                           session_type, state, server_time_at_start,
                           device_monotonic_offset_ms, fps,
                           is_synced, drift_exceeded_threshold, unsynced_reason,
                           closed_at, close_reason, created_at, updated_at
                    FROM capture_sessions
                    WHERE regatta_id = ? AND state = 'open' AND station = ?
                    ORDER BY created_at DESC
                    """;
            return queryList(sql, stmt -> {
                stmt.setObject(1, regattaId);
                stmt.setString(2, station);
            });
        } else {
            sql = """
                    SELECT id, regatta_id, block_id, station, device_id,
                           session_type, state, server_time_at_start,
                           device_monotonic_offset_ms, fps,
                           is_synced, drift_exceeded_threshold, unsynced_reason,
                           closed_at, close_reason, created_at, updated_at
                    FROM capture_sessions
                    WHERE regatta_id = ? AND state = 'open'
                    ORDER BY created_at DESC
                    """;
            return queryList(sql, stmt -> stmt.setObject(1, regattaId));
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

                try (PreparedStatement stmt = conn.prepareStatement(aggregateSql)) {
                    stmt.setObject(1, event.getAggregateId());
                    stmt.setString(2, "CaptureSession");
                    stmt.setTimestamp(3, Timestamp.from(occurredAt));
                    stmt.setTimestamp(4, Timestamp.from(occurredAt));
                    stmt.executeUpdate();
                } catch (SQLException e) {
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
                throw new RuntimeException("Failed to append capture session event", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to append capture session event", e);
        }
    }

    // ---- Helpers ---------------------------------------------------------

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement stmt) throws SQLException;
    }

    private List<CaptureSession> queryList(String sql, StatementBinder binder) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            binder.bind(stmt);
            List<CaptureSession> result = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to query capture sessions", e);
        }
    }

    private CaptureSession mapRow(ResultSet rs) throws SQLException {
        Long offsetMs = rs.getLong("device_monotonic_offset_ms");
        if (rs.wasNull()) offsetMs = null;

        Timestamp closedAtTs = rs.getTimestamp("closed_at");

        return new CaptureSession(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("regatta_id"),
                (UUID) rs.getObject("block_id"),
                rs.getString("station"),
                rs.getString("device_id"),
                CaptureSession.SessionType.valueOf(rs.getString("session_type")),
                CaptureSession.SessionState.valueOf(rs.getString("state")),
                rs.getTimestamp("server_time_at_start").toInstant(),
                offsetMs,
                rs.getInt("fps"),
                rs.getBoolean("is_synced"),
                rs.getBoolean("drift_exceeded_threshold"),
                rs.getString("unsynced_reason"),
                closedAtTs != null ? closedAtTs.toInstant() : null,
                rs.getString("close_reason"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private Instant extractOccurredAt(DomainEvent event) {
        return switch (event) {
            case CaptureSessionStartedEvent e -> e.occurredAt();
            case CaptureSessionSyncStateUpdatedEvent e -> e.occurredAt();
            case CaptureSessionClosedEvent e -> e.closedAt();
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
        metadata.put("source", "capture-session-service");
        String actor = extractActor(event);
        if (actor != null) {
            metadata.put("actor", actor);
        }
        return metadata;
    }

    private String extractActor(DomainEvent event) {
        return switch (event) {
            case CaptureSessionStartedEvent e -> e.actor();
            case CaptureSessionSyncStateUpdatedEvent e -> e.actor();
            case CaptureSessionClosedEvent e -> e.actor();
            default -> null;
        };
    }

    private boolean isUniqueConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
            // H2 uses a different SQLState ("23001" or "23505") but also embeds "unique"
            // in the message for UNIQUE_VIOLATION. Keep secondary check for H2 test compat.
            String msg = current.getMessage();
            if (msg != null && msg.toLowerCase().contains("unique constraint")
                    || (current instanceof SQLException s && "23001".equals(s.getSQLState()))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isPostgreSql(Connection conn) throws SQLException {
        return conn.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
    }
}
