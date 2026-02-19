package com.regattadesk.athlete;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regattadesk.eventstore.EventEnvelope;
import com.regattadesk.projection.ProjectionHandler;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

@ApplicationScoped
public class AthleteProjectionHandler implements ProjectionHandler {

    @Inject
    DataSource dataSource;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public String getProjectionName() {
        return "athlete_projection";
    }

    @Override
    public boolean canHandle(EventEnvelope event) {
        String eventType = event.getEventType();
        return "AthleteCreated".equals(eventType) ||
               "AthleteUpdated".equals(eventType) ||
               "AthleteDeleted".equals(eventType);
    }

    @Override
    public void handle(EventEnvelope envelope) {
        var eventType = envelope.getEventType();

        try {
            switch (eventType) {
                case "AthleteCreated" -> handleAthleteCreated(envelope);
                case "AthleteUpdated" -> handleAthleteUpdated(envelope);
                case "AthleteDeleted" -> handleAthleteDeleted(envelope);
                default -> Log.debugf("Ignoring event type: %s", eventType);
            }
        } catch (Exception e) {
            Log.errorf(e, "Failed to handle event type: %s", eventType);
            throw new RuntimeException("Failed to handle event: " + eventType, e);
        }
    }

    private void handleAthleteCreated(EventEnvelope envelope) throws Exception {
        var event = parseEvent(envelope, AthleteCreatedEvent.class);

        try (Connection conn = dataSource.getConnection()) {
            String sql = insertAthleteSql(conn);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                var now = Timestamp.from(Instant.now());
                stmt.setObject(1, event.getAthleteId());
                stmt.setString(2, event.getFirstName());
                stmt.setString(3, event.getMiddleName());
                stmt.setString(4, event.getLastName());
                stmt.setDate(5, java.sql.Date.valueOf(event.getDateOfBirth()));
                stmt.setString(6, event.getGender());
                stmt.setObject(7, event.getClubId());
                stmt.setTimestamp(8, now);
                stmt.setTimestamp(9, now);
                stmt.executeUpdate();
            }
        }
    }

    private void handleAthleteUpdated(EventEnvelope envelope) throws Exception {
        var event = parseEvent(envelope, AthleteUpdatedEvent.class);

        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                UPDATE athletes
                SET first_name = ?, middle_name = ?, last_name = ?, date_of_birth = ?, gender = ?, club_id = ?, updated_at = ?
                WHERE id = ?
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                var now = Timestamp.from(Instant.now());
                stmt.setString(1, event.getFirstName());
                stmt.setString(2, event.getMiddleName());
                stmt.setString(3, event.getLastName());
                stmt.setDate(4, java.sql.Date.valueOf(event.getDateOfBirth()));
                stmt.setString(5, event.getGender());
                stmt.setObject(6, event.getClubId());
                stmt.setTimestamp(7, now);
                stmt.setObject(8, event.getAthleteId());
                stmt.executeUpdate();
            }
        }
    }

    private void handleAthleteDeleted(EventEnvelope envelope) throws Exception {
        var event = parseEvent(envelope, AthleteDeletedEvent.class);

        try (Connection conn = dataSource.getConnection()) {
            String sql = "DELETE FROM athletes WHERE id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, event.getAthleteId());
                stmt.executeUpdate();
            }
        }
    }

    private <T> T parseEvent(EventEnvelope envelope, Class<T> eventClass) {
        try {
            String payload = envelope.getRawPayload();
            if (payload != null && !payload.isBlank()) {
                return objectMapper.readValue(payload, eventClass);
            }

            return objectMapper.convertValue(envelope.getPayload(), eventClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse event payload", e);
        }
    }

    private String insertAthleteSql(Connection conn) throws SQLException {
        String databaseName = conn.getMetaData().getDatabaseProductName();
        if ("PostgreSQL".equalsIgnoreCase(databaseName)) {
            return """
                INSERT INTO athletes (id, first_name, middle_name, last_name, date_of_birth, gender, club_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    first_name = EXCLUDED.first_name,
                    middle_name = EXCLUDED.middle_name,
                    last_name = EXCLUDED.last_name,
                    date_of_birth = EXCLUDED.date_of_birth,
                    gender = EXCLUDED.gender,
                    club_id = EXCLUDED.club_id,
                    updated_at = EXCLUDED.updated_at
                """;
        }

        return """
            MERGE INTO athletes (id, first_name, middle_name, last_name, date_of_birth, gender, club_id, created_at, updated_at)
            KEY (id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    }
}
