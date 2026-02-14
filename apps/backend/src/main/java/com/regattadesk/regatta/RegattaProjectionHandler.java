package com.regattadesk.regatta;

import com.regattadesk.eventstore.EventEnvelope;
import com.regattadesk.projection.ProjectionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Projection handler for Regatta read model.
 * 
 * Transforms regatta domain events into the regattas read model table.
 */
@ApplicationScoped
public class RegattaProjectionHandler implements ProjectionHandler {
    
    private static final Logger LOG = LoggerFactory.getLogger(RegattaProjectionHandler.class);
    
    @Inject
    DataSource dataSource;
    
    @Inject
    ObjectMapper objectMapper;
    
    @Override
    public String getProjectionName() {
        return "RegattaProjection";
    }
    
    @Override
    public boolean canHandle(EventEnvelope event) {
        return "RegattaCreated".equals(event.getEventType());
    }
    
    @Override
    public void handle(EventEnvelope event) {
        if ("RegattaCreated".equals(event.getEventType())) {
            handleRegattaCreated(event);
        }
    }
    
    private void handleRegattaCreated(EventEnvelope envelope) {
        try {
            // Parse the event payload
            RegattaCreatedEvent event = parseEvent(envelope, RegattaCreatedEvent.class);
            
            // Insert into read model
            insertRegatta(event);
            
            LOG.debug("Projected RegattaCreated event for regatta {}", event.getRegattaId());
            
        } catch (Exception e) {
            LOG.error("Failed to handle RegattaCreated event", e);
            throw new RuntimeException("Failed to handle RegattaCreated event", e);
        }
    }
    
    private void insertRegatta(RegattaCreatedEvent event) {
        String sql = """
            INSERT INTO regattas (id, name, description, time_zone, status, entry_fee, currency, 
                                 draw_revision, results_revision, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'draft', ?, ?, 0, 0, now(), now())
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                time_zone = EXCLUDED.time_zone,
                entry_fee = EXCLUDED.entry_fee,
                currency = EXCLUDED.currency,
                updated_at = now()
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, event.getRegattaId());
            stmt.setString(2, event.getName());
            stmt.setString(3, event.getDescription());
            stmt.setString(4, event.getTimeZone());
            stmt.setBigDecimal(5, event.getEntryFee());
            stmt.setString(6, event.getCurrency());
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert regatta into read model", e);
        }
    }
    
    private <T> T parseEvent(EventEnvelope envelope, Class<T> eventClass) {
        try {
            // For now, we use the raw payload from the event envelope
            // In production, you'd want proper event deserialization
            String payload = envelope.getPayload().toString();
            return objectMapper.readValue(payload, eventClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse event payload", e);
        }
    }
}
