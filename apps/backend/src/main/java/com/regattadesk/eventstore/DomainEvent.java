package com.regattadesk.eventstore;

import java.util.UUID;

/**
 * Base interface for all domain events in the system.
 * 
 * Domain events represent facts about state changes in the system.
 * They are immutable and form an append-only audit trail.
 */
public interface DomainEvent {
    /**
     * Returns the type identifier for this event.
     * This is used for deserialization and routing.
     * 
     * @return the event type (e.g., "RegattaCreated", "EntryWithdrawn")
     */
    String getEventType();
    
    /**
     * Returns the aggregate ID this event belongs to.
     * 
     * @return the aggregate ID
     */
    UUID getAggregateId();
}
