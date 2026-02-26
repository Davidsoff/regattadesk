package com.regattadesk.sse;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SseEventIdGenerator.
 * 
 * Tests verify deterministic ID generation and parsing.
 */
class SseEventIdGeneratorTest {
    
    @Test
    void generate_creates_deterministic_id() {
        UUID regattaId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        int drawRevision = 2;
        int resultsRevision = 5;
        int sequence = 0;
        
        String eventId = SseEventIdGenerator.generate(regattaId, drawRevision, resultsRevision, sequence);
        
        assertEquals("123e4567-e89b-12d3-a456-426614174000:2:5:0", eventId);
    }
    
    @Test
    void generate_with_different_sequences_produces_different_ids() {
        UUID regattaId = UUID.randomUUID();
        
        String id1 = SseEventIdGenerator.generate(regattaId, 1, 1, 0);
        String id2 = SseEventIdGenerator.generate(regattaId, 1, 1, 1);
        String id3 = SseEventIdGenerator.generate(regattaId, 1, 1, 2);
        
        assertNotEquals(id1, id2);
        assertNotEquals(id2, id3);
        assertNotEquals(id1, id3);
    }
    
    @Test
    void generate_is_deterministic_for_same_inputs() {
        UUID regattaId = UUID.randomUUID();
        int drawRevision = 3;
        int resultsRevision = 7;
        int sequence = 5;
        
        String id1 = SseEventIdGenerator.generate(regattaId, drawRevision, resultsRevision, sequence);
        String id2 = SseEventIdGenerator.generate(regattaId, drawRevision, resultsRevision, sequence);
        
        assertEquals(id1, id2);
    }
    
    @Test
    void generate_with_null_regattaId_throws_exception() {
        assertThrows(IllegalArgumentException.class, () ->
            SseEventIdGenerator.generate(null, 1, 1, 0)
        );
    }
    
