package com.regattadesk.eventstore;

import java.util.List;
import java.util.UUID;

/**
 * Event store repository interface for appending and reading domain events.
 * 
 * Provides operations for:
 * - Appending events with optimistic concurrency control
 * - Reading event streams by aggregate
 * - Reading events by type
 * - Reading global event streams
 */
public interface EventStore {
    
    /**
     * Appends events to an aggregate stream with optimistic concurrency control.
     * 
     * @param aggregateId the aggregate ID
     * @param aggregateType the aggregate type
     * @param expectedVersion the expected current version of the aggregate (-1 for new aggregate)
     * @param events the events to append
     * @param metadata the metadata for the events
     * @throws ConcurrencyException if the expected version doesn't match the actual version
     * @throws IllegalArgumentException if events list is empty or contains null values
     */
    void append(UUID aggregateId, String aggregateType, long expectedVersion, 
                List<DomainEvent> events, EventMetadata metadata);
    
    /**
     * Reads all events for a specific aggregate in sequence order.
     * 
     * @param aggregateId the aggregate ID
     * @return list of event envelopes in sequence order
     */
    List<EventEnvelope> readStream(UUID aggregateId);
    
    /**
     * Reads events for an aggregate starting from a specific sequence number.
     * 
     * @param aggregateId the aggregate ID
     * @param fromSequence the starting sequence number (inclusive)
     * @return list of event envelopes in sequence order
     */
    List<EventEnvelope> readStream(UUID aggregateId, long fromSequence);
    
    /**
     * Reads all events of a specific type across all aggregates.
     * 
     * @param eventType the event type
     * @param limit maximum number of events to return
     * @param offset number of events to skip
     * @return list of event envelopes ordered by creation time
     */
    List<EventEnvelope> readByEventType(String eventType, int limit, int offset);
    
    /**
     * Reads events globally across all aggregates with pagination.
     * 
     * @param limit maximum number of events to return
     * @param offset number of events to skip
     * @return list of event envelopes ordered by creation time
     */
    List<EventEnvelope> readGlobal(int limit, int offset);
    
    /**
     * Gets the current version of an aggregate.
     * 
     * @param aggregateId the aggregate ID
     * @return the current version, or -1 if aggregate doesn't exist
     */
    long getCurrentVersion(UUID aggregateId);
}
