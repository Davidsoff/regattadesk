package com.regattadesk.eventstore;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope containing a domain event along with its persistence metadata.
 * 
 * The envelope includes:
 * - Event ID (unique identifier for this specific event instance)
 * - Aggregate ID and type
 * - Sequence number (for ordering and optimistic concurrency)
 * - Event type and payload
 * - Metadata (correlation, causation, additional context)
 * - Timestamp (when the event was persisted)
 */
public class EventEnvelope {
    private final UUID eventId;
    private final UUID aggregateId;
    private final String aggregateType;
    private final String eventType;
    private final long sequenceNumber;
    private final DomainEvent payload;
    private final EventMetadata metadata;
    private final Instant createdAt;
    
    private EventEnvelope(Builder builder) {
        this.eventId = builder.eventId;
        this.aggregateId = builder.aggregateId;
        this.aggregateType = builder.aggregateType;
        this.eventType = builder.eventType;
        this.sequenceNumber = builder.sequenceNumber;
        this.payload = builder.payload;
        this.metadata = builder.metadata;
        this.createdAt = builder.createdAt;
    }
    
    public UUID getEventId() {
        return eventId;
    }
    
    public UUID getAggregateId() {
        return aggregateId;
    }
    
    public String getAggregateType() {
        return aggregateType;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public long getSequenceNumber() {
        return sequenceNumber;
    }
    
    public DomainEvent getPayload() {
        return payload;
    }
    
    public EventMetadata getMetadata() {
        return metadata;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private UUID eventId;
        private UUID aggregateId;
        private String aggregateType;
        private String eventType;
        private long sequenceNumber;
        private DomainEvent payload;
        private EventMetadata metadata;
        private Instant createdAt;
        
        public Builder eventId(UUID eventId) {
            this.eventId = eventId;
            return this;
        }
        
        public Builder aggregateId(UUID aggregateId) {
            this.aggregateId = aggregateId;
            return this;
        }
        
        public Builder aggregateType(String aggregateType) {
            this.aggregateType = aggregateType;
            return this;
        }
        
        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }
        
        public Builder sequenceNumber(long sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }
        
        public Builder payload(DomainEvent payload) {
            this.payload = payload;
            return this;
        }
        
        public Builder metadata(EventMetadata metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public EventEnvelope build() {
            return new EventEnvelope(this);
        }
    }
}
