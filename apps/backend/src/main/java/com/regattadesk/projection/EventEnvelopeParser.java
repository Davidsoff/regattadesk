package com.regattadesk.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regattadesk.eventstore.EventEnvelope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Utility class for parsing event payloads from EventEnvelope instances.
 *
 * This class centralizes the parsing logic that was previously duplicated
 * across multiple projection handlers, ensuring consistent event deserialization
 * and error handling.
 */
@ApplicationScoped
public class EventEnvelopeParser {

    private final ObjectMapper objectMapper;

    @Inject
    public EventEnvelopeParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Parses an event payload from the envelope into the specified type.
     * 
     * First attempts to parse from raw payload if available, otherwise
     * converts the deserialized payload object.
     * 
     * @param envelope the event envelope containing the payload
     * @param eventClass the target class type for deserialization
     * @return the parsed event object
     * @throws RuntimeException if parsing fails
     */
    public <T> T parseEvent(EventEnvelope envelope, Class<T> eventClass) {
        try {
            String payload = envelope.getRawPayload();
            if (payload != null && !payload.isBlank()) {
                return objectMapper.readValue(payload, eventClass);
            }
            return objectMapper.convertValue(envelope.getPayload(), eventClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse event payload", e);
        }
    }
}
