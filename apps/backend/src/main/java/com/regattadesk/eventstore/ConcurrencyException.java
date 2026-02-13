package com.regattadesk.eventstore;

import java.util.UUID;

/**
 * Exception thrown when an optimistic concurrency conflict is detected.
 * 
 * This occurs when attempting to append an event with an expected version
 * that doesn't match the actual current version of the aggregate.
 */
public class ConcurrencyException extends RuntimeException {
    private final UUID aggregateId;
    private final long expectedVersion;
    private final long actualVersion;
    
    public ConcurrencyException(UUID aggregateId, long expectedVersion, long actualVersion) {
        super(String.format(
            "Concurrency conflict for aggregate %s: expected version %d, but actual version is %d",
            aggregateId, expectedVersion, actualVersion
        ));
        this.aggregateId = aggregateId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }
    
    public UUID getAggregateId() {
        return aggregateId;
    }
    
    public long getExpectedVersion() {
        return expectedVersion;
    }
    
    public long getActualVersion() {
        return actualVersion;
    }
}
