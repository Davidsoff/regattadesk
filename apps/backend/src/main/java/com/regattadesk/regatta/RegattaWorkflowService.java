package com.regattadesk.regatta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regattadesk.eventstore.DomainEvent;
import com.regattadesk.eventstore.EventEnvelope;
import com.regattadesk.eventstore.EventMetadata;
import com.regattadesk.eventstore.EventStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class RegattaWorkflowService {

    private final EventStore eventStore;
    private final ObjectMapper objectMapper;

    @Inject
    public RegattaWorkflowService(EventStore eventStore, ObjectMapper objectMapper) {
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
    }

    public Optional<RegattaState> getRegatta(UUID regattaId) {
        return loadAggregate(regattaId).map(this::toState);
    }

    public RegattaState requireRegatta(UUID regattaId) {
        return getRegatta(regattaId)
            .orElseThrow(() -> new IllegalArgumentException("Regatta not found"));
    }

    public GeneratedDrawState generateDraw(UUID regattaId, Long requestedSeed) {
        RegattaAggregate regatta = loadAggregate(regattaId)
            .orElseThrow(() -> new IllegalArgumentException("Regatta not found"));

        long seed = requestedSeed != null ? requestedSeed : ThreadLocalRandom.current().nextLong();
        regatta.generateDraw(seed);
        saveAggregate(regatta);

        return new GeneratedDrawState(seed, true, false);
    }

    public PublicationState publishDraw(UUID regattaId) {
        RegattaAggregate regatta = loadAggregate(regattaId)
            .orElseThrow(() -> new IllegalArgumentException("Regatta not found"));

        long seed = regatta.getDrawSeed() != null
            ? regatta.getDrawSeed()
            : ThreadLocalRandom.current().nextLong();

        if (regatta.getDrawSeed() == null) {
            regatta.generateDraw(seed);
        }

        regatta.publishDraw(seed);
        saveAggregate(regatta);

        return new PublicationState(regatta.getDrawRevision(), regatta.getResultsRevision(), true, true);
    }

    public PublicationState unpublishDraw(UUID regattaId) {
        RegattaAggregate regatta = loadAggregate(regattaId)
            .orElseThrow(() -> new IllegalArgumentException("Regatta not found"));

        regatta.unpublishDraw();
        saveAggregate(regatta);

        return new PublicationState(regatta.getDrawRevision(), regatta.getResultsRevision(), false, false);
    }

    private Optional<RegattaAggregate> loadAggregate(UUID id) {
        List<EventEnvelope> envelopes = eventStore.readStream(id);
        if (envelopes.isEmpty()) {
            return Optional.empty();
        }

        List<DomainEvent> events = envelopes.stream()
            .map(this::toDomainEvent)
            .toList();

        RegattaAggregate regatta = new RegattaAggregate(id);
        regatta.loadFromHistory(events);
        return Optional.of(regatta);
    }

    private void saveAggregate(RegattaAggregate regatta) {
        int newEventCount = regatta.getUncommittedEvents().size();
        long expectedVersion = newEventCount > regatta.getVersion()
            ? -1
            : regatta.getVersion() - newEventCount + 1;

        eventStore.append(
            regatta.getId(),
            regatta.getAggregateType(),
            expectedVersion,
            regatta.getUncommittedEvents(),
            EventMetadata.builder().build()
        );

        regatta.markEventsAsCommitted();
    }

    private DomainEvent toDomainEvent(EventEnvelope envelope) {
        Class<? extends DomainEvent> eventClass = switch (envelope.getEventType()) {
            case "RegattaCreated" -> RegattaCreatedEvent.class;
            case "DrawGenerated" -> DrawGeneratedEvent.class;
            case "DrawPublished" -> DrawPublishedEvent.class;
            case "DrawUnpublished" -> DrawUnpublishedEvent.class;
            case "ResultsRevisionIncremented" -> ResultsRevisionIncrementedEvent.class;
            case "RegattaPenaltyConfigurationUpdated" -> RegattaPenaltyConfigurationUpdatedEvent.class;
            default -> null;
        };

        if (eventClass == null) {
            return envelope.getPayload();
        }

        String rawPayload = envelope.getRawPayload();
        if (rawPayload == null || rawPayload.isBlank()) {
            return objectMapper.convertValue(envelope.getPayload(), eventClass);
        }

        try {
            return objectMapper.readValue(rawPayload, eventClass);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize regatta event " + envelope.getEventType(), e);
        }
    }

    public record RegattaState(
        UUID id,
        String name,
        String description,
        String timeZone,
        String status,
        java.math.BigDecimal entryFee,
        String currency,
        int drawRevision,
        int resultsRevision,
        Long drawSeed,
        boolean drawPublished
    ) {
        public boolean drawGenerated() {
            return drawSeed != null;
        }
    }

    public record GeneratedDrawState(long seed, boolean generated, boolean published) {
    }

    public record PublicationState(int drawRevision, int resultsRevision, boolean generated, boolean published) {
    }

    private RegattaState toState(RegattaAggregate aggregate) {
        return new RegattaState(
            aggregate.getId(),
            aggregate.getName(),
            aggregate.getDescription(),
            aggregate.getTimeZone(),
            aggregate.getStatus(),
            aggregate.getEntryFee(),
            aggregate.getCurrency(),
            aggregate.getDrawRevision(),
            aggregate.getResultsRevision(),
            aggregate.getDrawSeed(),
            aggregate.isDrawPublished()
        );
    }
}
