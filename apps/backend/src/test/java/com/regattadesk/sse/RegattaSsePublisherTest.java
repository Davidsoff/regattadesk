package com.regattadesk.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RegattaSsePublisher.
 * 
 * Tests verify:
 * - Broadcaster creation per regatta
 * - Event broadcasting and formatting
 * - Deterministic event ID generation
 * - Sequence counter management
 */
class RegattaSsePublisherTest {
    
    private RegattaSsePublisher publisher;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        publisher = new RegattaSsePublisher();
        objectMapper = new ObjectMapper();
        
        // Inject ObjectMapper manually for testing
        publisher.objectMapper = objectMapper;
    }
    
    @Test
    void getStream_creates_new_broadcaster_for_regatta() {
        UUID regattaId = UUID.randomUUID();
        
        Multi<String> stream = publisher.getStream(regattaId);
        
        assertNotNull(stream);
    }
    
    @Test
    void getStream_returns_same_broadcaster_for_same_regatta() {
        UUID regattaId = UUID.randomUUID();
        
        Multi<String> stream1 = publisher.getStream(regattaId);
        Multi<String> stream2 = publisher.getStream(regattaId);
        
        assertNotNull(stream1);
        assertNotNull(stream2);
        // Both streams should receive the same events (tested by broadcasting below)
    }
    
    @Test
    void broadcastSnapshot_sends_snapshot_event() throws Exception {
        UUID regattaId = UUID.randomUUID();
        
        // Subscribe to stream first (without heartbeat for testing)
        AssertSubscriber<String> subscriber = publisher.getStream(regattaId, false)
            .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        
        // Broadcast snapshot
        publisher.broadcastSnapshot(regattaId, 2, 5);
        
        // Wait for event
        subscriber.awaitNextItem(Duration.ofSeconds(2));
        
        // Verify snapshot event was received
        List<String> items = subscriber.getItems();
        assertFalse(items.isEmpty(), "Should have received at least one item");
        
        String firstItem = items.get(0);
        assertTrue(firstItem.contains("event: snapshot"), "Should be snapshot event");
        assertTrue(firstItem.contains("\"draw_revision\":2"), "Should contain draw revision");
        assertTrue(firstItem.contains("\"results_revision\":5"), "Should contain results revision");
        assertTrue(firstItem.startsWith("id: " + regattaId), "Should have event ID with regatta UUID");
    }
    
    @Test
    void broadcastDrawRevision_sends_draw_revision_event() throws Exception {
        UUID regattaId = UUID.randomUUID();
        
        AssertSubscriber<String> subscriber = publisher.getStream(regattaId, false)
            .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        
        publisher.broadcastDrawRevision(regattaId, 3, 5, "Draw published");
        
        subscriber.awaitNextItem(Duration.ofSeconds(2));
        
        List<String> items = subscriber.getItems();
        assertFalse(items.isEmpty());
        
        String firstItem = items.get(0);
        assertTrue(firstItem.contains("event: draw_revision"));
        assertTrue(firstItem.contains("\"draw_revision\":3"));
        assertTrue(firstItem.contains("\"results_revision\":5"));
        assertTrue(firstItem.contains("\"reason\":\"Draw published\""));
    }
    
    @Test
    void broadcastResultsRevision_sends_results_revision_event() throws Exception {
        UUID regattaId = UUID.randomUUID();
        
        AssertSubscriber<String> subscriber = publisher.getStream(regattaId, false)
            .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        
        publisher.broadcastResultsRevision(regattaId, 2, 6, "Results updated");
        
        subscriber.awaitNextItem(Duration.ofSeconds(2));
        
        List<String> items = subscriber.getItems();
        assertFalse(items.isEmpty());
        
        String firstItem = items.get(0);
        assertTrue(firstItem.contains("event: results_revision"));
        assertTrue(firstItem.contains("\"draw_revision\":2"));
        assertTrue(firstItem.contains("\"results_revision\":6"));
        assertTrue(firstItem.contains("\"reason\":\"Results updated\""));
    }
    
    @Test
    void broadcast_without_reason_omits_reason_field() throws Exception {
        UUID regattaId = UUID.randomUUID();
        
        AssertSubscriber<String> subscriber = publisher.getStream(regattaId, false)
            .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        
        publisher.broadcastDrawRevision(regattaId, 3, 5, null);
        
        subscriber.awaitNextItem(Duration.ofSeconds(2));
        
        List<String> items = subscriber.getItems();
        assertFalse(items.isEmpty());
        
        String firstItem = items.get(0);
        assertFalse(firstItem.contains("\"reason\""), "Null reason should be omitted from JSON");
    }
    
    @Test
    void event_ids_are_deterministic_and_sequential() throws Exception {
        UUID regattaId = UUID.randomUUID();
        
        AssertSubscriber<String> subscriber = publisher.getStream(regattaId, false)
            .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        
        // Broadcast multiple events
        publisher.broadcastSnapshot(regattaId, 1, 1);
        publisher.broadcastDrawRevision(regattaId, 2, 1, null);
        publisher.broadcastResultsRevision(regattaId, 2, 2, null);
        
        subscriber.awaitItems(3, Duration.ofSeconds(2));
        
        List<String> items = subscriber.getItems();
        assertEquals(3, items.size(), "Should receive 3 events");
        
        // Extract event IDs
        String id0 = extractEventId(items.get(0));
        String id1 = extractEventId(items.get(1));
        String id2 = extractEventId(items.get(2));
        
        // Verify IDs contain regatta UUID
        assertTrue(id0.startsWith(regattaId.toString()));
        assertTrue(id1.startsWith(regattaId.toString()));
        assertTrue(id2.startsWith(regattaId.toString()));
        
        // Verify sequences are 0, 1, 2
        assertEquals(0, SseEventIdGenerator.parseSequence(id0));
        assertEquals(1, SseEventIdGenerator.parseSequence(id1));
        assertEquals(2, SseEventIdGenerator.parseSequence(id2));
    }
    
    @Test
    void multiple_subscribers_receive_same_events() throws Exception {
        UUID regattaId = UUID.randomUUID();
        
        // Create two subscribers (without heartbeat for testing)
        AssertSubscriber<String> subscriber1 = publisher.getStream(regattaId, false)
            .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        AssertSubscriber<String> subscriber2 = publisher.getStream(regattaId, false)
            .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        
        // Broadcast event
        publisher.broadcastSnapshot(regattaId, 2, 5);
        
        // Both should receive the event
        subscriber1.awaitNextItem(Duration.ofSeconds(2));
        subscriber2.awaitNextItem(Duration.ofSeconds(2));
        
        assertFalse(subscriber1.getItems().isEmpty());
        assertFalse(subscriber2.getItems().isEmpty());
        
        // Events should be the same
        String event1 = subscriber1.getItems().get(0);
        String event2 = subscriber2.getItems().get(0);
        assertEquals(event1, event2);
    }
    
    @Test
    void different_regattas_have_independent_streams() throws Exception {
        UUID regatta1 = UUID.randomUUID();
        UUID regatta2 = UUID.randomUUID();
        
        AssertSubscriber<String> subscriber1 = publisher.getStream(regatta1, false)
            .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        AssertSubscriber<String> subscriber2 = publisher.getStream(regatta2, false)
            .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        
        // Broadcast to regatta1 only
        publisher.broadcastSnapshot(regatta1, 1, 1);
        
        subscriber1.awaitNextItem(Duration.ofSeconds(2));
        
        // Subscriber 1 should have event
        assertFalse(subscriber1.getItems().isEmpty());
        
        // Subscriber 2 should not have received anything
        // Give it a short wait
        try {
            subscriber2.awaitItems(1, Duration.ofMillis(200));
            fail("Subscriber 2 should not have received regatta 1's event");
        } catch (AssertionError e) {
            // Expected - subscriber 2 should not receive the event
        }
        
        // Verify subscriber2 has no items or items don't contain regatta1's ID
        if (!subscriber2.getItems().isEmpty()) {
            String item = subscriber2.getItems().get(0);
            assertFalse(item.contains(regatta1.toString()), 
                       "Regatta 2 subscriber should not receive regatta 1 events");
        }
    }
    
    @Test
    void resetSequenceCounter_resets_to_zero() throws Exception {
        UUID regattaId = UUID.randomUUID();
        
        // Broadcast an event to initialize counter
        publisher.getStream(regattaId, false).subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        publisher.broadcastSnapshot(regattaId, 1, 1);
        
        // Wait for broadcast
        Thread.sleep(100);
        
        // Counter should be 1
        assertEquals(1, publisher.getSequenceCounter(regattaId));
        
        // Reset
        publisher.resetSequenceCounter(regattaId);
        
        // Counter should be 0
        assertEquals(0, publisher.getSequenceCounter(regattaId));
    }
    
    @Test
    void getSequenceCounter_returns_zero_for_new_regatta() {
        UUID regattaId = UUID.randomUUID();
        
        assertEquals(0, publisher.getSequenceCounter(regattaId));
    }
    
    @Test
    void sse_message_format_is_correct() throws Exception {
        UUID regattaId = UUID.randomUUID();
        
        AssertSubscriber<String> subscriber = publisher.getStream(regattaId, false)
            .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        
        publisher.broadcastSnapshot(regattaId, 2, 5);
        
        subscriber.awaitNextItem(Duration.ofSeconds(2));
        
        String message = subscriber.getItems().get(0);
        
        // Verify SSE format:
        // id: {id}
        // event: {type}
        // data: {json}
        // (blank line)
        assertTrue(message.startsWith("id: "), "Should start with 'id: '");
        assertTrue(message.contains("\nevent: "), "Should contain 'event: ' on new line");
        assertTrue(message.contains("\ndata: "), "Should contain 'data: ' on new line");
        assertTrue(message.endsWith("\n\n"), "Should end with double newline");
    }
    
    /**
     * Helper method to extract event ID from SSE message.
     */
    private String extractEventId(String sseMessage) {
        String[] lines = sseMessage.split("\n");
        for (String line : lines) {
            if (line.startsWith("id: ")) {
                return line.substring(4);
            }
        }
        return null;
    }
}