    @Test
    void generate_with_negative_drawRevision_throws_exception() {
        UUID regattaId = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () ->
            SseEventIdGenerator.generate(regattaId, -1, 1, 0)
        );
    }
    
    @Test
    void generate_with_negative_resultsRevision_throws_exception() {
        UUID regattaId = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () ->
            SseEventIdGenerator.generate(regattaId, 1, -1, 0)
        );
    }
    
    @Test
    void generate_with_negative_sequence_throws_exception() {
        UUID regattaId = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () ->
            SseEventIdGenerator.generate(regattaId, 1, 1, -1)
        );
    }
    
    @Test
    void generate_with_zero_revisions_succeeds() {
        UUID regattaId = UUID.randomUUID();
        
        String eventId = SseEventIdGenerator.generate(regattaId, 0, 0, 0);
        
        assertNotNull(eventId);
        assertTrue(eventId.contains(":0:0:0"));
    }
    
    @Test
    void parseRegattaId_extracts_regatta_id() {
        UUID expectedId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        String eventId = "123e4567-e89b-12d3-a456-426614174000:2:5:0";
        
        UUID parsedId = SseEventIdGenerator.parseRegattaId(eventId);
        
        assertEquals(expectedId, parsedId);
    }
    
    @Test
    void parseRegattaId_with_null_returns_null() {
        assertNull(SseEventIdGenerator.parseRegattaId(null));
    }
    
    @Test
    void parseRegattaId_with_blank_returns_null() {
        assertNull(SseEventIdGenerator.parseRegattaId(""));
        assertNull(SseEventIdGenerator.parseRegattaId("   "));
    }
    
    @Test
    void parseRegattaId_with_invalid_format_returns_null() {
        assertNull(SseEventIdGenerator.parseRegattaId("invalid"));
        assertNull(SseEventIdGenerator.parseRegattaId("a:b:c"));
    }
    
    @Test
    void parseRegattaId_with_invalid_uuid_returns_null() {
        assertNull(SseEventIdGenerator.parseRegattaId("not-a-uuid:1:1:0"));
    }
    
    @Test
    void parseDrawRevision_extracts_draw_revision() {
        String eventId = "123e4567-e89b-12d3-a456-426614174000:2:5:0";
        
        int drawRevision = SseEventIdGenerator.parseDrawRevision(eventId);
        
        assertEquals(2, drawRevision);
    }
    
    @Test
    void parseDrawRevision_with_null_returns_minus_one() {
        assertEquals(-1, SseEventIdGenerator.parseDrawRevision(null));
    }
    
    @Test
    void parseDrawRevision_with_invalid_format_returns_minus_one() {
        assertEquals(-1, SseEventIdGenerator.parseDrawRevision("invalid"));
    }
    
    @Test
    void parseDrawRevision_with_non_numeric_returns_minus_one() {
        assertEquals(-1, SseEventIdGenerator.parseDrawRevision("123e4567-e89b-12d3-a456-426614174000:abc:5:0"));
    }
    
    @Test
    void parseResultsRevision_extracts_results_revision() {
        String eventId = "123e4567-e89b-12d3-a456-426614174000:2:5:0";
        
        int resultsRevision = SseEventIdGenerator.parseResultsRevision(eventId);
        
        assertEquals(5, resultsRevision);
    }
    
    @Test
    void parseResultsRevision_with_null_returns_minus_one() {
        assertEquals(-1, SseEventIdGenerator.parseResultsRevision(null));
    }
    
    @Test
    void parseResultsRevision_with_invalid_format_returns_minus_one() {
        assertEquals(-1, SseEventIdGenerator.parseResultsRevision("invalid"));
    }
    
    @Test
    void parseResultsRevision_with_non_numeric_returns_minus_one() {
        assertEquals(-1, SseEventIdGenerator.parseResultsRevision("123e4567-e89b-12d3-a456-426614174000:2:abc:0"));
    }
    
    @Test
    void parseSequence_extracts_sequence() {
        String eventId = "123e4567-e89b-12d3-a456-426614174000:2:5:7";
        
        int sequence = SseEventIdGenerator.parseSequence(eventId);
        
        assertEquals(7, sequence);
    }
    
    @Test
    void parseSequence_with_null_returns_minus_one() {
        assertEquals(-1, SseEventIdGenerator.parseSequence(null));
    }
    
    @Test
    void parseSequence_with_invalid_format_returns_minus_one() {
        assertEquals(-1, SseEventIdGenerator.parseSequence("invalid"));
    }
    
    @Test
    void parseSequence_with_non_numeric_returns_minus_one() {
        assertEquals(-1, SseEventIdGenerator.parseSequence("123e4567-e89b-12d3-a456-426614174000:2:5:abc"));
    }
    
    @Test
    void roundtrip_generate_and_parse_preserves_values() {
        UUID regattaId = UUID.randomUUID();
        int drawRevision = 10;
        int resultsRevision = 15;
        int sequence = 3;
        
        String eventId = SseEventIdGenerator.generate(regattaId, drawRevision, resultsRevision, sequence);
        
        assertEquals(regattaId, SseEventIdGenerator.parseRegattaId(eventId));
        assertEquals(drawRevision, SseEventIdGenerator.parseDrawRevision(eventId));
        assertEquals(resultsRevision, SseEventIdGenerator.parseResultsRevision(eventId));
        assertEquals(sequence, SseEventIdGenerator.parseSequence(eventId));
    }
    
    @Test
    void event_ids_are_lexicographically_ordered_by_revision() {
        UUID regattaId = UUID.randomUUID();
        
        String id1 = SseEventIdGenerator.generate(regattaId, 1, 1, 0);
        String id2 = SseEventIdGenerator.generate(regattaId, 2, 1, 0);
        String id3 = SseEventIdGenerator.generate(regattaId, 2, 2, 0);
        
        // IDs should be comparable by their components
        assertTrue(SseEventIdGenerator.parseDrawRevision(id1) < SseEventIdGenerator.parseDrawRevision(id2));
        assertTrue(SseEventIdGenerator.parseDrawRevision(id2) == SseEventIdGenerator.parseDrawRevision(id3));
        assertTrue(SseEventIdGenerator.parseResultsRevision(id2) < SseEventIdGenerator.parseResultsRevision(id3));
    }
}
