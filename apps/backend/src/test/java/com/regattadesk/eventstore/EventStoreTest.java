package com.regattadesk.eventstore;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for EventStore append/read operations.
 * 
 * Tests verify:
 * - Append operations with optimistic concurrency control
 * - Read operations for streams, event types, and global events
 * - Transaction boundaries and error handling
 * - Concurrency conflict detection and retry behavior
 */
@QuarkusTest
class EventStoreTest {
    
    @Inject
    EventStore eventStore;
    
    @Test
    void testAppendAndReadSingleEvent() {
        UUID aggregateId = UUID.randomUUID();
        String aggregateType = "TestAggregate";
        
        TestEvent event = new TestEvent("TestEventCreated", aggregateId, "test data");
        EventMetadata metadata = EventMetadata.builder()
                .correlationId(UUID.randomUUID())
                .addData("userId", "test-user")
                .build();
        
        // Append event to new aggregate (expectedVersion = -1)
        eventStore.append(aggregateId, aggregateType, -1, List.of(event), metadata);
        
        // Read back the stream
        List<EventEnvelope> envelopes = eventStore.readStream(aggregateId);
        
        assertEquals(1, envelopes.size());
        EventEnvelope envelope = envelopes.get(0);
        
        assertEquals(aggregateId, envelope.getAggregateId());
        assertEquals(aggregateType, envelope.getAggregateType());
        assertEquals("TestEventCreated", envelope.getEventType());
        assertEquals(1, envelope.getSequenceNumber());
        assertNotNull(envelope.getEventId());
        assertNotNull(envelope.getCreatedAt());
        assertNotNull(envelope.getMetadata());
        assertEquals(metadata.getCorrelationId(), envelope.getMetadata().getCorrelationId());
    }
    
    @Test
    void testAppendMultipleEventsInBatch() {
        UUID aggregateId = UUID.randomUUID();
        String aggregateType = "TestAggregate";
        
        List<DomainEvent> events = Arrays.asList(
                new TestEvent("Event1", aggregateId, "data1"),
                new TestEvent("Event2", aggregateId, "data2"),
                new TestEvent("Event3", aggregateId, "data3")
        );
        
        EventMetadata metadata = EventMetadata.builder()
                .correlationId(UUID.randomUUID())
                .build();
        
        // Append all events in one transaction
        eventStore.append(aggregateId, aggregateType, -1, events, metadata);
        
        // Read back the stream
        List<EventEnvelope> envelopes = eventStore.readStream(aggregateId);
        
        assertEquals(3, envelopes.size());
        
        // Verify sequence numbers
        assertEquals(1, envelopes.get(0).getSequenceNumber());
        assertEquals(2, envelopes.get(1).getSequenceNumber());
        assertEquals(3, envelopes.get(2).getSequenceNumber());
        
        // Verify event types
        assertEquals("Event1", envelopes.get(0).getEventType());
        assertEquals("Event2", envelopes.get(1).getEventType());
        assertEquals("Event3", envelopes.get(2).getEventType());
        
        // Verify current version
        assertEquals(3, eventStore.getCurrentVersion(aggregateId));
    }
    
    @Test
    void testAppendToExistingAggregate() {
        UUID aggregateId = UUID.randomUUID();
        String aggregateType = "TestAggregate";
        
        // First append
        TestEvent event1 = new TestEvent("Event1", aggregateId, "data1");
        eventStore.append(aggregateId, aggregateType, -1, List.of(event1), 
                         EventMetadata.builder().build());
        
        // Second append with correct expected version
        TestEvent event2 = new TestEvent("Event2", aggregateId, "data2");
        eventStore.append(aggregateId, aggregateType, 1, List.of(event2), 
                         EventMetadata.builder().build());
        
        // Verify both events are present
        List<EventEnvelope> envelopes = eventStore.readStream(aggregateId);
        assertEquals(2, envelopes.size());
        assertEquals("Event1", envelopes.get(0).getEventType());
        assertEquals("Event2", envelopes.get(1).getEventType());
        
        // Verify current version
        assertEquals(2, eventStore.getCurrentVersion(aggregateId));
    }
    
