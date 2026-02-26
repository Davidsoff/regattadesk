package com.regattadesk.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SseEvent.
 * 
 * Tests verify payload structure and JSON serialization.
 */
class SseEventTest {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    void constructor_with_all_parameters_sets_values() {
        SseEvent event = new SseEvent(2, 5, "Draw published");
        
        assertEquals(2, event.getDrawRevision());
        assertEquals(5, event.getResultsRevision());
        assertEquals("Draw published", event.getReason());
    }
    
    @Test
    void constructor_without_reason_sets_null_reason() {
        SseEvent event = new SseEvent(2, 5);
        
        assertEquals(2, event.getDrawRevision());
        assertEquals(5, event.getResultsRevision());
        assertNull(event.getReason());
    }
    
    @Test
    void json_serialization_includes_all_fields() throws Exception {
        SseEvent event = new SseEvent(2, 5, "Draw published");
        
        String json = objectMapper.writeValueAsString(event);
        
        assertTrue(json.contains("\"draw_revision\":2"));
        assertTrue(json.contains("\"results_revision\":5"));
        assertTrue(json.contains("\"reason\":\"Draw published\""));
    }
    
    @Test
    void json_serialization_omits_null_reason() throws Exception {
        SseEvent event = new SseEvent(2, 5, null);
        
        String json = objectMapper.writeValueAsString(event);
        
        assertTrue(json.contains("\"draw_revision\":2"));
        assertTrue(json.contains("\"results_revision\":5"));
        assertFalse(json.contains("\"reason\""));
    }
    
    @Test
    void json_deserialization_works() throws Exception {
        String json = "{\"draw_revision\":3,\"results_revision\":7,\"reason\":\"Test\"}";
        
        SseEvent event = objectMapper.readValue(json, SseEvent.class);
        
        assertEquals(3, event.getDrawRevision());
        assertEquals(7, event.getResultsRevision());
        assertEquals("Test", event.getReason());
    }
    
    @Test
    void json_deserialization_without_reason_works() throws Exception {
        String json = "{\"draw_revision\":3,\"results_revision\":7}";
        
        SseEvent event = objectMapper.readValue(json, SseEvent.class);
        
        assertEquals(3, event.getDrawRevision());
        assertEquals(7, event.getResultsRevision());
        assertNull(event.getReason());
    }
    
    @Test
    void equals_returns_true_for_same_values() {
        SseEvent event1 = new SseEvent(2, 5, "Test");
        SseEvent event2 = new SseEvent(2, 5, "Test");
        
        assertEquals(event1, event2);
    }
    
    @Test
    void equals_returns_true_for_same_values_without_reason() {
        SseEvent event1 = new SseEvent(2, 5);
        SseEvent event2 = new SseEvent(2, 5);
        
        assertEquals(event1, event2);
    }
    
    @Test
    void equals_returns_false_for_different_draw_revision() {
        SseEvent event1 = new SseEvent(2, 5, "Test");
        SseEvent event2 = new SseEvent(3, 5, "Test");
        
        assertNotEquals(event1, event2);
    }
    
    @Test
    void equals_returns_false_for_different_results_revision() {
        SseEvent event1 = new SseEvent(2, 5, "Test");
        SseEvent event2 = new SseEvent(2, 6, "Test");
        
        assertNotEquals(event1, event2);
    }
    
    @Test
    void equals_returns_false_for_different_reason() {
        SseEvent event1 = new SseEvent(2, 5, "Test1");
        SseEvent event2 = new SseEvent(2, 5, "Test2");
        
        assertNotEquals(event1, event2);
    }
    
    @Test
    void hashCode_is_consistent() {
        SseEvent event1 = new SseEvent(2, 5, "Test");
        SseEvent event2 = new SseEvent(2, 5, "Test");
        
        assertEquals(event1.hashCode(), event2.hashCode());
    }
    
    @Test
    void toString_includes_all_fields() {
        SseEvent event = new SseEvent(2, 5, "Test");
        
        String str = event.toString();
        
        assertTrue(str.contains("drawRevision=2"));
        assertTrue(str.contains("resultsRevision=5"));
        assertTrue(str.contains("reason='Test'"));
    }
    
    @Test
    void zero_revisions_are_valid() {
        SseEvent event = new SseEvent(0, 0);
        
        assertEquals(0, event.getDrawRevision());
        assertEquals(0, event.getResultsRevision());
    }
}
