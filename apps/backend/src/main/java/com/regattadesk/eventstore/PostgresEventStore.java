package com.regattadesk.eventstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PostgreSQL-based implementation of the EventStore interface.
 * 
 * Provides transactional event persistence with optimistic concurrency control
 * and efficient stream reads using database indexes.
 * 
 * Note on transaction management:
 * The @Transactional annotation ensures that all database operations within a method
 * participate in a JTA transaction. When we call dataSource.getConnection(), we get
 * a connection that is already enlisted in the current transaction context managed
 * by Quarkus/Narayana. This means that:
 * - All SQL operations use the same transaction
 * - Rollback occurs automatically on exceptions
 * - Commit happens when the method completes successfully
 */
@ApplicationScoped
public class PostgresEventStore implements EventStore {
    
    @Inject
    DataSource dataSource;
    
    @Inject
    ObjectMapper objectMapper;
    
    @Override
    @Transactional
    public void append(UUID aggregateId, String aggregateType, long expectedVersion,
                      List<DomainEvent> events, EventMetadata metadata) {
        if (aggregateId == null) {
            throw new IllegalArgumentException("aggregateId cannot be null");
        }
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("aggregateType cannot be null or blank");
        }
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("Events list cannot be null or empty");
        }
        
        if (events.stream().anyMatch(e -> e == null)) {
            throw new IllegalArgumentException("Events list cannot contain null values");
        }
        
        try (Connection conn = dataSource.getConnection()) {
            // Check if aggregate exists and verify version
            long currentVersion = getCurrentVersion(conn, aggregateId);
            
            if (expectedVersion != currentVersion) {
                throw new ConcurrencyException(aggregateId, expectedVersion, currentVersion);
            }
            
            // Create aggregate if it doesn't exist (version == -1)
            if (currentVersion == -1) {
                createAggregate(conn, aggregateId, aggregateType);
                currentVersion = 0;
            }
            
            // Append events
            long sequenceNumber = currentVersion + 1;
            for (DomainEvent event : events) {
                appendEvent(conn, aggregateId, event, sequenceNumber, metadata);
                sequenceNumber++;
            }
            
            // Update aggregate version
            updateAggregateVersion(conn, aggregateId, sequenceNumber - 1);
            
        } catch (SQLException e) {
            // Check for unique constraint violations using SQLState
            // 23505: PostgreSQL unique_violation
            // 23000: H2 integrity constraint violation
            String sqlState = e.getSQLState();
            if ("23505".equals(sqlState) || "23000".equals(sqlState)) {
                throw new ConcurrencyException(aggregateId, expectedVersion, getCurrentVersion(aggregateId));
            }
            throw new RuntimeException("Failed to append events", e);
        }
    }
    
    @Override
    public List<EventEnvelope> readStream(UUID aggregateId) {
        return readStream(aggregateId, 0);
    }
    
    @Override
    public List<EventEnvelope> readStream(UUID aggregateId, long fromSequence) {
        if (aggregateId == null) {
            throw new IllegalArgumentException("aggregateId cannot be null");
        }
        if (fromSequence < 0) {
            throw new IllegalArgumentException("fromSequence must be >= 0");
        }

        String sql = """
            SELECT e.id, e.aggregate_id, a.aggregate_type, e.event_type, e.sequence_number,
                   e.payload, e.metadata, e.correlation_id, e.causation_id, e.created_at
            FROM event_store e
            JOIN aggregates a ON e.aggregate_id = a.id
            WHERE e.aggregate_id = ? AND e.sequence_number >= ?
            ORDER BY e.sequence_number ASC
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, aggregateId);
            stmt.setLong(2, fromSequence);
            
            return executeQuery(stmt);
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read stream for aggregate " + aggregateId, e);
        }
    }
    
    @Override
    public List<EventEnvelope> readByEventType(String eventType, int limit, int offset) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType cannot be null or blank");
        }
        if (limit <= 0 || offset < 0) {
            throw new IllegalArgumentException("limit must be > 0 and offset must be >= 0");
        }

        String sql = """
            SELECT e.id, e.aggregate_id, a.aggregate_type, e.event_type, e.sequence_number,
                   e.payload, e.metadata, e.correlation_id, e.causation_id, e.created_at
            FROM event_store e
            JOIN aggregates a ON e.aggregate_id = a.id
            WHERE e.event_type = ?
            ORDER BY e.created_at ASC, e.id ASC
            LIMIT ? OFFSET ?
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, eventType);
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);
            
            return executeQuery(stmt);
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read events by type " + eventType, e);
        }
    }
    
    @Override
    public List<EventEnvelope> readGlobal(int limit, int offset) {
        if (limit <= 0 || offset < 0) {
            throw new IllegalArgumentException("limit must be > 0 and offset must be >= 0");
        }

        String sql = """
            SELECT e.id, e.aggregate_id, a.aggregate_type, e.event_type, e.sequence_number,
                   e.payload, e.metadata, e.correlation_id, e.causation_id, e.created_at
            FROM event_store e
            JOIN aggregates a ON e.aggregate_id = a.id
            ORDER BY e.created_at ASC, e.id ASC
            LIMIT ? OFFSET ?
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, limit);
            stmt.setInt(2, offset);
            
            return executeQuery(stmt);
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read global events", e);
        }
    }
    
    @Override
    public long getCurrentVersion(UUID aggregateId) {
        if (aggregateId == null) {
            throw new IllegalArgumentException("aggregateId cannot be null");
        }

        try (Connection conn = dataSource.getConnection()) {
            return getCurrentVersion(conn, aggregateId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get current version for aggregate " + aggregateId, e);
        }
    }

    private long getCurrentVersion(Connection conn, UUID aggregateId) throws SQLException {
        String sql = "SELECT version FROM aggregates WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, aggregateId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("version");
                } else {
                    return -1; // Aggregate doesn't exist
                }
            }
        }
    }
    
    private void createAggregate(Connection conn, UUID aggregateId, String aggregateType) throws SQLException {
        String sql = """
            INSERT INTO aggregates (id, aggregate_type, version, created_at, updated_at)
            VALUES (?, ?, 0, now(), now())
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, aggregateId);
            stmt.setString(2, aggregateType);
            stmt.executeUpdate();
        }
    }
    
    private void appendEvent(Connection conn, UUID aggregateId, DomainEvent event,
                           long sequenceNumber, EventMetadata metadata) throws SQLException {
        String sql = """
            INSERT INTO event_store (id, aggregate_id, event_type, sequence_number, payload,
                                    metadata, correlation_id, causation_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, UUID.randomUUID());
            stmt.setObject(2, aggregateId);
            stmt.setString(3, event.getEventType());
            stmt.setLong(4, sequenceNumber);
            stmt.setString(5, serializePayload(event));
            stmt.setString(6, serializeMetadata(metadata));
            stmt.setObject(7, metadata != null ? metadata.getCorrelationId() : null);
            stmt.setObject(8, metadata != null ? metadata.getCausationId() : null);
            stmt.executeUpdate();
        }
    }
    
    private void updateAggregateVersion(Connection conn, UUID aggregateId, long version) throws SQLException {
        String sql = "UPDATE aggregates SET version = ?, updated_at = now() WHERE id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, version);
            stmt.setObject(2, aggregateId);
            stmt.executeUpdate();
        }
    }
    
    private List<EventEnvelope> executeQuery(PreparedStatement stmt) throws SQLException {
        List<EventEnvelope> envelopes = new ArrayList<>();
        
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                EventEnvelope envelope = mapResultSetToEnvelope(rs);
                envelopes.add(envelope);
            }
        }
        
        return envelopes;
    }
    
    private EventEnvelope mapResultSetToEnvelope(ResultSet rs) throws SQLException {
        UUID eventId = (UUID) rs.getObject("id");
        UUID aggregateId = (UUID) rs.getObject("aggregate_id");
        String aggregateType = rs.getString("aggregate_type");
        String eventType = rs.getString("event_type");
        long sequenceNumber = rs.getLong("sequence_number");
        String payloadJson = rs.getString("payload");
        String metadataJson = rs.getString("metadata");
        UUID correlationId = (UUID) rs.getObject("correlation_id");
        UUID causationId = (UUID) rs.getObject("causation_id");
        Timestamp createdAt = rs.getTimestamp("created_at");
        
        // Deserialize payload - for now, we'll create a generic event
        // In a real implementation, you'd use a registry to deserialize to the correct type
        DomainEvent payload = deserializePayload(eventType, aggregateId, payloadJson);
        
        // Deserialize metadata
        EventMetadata metadata = deserializeMetadata(metadataJson, correlationId, causationId);
        
        return EventEnvelope.builder()
                .eventId(eventId)
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .eventType(eventType)
                .sequenceNumber(sequenceNumber)
                .payload(payload)
                .rawPayload(payloadJson)
                .metadata(metadata)
                .createdAt(createdAt.toInstant())
                .build();
    }
    
    private String serializePayload(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
    }
    
    private String serializeMetadata(EventMetadata metadata) {
        if (metadata == null) {
            return "{}";
        }
        
        try {
            // correlationId/causationId are stored in dedicated columns for filtering/indexing;
            // JSON metadata intentionally contains only additionalData.
            Map<String, Object> metadataMap = new HashMap<>(metadata.getAdditionalData());
            return objectMapper.writeValueAsString(metadataMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event metadata", e);
        }
    }
    
    private DomainEvent deserializePayload(String eventType, UUID aggregateId, String payloadJson) {
        // TODO: Implement event type registry for proper deserialization
        // Expected pattern:
        // 1. Register event types with their corresponding deserializer classes
        // 2. Use EventTypeRegistry.getDeserializer(eventType) to get the right class
        // 3. Deserialize to the specific event type: objectMapper.readValue(payloadJson, eventClass)
        // For now, return a generic event that preserves the raw JSON payload
        return new GenericDomainEvent(eventType, aggregateId, payloadJson);
    }
    
    private EventMetadata deserializeMetadata(String metadataJson, UUID correlationId, UUID causationId) {
        try {
            Map<String, Object> additionalData;
            if (metadataJson == null || metadataJson.isEmpty() || "{}".equals(metadataJson.trim())) {
                additionalData = new HashMap<>();
            } else {
                additionalData = objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
                if (additionalData == null) {
                    additionalData = new HashMap<>();
                }
            }
            
            return EventMetadata.builder()
                    .correlationId(correlationId)
                    .causationId(causationId)
                    .additionalData(additionalData)
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize event metadata: " + metadataJson, e);
        }
    }
    
    /**
     * Generic domain event implementation for deserialization.
     * Used when the specific event type is not registered.
     */
    private static class GenericDomainEvent implements DomainEvent {
        private final String eventType;
        private final UUID aggregateId;
        private final String rawPayload;
        
        GenericDomainEvent(String eventType, UUID aggregateId, String rawPayload) {
            this.eventType = eventType;
            this.aggregateId = aggregateId;
            this.rawPayload = rawPayload;
        }
        
        @Override
        public String getEventType() {
            return eventType;
        }
        
        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }
        
        public String getRawPayload() {
            return rawPayload;
        }
    }
}
