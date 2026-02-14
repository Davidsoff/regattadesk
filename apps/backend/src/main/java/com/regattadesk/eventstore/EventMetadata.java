package com.regattadesk.eventstore;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

/**
 * Metadata associated with a domain event.
 * 
 * Captures contextual information about the event such as:
 * - Correlation ID for tracing related events across aggregates
 * - Causation ID for tracking causal chains (event A caused event B)
 * - Additional metadata (user ID, IP address, client info, etc.)
 */
public class EventMetadata {
    private final UUID correlationId;
    private final UUID causationId;
    private final Map<String, Object> additionalData;
    
    private EventMetadata(Builder builder) {
        this.correlationId = builder.correlationId;
        this.causationId = builder.causationId;
        this.additionalData = new HashMap<>(builder.additionalData);
    }
    
    public UUID getCorrelationId() {
        return correlationId;
    }
    
    public UUID getCausationId() {
        return causationId;
    }
    
    public Map<String, Object> getAdditionalData() {
        return new HashMap<>(additionalData);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private UUID correlationId;
        private UUID causationId;
        private Map<String, Object> additionalData = new HashMap<>();
        
        public Builder correlationId(UUID correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public Builder causationId(UUID causationId) {
            this.causationId = causationId;
            return this;
        }
        
        public Builder additionalData(Map<String, Object> data) {
            if (data == null) {
                this.additionalData = new HashMap<>();
            } else {
                this.additionalData = new HashMap<>(data);
            }
            return this;
        }
        
        public Builder addData(String key, Object value) {
            this.additionalData.put(key, value);
            return this;
        }
        
        public EventMetadata build() {
            return new EventMetadata(this);
        }
    }
}