    @Test
    void testConcurrencyConflictDetection() {
        UUID aggregateId = UUID.randomUUID();
        String aggregateType = "TestAggregate";
        
        // First append
        TestEvent event1 = new TestEvent("Event1", aggregateId, "data1");
        eventStore.append(aggregateId, aggregateType, -1, List.of(event1), 
                         EventMetadata.builder().build());
        
        // Try to append with wrong expected version
        TestEvent event2 = new TestEvent("Event2", aggregateId, "data2");
        
        ConcurrencyException exception = assertThrows(ConcurrencyException.class, () -> {
            eventStore.append(aggregateId, aggregateType, 0, List.of(event2), 
                             EventMetadata.builder().build());
        });
        
        assertEquals(aggregateId, exception.getAggregateId());
        assertEquals(0, exception.getExpectedVersion());
        assertEquals(1, exception.getActualVersion());
        
        // Verify only first event was persisted
        List<EventEnvelope> envelopes = eventStore.readStream(aggregateId);
        assertEquals(1, envelopes.size());
    }
    
    @Test
    void testConcurrencyRetryPattern() {
        UUID aggregateId = UUID.randomUUID();
        String aggregateType = "TestAggregate";
        
        // Initial append
        eventStore.append(aggregateId, aggregateType, -1, 
                         List.of(new TestEvent("Event1", aggregateId, "data1")),
                         EventMetadata.builder().build());
        
        // Simulate retry pattern
        boolean success = false;
        int maxRetries = 3;
        int attempt = 0;
        
        while (!success && attempt < maxRetries) {
            try {
                long currentVersion = eventStore.getCurrentVersion(aggregateId);
                eventStore.append(aggregateId, aggregateType, currentVersion,
                                 List.of(new TestEvent("Event2", aggregateId, "data2")),
                                 EventMetadata.builder().build());
                success = true;
            } catch (ConcurrencyException e) {
                attempt++;
                // In real code, you might add backoff here
            }
        }
        
        assertTrue(success, "Retry pattern should succeed");
        assertEquals(2, eventStore.getCurrentVersion(aggregateId));
    }
    
    @Test
    void testReadStreamFromSequence() {
        UUID aggregateId = UUID.randomUUID();
        String aggregateType = "TestAggregate";
        
        // Append multiple events
        eventStore.append(aggregateId, aggregateType, -1,
                         Arrays.asList(
                                 new TestEvent("Event1", aggregateId, "data1"),
                                 new TestEvent("Event2", aggregateId, "data2"),
                                 new TestEvent("Event3", aggregateId, "data3"),
                                 new TestEvent("Event4", aggregateId, "data4")
                         ),
                         EventMetadata.builder().build());
        
        // Read from sequence 2 onwards
        List<EventEnvelope> envelopes = eventStore.readStream(aggregateId, 2);
        
        assertEquals(3, envelopes.size());
        assertEquals(2, envelopes.get(0).getSequenceNumber());
        assertEquals(3, envelopes.get(1).getSequenceNumber());
        assertEquals(4, envelopes.get(2).getSequenceNumber());
    }
    
    @Test
    void testReadByEventType() {
        UUID aggregateId1 = UUID.randomUUID();
        UUID aggregateId2 = UUID.randomUUID();
        
        // Create events across multiple aggregates
        eventStore.append(aggregateId1, "TestAggregate", -1,
                         List.of(new TestEvent("SpecialEvent", aggregateId1, "data1")),
                         EventMetadata.builder().build());
        
        eventStore.append(aggregateId2, "TestAggregate", -1,
                         List.of(new TestEvent("SpecialEvent", aggregateId2, "data2")),
                         EventMetadata.builder().build());
        
        eventStore.append(aggregateId1, "TestAggregate", 1,
                         List.of(new TestEvent("OtherEvent", aggregateId1, "data3")),
                         EventMetadata.builder().build());
        
        // Read by event type
        List<EventEnvelope> envelopes = eventStore.readByEventType("SpecialEvent", 10, 0);
        
        assertEquals(2, envelopes.size());
        assertTrue(envelopes.stream().allMatch(e -> "SpecialEvent".equals(e.getEventType())));
    }
    
    @Test
    void testReadByEventTypeWithPagination() {
        UUID aggregateId = UUID.randomUUID();
        
        // Create multiple events of the same type
        for (int i = 0; i < 5; i++) {
            UUID aggId = UUID.randomUUID();
            eventStore.append(aggId, "TestAggregate", -1,
                             List.of(new TestEvent("PagedEvent", aggId, "data" + i)),
                             EventMetadata.builder().build());
        }
        
        // Read first page
        List<EventEnvelope> page1 = eventStore.readByEventType("PagedEvent", 2, 0);
        assertEquals(2, page1.size());
        
        // Read second page
        List<EventEnvelope> page2 = eventStore.readByEventType("PagedEvent", 2, 2);
        assertEquals(2, page2.size());
        
        // Verify no overlap
        assertNotEquals(page1.get(0).getEventId(), page2.get(0).getEventId());
    }
    
