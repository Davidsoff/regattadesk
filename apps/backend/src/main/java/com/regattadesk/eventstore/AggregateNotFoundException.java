package com.regattadesk.eventstore;

import java.util.UUID;

/**
 * Exception thrown when an aggregate is not found in the event store.
 */
public class AggregateNotFoundException extends RuntimeException {
    private final UUID aggregateId;
    
    public AggregateNotFoundException(UUID aggregateId) {
        super(String.format("Aggregate not found: %s", aggregateId));
        this.aggregateId = aggregateId;
    }
    
    public UUID getAggregateId() {
        return aggregateId;
    }
}
