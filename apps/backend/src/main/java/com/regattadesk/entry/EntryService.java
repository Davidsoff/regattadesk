package com.regattadesk.entry;

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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing entries (BC03-004, BC08-001).
 * 
 * Handles entry lifecycle including payment status management.
 */
@ApplicationScoped
public class EntryService {

    @Inject
    EventStore eventStore;

    @Inject
    DataSource dataSource;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EntryProjectionHandler projectionHandler;

    /**
     * Creates a new entry.
     */
    @Transactional
    public EntryDto createEntry(
            UUID regattaId,
            UUID eventId,
            UUID blockId,
            UUID crewId,
            UUID billingClubId
    ) {
        UUID entryId = UUID.randomUUID();
        EntryAggregate aggregate = EntryAggregate.create(
            entryId,
            regattaId,
            eventId,
            blockId,
            crewId,
            billingClubId
        );

        appendChanges(aggregate);
        return toDto(aggregate);
    }

    /**
     * Updates the payment status of an entry (BC08-001).
     * 
     * @param entryId the entry to update
     * @param paymentStatus "paid" or "unpaid"
     * @param paidAt timestamp when payment was received (required for "paid")
     * @param paidBy who recorded the payment
     * @param paymentReference payment reference/transaction ID
     * @return the updated entry
     * @throws IllegalArgumentException if entry not found or invalid status
     */
    @Transactional
    public EntryDto updatePaymentStatus(
            UUID entryId,
            String paymentStatus,
            Instant paidAt,
            String paidBy,
            String paymentReference
    ) {
        EntryAggregate aggregate = loadAggregate(entryId)
            .orElseThrow(() -> new IllegalArgumentException("Entry not found"));

        aggregate.updatePaymentStatus(paymentStatus, paidAt, paidBy, paymentReference);
        appendChanges(aggregate);
        return toDto(aggregate);
    }

    /**
     * Retrieves an entry by ID from the read model.
     */
    public Optional<EntryDto> getEntry(UUID entryId) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM entries WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, entryId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapToDto(rs));
                    }
                    return Optional.empty();
                }
            }
        }
    }

    private void appendChanges(EntryAggregate aggregate) {
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

    private Optional<EntryAggregate> loadAggregate(UUID entryId) {
        List<EventEnvelope> events = eventStore.readStream(entryId);
        if (events.isEmpty()) {
            return Optional.empty();
        }

        EntryAggregate aggregate = new EntryAggregate(entryId);
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
            if ("EntryCreated".equals(eventType)) {
                return objectMapper.readValue(payload, EntryCreatedEvent.class);
            }
            if ("EntryPaymentStatusUpdatedEvent".equals(eventType)) {
                return objectMapper.readValue(payload, EntryPaymentStatusUpdatedEvent.class);
            }
            throw new IllegalStateException("Unsupported entry event type: " + eventType);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize entry event: " + eventType, e);
        }
    }

    private EntryDto mapToDto(ResultSet rs) throws Exception {
        Timestamp paidAtTs = rs.getTimestamp("paid_at");
        Instant paidAt = paidAtTs != null ? paidAtTs.toInstant() : null;
        
        return new EntryDto(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("regatta_id"),
            (UUID) rs.getObject("event_id"),
            (UUID) rs.getObject("block_id"),
            (UUID) rs.getObject("crew_id"),
            (UUID) rs.getObject("billing_club_id"),
            rs.getString("status"),
            rs.getString("payment_status"),
            paidAt,
            rs.getString("paid_by"),
            rs.getString("payment_reference")
        );
    }

    private EntryDto toDto(EntryAggregate aggregate) {
        return new EntryDto(
            aggregate.getId(),
            aggregate.getRegattaId(),
            aggregate.getEventId(),
            aggregate.getBlockId(),
            aggregate.getCrewId(),
            aggregate.getBillingClubId(),
            aggregate.getStatus(),
            aggregate.getPaymentStatus(),
            aggregate.getPaidAt(),
            aggregate.getPaidBy(),
            aggregate.getPaymentReference()
        );
    }
}
