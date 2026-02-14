package com.regattadesk.projection;

import com.regattadesk.eventstore.DomainEvent;
import com.regattadesk.eventstore.EventEnvelope;
import com.regattadesk.eventstore.EventMetadata;
import com.regattadesk.eventstore.EventStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for projection infrastructure.
 * 
 * Tests verify:
 * - Checkpoint persistence and retrieval
 * - Projection worker processes events
 * - Idempotent replay behavior
 * - No duplicate projection side effects
 */
@QuarkusTest
class ProjectionIntegrationTest {
    
    @Inject
    ProjectionCheckpointRepository checkpointRepository;
    
    @Inject
    EventStore eventStore;
    
    @Inject
    ProjectionWorker projectionWorker;
    
    private String testProjectionName;
    
    @BeforeEach
    void setUp() {
        // Use unique projection name for each test to avoid conflicts
        testProjectionName = "TestProjection-" + UUID.randomUUID();
    }
    
    @Test
    void testSaveAndGetCheckpoint() {
        UUID eventId = UUID.randomUUID();
        Instant processedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        
        ProjectionCheckpoint checkpoint = new ProjectionCheckpoint(
                testProjectionName, eventId, processedAt
        );
        
        checkpointRepository.saveCheckpoint(checkpoint);
        
        Optional<ProjectionCheckpoint> retrieved = checkpointRepository.getCheckpoint(testProjectionName);
        
        assertTrue(retrieved.isPresent());
        assertEquals(testProjectionName, retrieved.get().getProjectionName());
        assertEquals(eventId, retrieved.get().getLastProcessedEventId());
        // Compare with truncated time for database precision compatibility
        assertEquals(processedAt, retrieved.get().getLastProcessedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    }
    
    @Test
    void testUpdateCheckpoint() {
        UUID eventId1 = UUID.randomUUID();
        Instant processedAt1 = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        
        ProjectionCheckpoint checkpoint1 = new ProjectionCheckpoint(
                testProjectionName, eventId1, processedAt1
        );
        checkpointRepository.saveCheckpoint(checkpoint1);
        
        // Update with new event
        UUID eventId2 = UUID.randomUUID();
        Instant processedAt2 = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        
        ProjectionCheckpoint checkpoint2 = new ProjectionCheckpoint(
                testProjectionName, eventId2, processedAt2
        );
        checkpointRepository.saveCheckpoint(checkpoint2);
        
        // Verify update
        Optional<ProjectionCheckpoint> retrieved = checkpointRepository.getCheckpoint(testProjectionName);
        
        assertTrue(retrieved.isPresent());
        assertEquals(eventId2, retrieved.get().getLastProcessedEventId());
        // Compare with truncated time for database precision compatibility
        assertEquals(processedAt2, retrieved.get().getLastProcessedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    }
    
    @Test
    void testGetNonExistentCheckpoint() {
        Optional<ProjectionCheckpoint> result = checkpointRepository.getCheckpoint("NonExistentProjection");
        
        assertFalse(result.isPresent());
    }
    
    @Test
    void testGetLastProcessedEventId() {
        UUID eventId = UUID.randomUUID();
        
        ProjectionCheckpoint checkpoint = new ProjectionCheckpoint(
                testProjectionName, eventId, Instant.now()
        );
        checkpointRepository.saveCheckpoint(checkpoint);
        
        Optional<UUID> lastEventId = checkpointRepository.getLastProcessedEventId(testProjectionName);
        
        assertTrue(lastEventId.isPresent());
        assertEquals(eventId, lastEventId.get());
    }
    
    @Test
    @Transactional
    void testProjectionWorkerProcessesEvents() {
        // Create test events in event store
        UUID aggregateId = UUID.randomUUID();
        TestEvent event1 = new TestEvent("TestEventType", aggregateId, "data1");
        TestEvent event2 = new TestEvent("TestEventType", aggregateId, "data2");
        
        eventStore.append(aggregateId, "TestAggregate", -1, 
                         List.of(event1, event2), EventMetadata.builder().build());
        
        // Create a test projection handler
        TestProjectionHandler handler = new TestProjectionHandler(testProjectionName);
        
        // Process projection
        int processed = projectionWorker.processProjection(handler);
        
        // Verify events were processed
        assertTrue(processed > 0);
        assertTrue(handler.getHandledEventCount() > 0);
        
        // Verify checkpoint was saved
        Optional<UUID> lastEventId = checkpointRepository.getLastProcessedEventId(testProjectionName);
        assertTrue(lastEventId.isPresent());
    }
    
    @Test
    @Transactional
    void testIdempotentReplay() {
        // Create test event
        UUID aggregateId = UUID.randomUUID();
        TestEvent event = new TestEvent("TestEventType", aggregateId, "data");
        
        eventStore.append(aggregateId, "TestAggregate", -1, 
                         List.of(event), EventMetadata.builder().build());
        
        // Create a test projection handler that tracks duplicates
        TestProjectionHandler handler = new TestProjectionHandler(testProjectionName);
        
        // Process projection first time
        int processed1 = projectionWorker.processProjection(handler);
        int handledCount1 = handler.getHandledEventCount();
        
        assertTrue(processed1 > 0);
        assertTrue(handledCount1 > 0);
        
        // Process projection second time (should not reprocess same events)
        int processed2 = projectionWorker.processProjection(handler);
        int handledCount2 = handler.getHandledEventCount();
        
        // No new events should be processed
        assertEquals(0, processed2);
        assertEquals(handledCount1, handledCount2);
    }
    
    // Test event implementation
    private static class TestEvent implements DomainEvent {
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
    
    // Test projection handler implementation
    private static class TestProjectionHandler implements ProjectionHandler {
        private final String projectionName;
        private int handledEventCount = 0;
        
        public TestProjectionHandler(String projectionName) {
            this.projectionName = projectionName;
        }
        
        @Override
        public String getProjectionName() {
            return projectionName;
        }
        
        @Override
        public boolean canHandle(EventEnvelope event) {
            return "TestEventType".equals(event.getEventType());
        }
        
        @Override
        public void handle(EventEnvelope event) {
            handledEventCount++;
        }
        
        public int getHandledEventCount() {
            return handledEventCount;
        }
    }
}