    @Test
    void testReadGlobal() {
        // Create events across multiple aggregates
        UUID agg1 = UUID.randomUUID();
        UUID agg2 = UUID.randomUUID();
        
        eventStore.append(agg1, "Type1", -1,
                         List.of(new TestEvent("Event1", agg1, "data1")),
                         EventMetadata.builder().build());
        
        eventStore.append(agg2, "Type2", -1,
                         List.of(new TestEvent("Event2", agg2, "data2")),
                         EventMetadata.builder().build());
        
        // Read global events
        List<EventEnvelope> envelopes = eventStore.readGlobal(10, 0);
        
        // Should have at least our 2 events (may have more from other tests)
        assertTrue(envelopes.size() >= 2);
        
        // Verify ordering by creation time
        for (int i = 0; i < envelopes.size() - 1; i++) {
            assertTrue(envelopes.get(i).getCreatedAt().compareTo(envelopes.get(i + 1).getCreatedAt()) <= 0,
                      "Events should be ordered by creation time");
        }
    }
    
    @Test
    void testReadGlobalWithPagination() {
        // Create several events
        for (int i = 0; i < 5; i++) {
            UUID aggId = UUID.randomUUID();
            eventStore.append(aggId, "TestAggregate", -1,
                             List.of(new TestEvent("GlobalEvent", aggId, "data" + i)),
                             EventMetadata.builder().build());
        }
        
        // Read first page
        List<EventEnvelope> page1 = eventStore.readGlobal(3, 0);
        assertTrue(page1.size() >= 3);
        
        // Read second page with offset
        List<EventEnvelope> page2 = eventStore.readGlobal(3, 3);
        assertTrue(page2.size() >= 2);
    }
    
    @Test
    void testGetCurrentVersionForNonExistentAggregate() {
        UUID nonExistentId = UUID.randomUUID();
        long version = eventStore.getCurrentVersion(nonExistentId);
        assertEquals(-1, version, "Non-existent aggregate should return version -1");
    }
    
    @Test
    void testAppendWithNullEventsList() {
        UUID aggregateId = UUID.randomUUID();
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            eventStore.append(aggregateId, "TestAggregate", -1, null, 
                             EventMetadata.builder().build());
        });
        
        assertTrue(exception.getMessage().contains("cannot be null or empty"));
    }
    
    @Test
    void testAppendWithEmptyEventsList() {
        UUID aggregateId = UUID.randomUUID();
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            eventStore.append(aggregateId, "TestAggregate", -1, List.of(), 
                             EventMetadata.builder().build());
        });
        
        assertTrue(exception.getMessage().contains("cannot be null or empty"));
    }
    
    @Test
    void testAppendWithNullEventInList() {
        UUID aggregateId = UUID.randomUUID();
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            eventStore.append(aggregateId, "TestAggregate", -1, 
                             Arrays.asList(new TestEvent("Event1", aggregateId, "data"), null),
                             EventMetadata.builder().build());
        });
        
        assertTrue(exception.getMessage().contains("cannot contain null values"));
    }
    
    @Test
    void testMetadataPreservation() {
        UUID aggregateId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID causationId = UUID.randomUUID();
        
        EventMetadata metadata = EventMetadata.builder()
                .correlationId(correlationId)
                .causationId(causationId)
                .addData("userId", "user123")
                .addData("clientIp", "192.168.1.1")
                .build();
        
        eventStore.append(aggregateId, "TestAggregate", -1,
                         List.of(new TestEvent("TestEvent", aggregateId, "data")),
                         metadata);
        
        List<EventEnvelope> envelopes = eventStore.readStream(aggregateId);
        assertEquals(1, envelopes.size());
        
        EventMetadata readMetadata = envelopes.get(0).getMetadata();
        assertEquals(correlationId, readMetadata.getCorrelationId());
        assertEquals(causationId, readMetadata.getCausationId());
        assertEquals("user123", readMetadata.getAdditionalData().get("userId"));
        assertEquals("192.168.1.1", readMetadata.getAdditionalData().get("clientIp"));
    }
}
