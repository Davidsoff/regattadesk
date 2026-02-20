package com.regattadesk.athlete;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regattadesk.eventstore.DomainEvent;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AthleteService {

    @Inject
    EventStore eventStore;

    @Inject
    DataSource dataSource;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AthleteProjectionHandler projectionHandler;

    @Transactional
    public AthleteDto createAthlete(
            String firstName,
            String middleName,
            String lastName,
            LocalDate dateOfBirth,
            String gender,
            UUID clubId
    ) {
        UUID athleteId = UUID.randomUUID();
        AthleteAggregate aggregate = AthleteAggregate.create(
            athleteId,
            firstName,
            middleName,
            lastName,
            dateOfBirth,
            gender,
            clubId
        );

        appendChanges(aggregate);
        return toDto(aggregate);
    }

    @Transactional
    public AthleteDto updateAthlete(
            UUID athleteId,
            String firstName,
            String middleName,
            String lastName,
            LocalDate dateOfBirth,
            String gender,
            UUID clubId
    ) {
        AthleteAggregate aggregate = loadAggregate(athleteId)
            .orElseThrow(() -> new IllegalArgumentException("Athlete not found"));

        aggregate.update(firstName, middleName, lastName, dateOfBirth, gender, clubId);
        appendChanges(aggregate);
        return toDto(aggregate);
    }

    @Transactional
    public void deleteAthlete(UUID athleteId) {
        AthleteAggregate aggregate = loadAggregate(athleteId)
            .orElseThrow(() -> new IllegalArgumentException("Athlete not found"));

        aggregate.delete();
        appendChanges(aggregate);
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

    public List<AthleteDto> listAthletes(
            String search,
            String federationCode,
            String federationExternalId,
            int limit,
            String cursor
    ) throws Exception {
        List<AthleteDto> athletes = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            StringBuilder sql = new StringBuilder("SELECT DISTINCT a.* FROM athletes a");

            boolean hasFederationLookup = federationCode != null && !federationCode.isBlank();
            if (hasFederationLookup) {
                sql.append(" JOIN athlete_federation_identifiers afi ON afi.athlete_id = a.id");
            }

            sql.append(" WHERE 1=1");

            if (search != null && !search.isBlank()) {
                sql.append(" AND (LOWER(a.first_name) LIKE LOWER(?) OR LOWER(a.last_name) LIKE LOWER(?))");
            }

            if (hasFederationLookup) {
                sql.append(" AND afi.federation_code = ? AND afi.external_id = ?");
            }

            // Cursor-based pagination is not implemented yet; keep deterministic ordering.
            sql.append(" ORDER BY a.last_name, a.first_name, a.id LIMIT ?");

            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                int paramIndex = 1;
                if (search != null && !search.isBlank()) {
                    String searchPattern = "%" + search + "%";
                    stmt.setString(paramIndex++, searchPattern);
                    stmt.setString(paramIndex++, searchPattern);
                }

                if (hasFederationLookup) {
                    stmt.setString(paramIndex++, federationCode);
                    stmt.setString(paramIndex++, federationExternalId);
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

    public Optional<AthleteDto> getAthleteState(UUID athleteId) {
        return loadAggregate(athleteId).map(this::toDto);
    }

    private void appendChanges(AthleteAggregate aggregate) {
        long expectedVersion = eventStore.getCurrentVersion(aggregate.getId());
        long fromSequence = expectedVersion + 1;
        EventMetadata metadata = EventMetadata.builder()
            .correlationId(UUID.randomUUID())
            .build();

        eventStore.append(
            aggregate.getId(),
            aggregate.getAggregateType(),
            expectedVersion,
            aggregate.getUncommittedEvents(),
            metadata
        );

        for (EventEnvelope envelope : eventStore.readStream(aggregate.getId(), fromSequence)) {
            if (projectionHandler.canHandle(envelope)) {
                projectionHandler.handle(envelope);
            }
        }

        aggregate.markEventsAsCommitted();
    }

    private Optional<AthleteAggregate> loadAggregate(UUID athleteId) {
        List<EventEnvelope> events = eventStore.readStream(athleteId);
        if (events.isEmpty()) {
            return Optional.empty();
        }

        AthleteAggregate aggregate = new AthleteAggregate(athleteId);
        List<DomainEvent> domainEvents = events.stream()
            .map(this::convertToDomainEvent)
            .toList();

        aggregate.loadFromHistory(domainEvents);
        return Optional.of(aggregate);
    }

    private DomainEvent convertToDomainEvent(EventEnvelope envelope) {
        String eventType = envelope.getEventType();
        String payload = envelope.getRawPayload();

        try {
            if ("AthleteCreated".equals(eventType)) {
                return objectMapper.readValue(payload, AthleteCreatedEvent.class);
            }
            if ("AthleteUpdated".equals(eventType)) {
                return objectMapper.readValue(payload, AthleteUpdatedEvent.class);
            }
            if ("AthleteDeleted".equals(eventType)) {
                return objectMapper.readValue(payload, AthleteDeletedEvent.class);
            }
            throw new IllegalStateException("Unsupported athlete event type: " + eventType);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize athlete event: " + eventType, e);
        }
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

    private AthleteDto toDto(AthleteAggregate aggregate) {
        return new AthleteDto(
            aggregate.getId(),
            aggregate.getFirstName(),
            aggregate.getMiddleName(),
            aggregate.getLastName(),
            aggregate.getDateOfBirth(),
            aggregate.getGender(),
            aggregate.getClubId()
        );
    }
}
