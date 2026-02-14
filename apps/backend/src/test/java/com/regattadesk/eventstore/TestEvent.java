package com.regattadesk.eventstore;

import java.util.UUID;

/**
 * Test event implementation for use in tests.
 */
public class TestEvent implements DomainEvent {
    private final String eventType;
    private final UUID aggregateId;
    private final String data;
    
    public TestEvent(String eventType, UUID aggregateId, String data) {
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.data = data;
    }
    
    @Override
    public String getEventType() {
        return eventType;
    }
    
    @Override
    public UUID getAggregateId() {
        return aggregateId;
    }
    
    public String getData() {
        return data;
    }
}
