package com.regattadesk.ruleset;

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
import java.util.stream.Collectors;

/**
 * Service for ruleset lifecycle management.
 * 
 * Provides high-level operations for creating, duplicating, and updating rulesets.
 * Integrates with the event store for persistence and state management.
 */
@ApplicationScoped
public class RulesetService {
    
    private final EventStore eventStore;
    private final ObjectMapper objectMapper;
    
    @Inject
    public RulesetService(EventStore eventStore, ObjectMapper objectMapper) {
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Creates a new ruleset.
     * 
     * @param id the ruleset ID
     * @param name the ruleset name
     * @param version the ruleset version
     * @param description the ruleset description (optional)
     * @param ageCalculationType the age calculation type
     * @return the created ruleset aggregate
     */
    public RulesetAggregate createRuleset(
            UUID id,
            String name,
            String version,
            String description,
            String ageCalculationType) {

        return createRuleset(id, name, version, description, ageCalculationType, null, null);
    }

    public RulesetAggregate createRuleset(
            UUID id,
            String name,
            String version,
            String description,
            String ageCalculationType,
            UUID correlationId,
            UUID causationId) {

        Optional<RulesetAggregate> existing = loadAggregate(id);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        RulesetAggregate ruleset = RulesetAggregate.create(
            id, name, version, description, ageCalculationType
        );
        
        saveAggregate(ruleset, correlationId, causationId);
        return ruleset;
    }
    
    /**
     * Duplicates an existing ruleset.
     * 
     * @param sourceId the source ruleset ID
     * @param newId the new ruleset ID
     * @param newName the new ruleset name
     * @param newVersion the new ruleset version
     * @return the duplicated ruleset aggregate
     * @throws IllegalArgumentException if source ruleset not found
     */
    public RulesetAggregate duplicateRuleset(
            UUID sourceId,
            UUID newId,
            String newName,
            String newVersion) {
        
        Optional<RulesetAggregate> source = loadAggregate(sourceId);
        if (source.isEmpty()) {
            throw new IllegalArgumentException("Source ruleset not found: " + sourceId);
        }
        
        RulesetAggregate duplicated = RulesetAggregate.duplicate(
            newId, source.get(), newName, newVersion
        );
        
        saveAggregate(duplicated, null, null);
        return duplicated;
    }

    public RulesetAggregate duplicateRuleset(
            UUID sourceId,
            String newName,
            String newVersion) {
        return duplicateRuleset(sourceId, UUID.randomUUID(), newName, newVersion);
    }
    
    /**
     * Updates an existing ruleset.
     * 
     * @param id the ruleset ID
     * @param name the new name
     * @param version the new version
     * @param description the new description
     * @param ageCalculationType the new age calculation type
     * @return the updated ruleset aggregate
     * @throws IllegalArgumentException if ruleset not found
     */
    public RulesetAggregate updateRuleset(
            UUID id,
            String name,
            String version,
            String description,
            String ageCalculationType) {
        
        Optional<RulesetAggregate> existing = loadAggregate(id);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Ruleset not found: " + id);
        }
        
        RulesetAggregate ruleset = existing.get();
        return updateRuleset(ruleset, name, version, description, ageCalculationType);
    }

    public RulesetAggregate updateRuleset(
            RulesetAggregate ruleset,
            String name,
            String version,
            String description,
            String ageCalculationType) {

        if (ruleset == null) {
            throw new IllegalArgumentException("Ruleset cannot be null");
        }

        ruleset.update(name, version, description, ageCalculationType);
        
        saveAggregate(ruleset, null, null);
        return ruleset;
    }
    
    /**
     * Loads a ruleset by ID.
     * 
     * @param id the ruleset ID
     * @return the ruleset aggregate, or empty if not found
     */
    public Optional<RulesetAggregate> getRuleset(UUID id) {
        return loadAggregate(id);
    }
    
    /**
     * Lists all rulesets (simplified - in production would use projection/read model).
     * 
     * @return list of all rulesets
     */
    public List<RulesetAggregate> listRulesets() {
        // In a real implementation, this would use a read model/projection
        // For now, we'll return an empty list as a placeholder
        // TODO: Implement proper projection-based listing in follow-up
        return List.of();
    }
    
    // Private helper methods
    
    private Optional<RulesetAggregate> loadAggregate(UUID id) {
        List<EventEnvelope> envelopes = eventStore.readStream(id);
        if (envelopes.isEmpty()) {
            return Optional.empty();
        }
        
        List<DomainEvent> events = envelopes.stream()
            .map(this::toDomainEvent)
            .collect(Collectors.toList());
        
        RulesetAggregate ruleset = new RulesetAggregate(id);
        ruleset.loadFromHistory(events);
        return Optional.of(ruleset);
    }
    
    private void saveAggregate(RulesetAggregate ruleset, UUID correlationId, UUID causationId) {
        int newEventCount = ruleset.getUncommittedEvents().size();
        long expectedVersion = newEventCount > ruleset.getVersion()
            ? -1
            : ruleset.getVersion() - newEventCount + 1;

        EventMetadata metadata = EventMetadata.builder()
            .correlationId(correlationId != null ? correlationId : UUID.randomUUID())
            .causationId(causationId)
            .addData("actor", "system")  // TODO: Get from security context when available
            .build();
        
        eventStore.append(
            ruleset.getId(),
            ruleset.getAggregateType(),
            expectedVersion,
            ruleset.getUncommittedEvents(),
            metadata
        );
        
        ruleset.markEventsAsCommitted();
    }

    private DomainEvent toDomainEvent(EventEnvelope envelope) {
        String rawPayload = envelope.getRawPayload();
        if (rawPayload == null || rawPayload.isBlank()) {
            return envelope.getPayload();
        }

        Class<? extends DomainEvent> eventClass = switch (envelope.getEventType()) {
            case "RulesetCreated" -> RulesetCreatedEvent.class;
            case "RulesetDuplicated" -> RulesetDuplicatedEvent.class;
            case "RulesetUpdated" -> RulesetUpdatedEvent.class;
            case "RulesetDrawPublished" -> RulesetDrawPublishedEvent.class;
            default -> null;
        };

        if (eventClass == null) {
            return envelope.getPayload();
        }

        try {
            return objectMapper.readValue(rawPayload, eventClass);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to deserialize ruleset event type " + envelope.getEventType(),
                e
            );
        }
    }
}
