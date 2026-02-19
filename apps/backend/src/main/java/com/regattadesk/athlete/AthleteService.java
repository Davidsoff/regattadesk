package com.regattadesk.athlete;

import com.regattadesk.eventstore.EventEnvelope;
import com.regattadesk.eventstore.EventMetadata;
import com.regattadesk.eventstore.EventStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class AthleteService {

    @Inject
    EventStore eventStore;

    @Inject
    DataSource dataSource;

    @Transactional
    public UUID createAthlete(
            String firstName,
            String middleName,
            String lastName,
            LocalDate dateOfBirth,
            String gender,
            UUID clubId
    ) {
        UUID athleteId = UUID.randomUUID();
        var aggregate = AthleteAggregate.create(
                athleteId,
                firstName,
                middleName,
                lastName,
                dateOfBirth,
                gender,
                clubId
        );

        var metadata = EventMetadata.builder()
                .correlationId(UUID.randomUUID())
                .build();

        eventStore.append(
                athleteId,
                "Athlete",
                aggregate.getVersion(),
                aggregate.getUncommittedEvents(),
                metadata
        );

        return athleteId;
    }

    @Transactional
    public void updateAthlete(
            UUID athleteId,
            String firstName,
            String middleName,
            String lastName,
            String gender,
            UUID clubId
    ) {
        var events = eventStore.readStream(athleteId);
        var aggregate = new AthleteAggregate(athleteId);
        
        // Convert EventEnvelopes to DomainEvents
        var domainEvents = events.stream()
                .map(this::convertToDomainEvent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        aggregate.loadFromHistory(domainEvents);

        aggregate.update(firstName, middleName, lastName, gender, clubId);

        var metadata = EventMetadata.builder()
                .correlationId(UUID.randomUUID())
                .build();

        eventStore.append(
                athleteId,
                "Athlete",
                aggregate.getVersion(),
                aggregate.getUncommittedEvents(),
                metadata
        );
    }

    @Transactional
    public void deleteAthlete(UUID athleteId) {
        var events = eventStore.readStream(athleteId);
        var aggregate = new AthleteAggregate(athleteId);
        
        // Convert EventEnvelopes to DomainEvents
        var domainEvents = events.stream()
                .map(this::convertToDomainEvent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        aggregate.loadFromHistory(domainEvents);

        aggregate.delete();

        var metadata = EventMetadata.builder()
                .correlationId(UUID.randomUUID())
                .build();

        eventStore.append(
                athleteId,
                "Athlete",
                aggregate.getVersion(),
                aggregate.getUncommittedEvents(),
                metadata
        );
    }

    public Optional<AthleteDto> getAthlete(UUID athleteId) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM athletes WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, athleteId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapToDto(rs));
                    }
                    return Optional.empty();
                }
            }
        }
    }

    public List<AthleteDto> listAthletes(String search, int limit, String cursor) throws Exception {
        List<AthleteDto> athletes = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            StringBuilder sql = new StringBuilder("SELECT * FROM athletes WHERE 1=1");

            if (search != null && !search.isBlank()) {
                sql.append(" AND (LOWER(first_name) LIKE LOWER(?) OR LOWER(last_name) LIKE LOWER(?))");
            }

            sql.append(" ORDER BY last_name, first_name LIMIT ?");

            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                int paramIndex = 1;
                if (search != null && !search.isBlank()) {
                    String searchPattern = "%" + search + "%";
                    stmt.setString(paramIndex++, searchPattern);
                    stmt.setString(paramIndex++, searchPattern);
                }
                stmt.setInt(paramIndex, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        athletes.add(mapToDto(rs));
                    }
                }
            }
        }

        return athletes;
    }

    private AthleteDto mapToDto(ResultSet rs) throws Exception {
        return new AthleteDto(
                (UUID) rs.getObject("id"),
                rs.getString("first_name"),
                rs.getString("middle_name"),
                rs.getString("last_name"),
                rs.getDate("date_of_birth").toLocalDate(),
                rs.getString("gender"),
                (UUID) rs.getObject("club_id")
        );
    }

    private com.regattadesk.eventstore.DomainEvent convertToDomainEvent(EventEnvelope envelope) {
        // This is a simple conversion - the envelope's payload is already the domain event
        // In a more complex scenario, you might need to deserialize based on event type
        try {
            return (com.regattadesk.eventstore.DomainEvent) envelope.getPayload();
        } catch (Exception e) {
            // If payload is not already a DomainEvent, return null
            return null;
        }
    }
}
