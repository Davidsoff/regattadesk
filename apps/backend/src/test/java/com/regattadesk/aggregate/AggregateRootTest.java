package com.regattadesk.aggregate;

import com.regattadesk.eventstore.DomainEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AggregateRoot base class.
 * 
 * Tests verify:
 * - Aggregate initialization and version tracking
 * - Event application and uncommitted events management
 * - State reconstruction from event history
 * - Event replay mechanics
 */
class AggregateRootTest {
    
    @Test
    void testNewAggregateHasVersionMinusOne() {
        UUID id = UUID.randomUUID();
        TestAggregate aggregate = new TestAggregate(id);
        
        assertEquals(id, aggregate.getId());
        assertEquals(-1, aggregate.getVersion());
        assertTrue(aggregate.getUncommittedEvents().isEmpty());
    }
    
    @Test
    void testRaiseEventAddsToUncommittedEvents() {
        UUID id = UUID.randomUUID();
        TestAggregate aggregate = new TestAggregate(id);
        
        TestEvent event = new TestEvent("TestEventType", id, "test data");
        aggregate.raiseTestEvent(event);
        
        List<DomainEvent> uncommitted = aggregate.getUncommittedEvents();
        assertEquals(1, uncommitted.size());
        assertEquals(event, uncommitted.get(0));
        assertEquals(1, aggregate.getAppliedEventCount());
    }
    
    @Test
    void testRaiseMultipleEvents() {
        UUID id = UUID.randomUUID();
        TestAggregate aggregate = new TestAggregate(id);
        
        aggregate.raiseTestEvent(new TestEvent("Event1", id, "data1"));
        aggregate.raiseTestEvent(new TestEvent("Event2", id, "data2"));
        aggregate.raiseTestEvent(new TestEvent("Event3", id, "data3"));
        
        assertEquals(3, aggregate.getUncommittedEvents().size());
        assertEquals(3, aggregate.getAppliedEventCount());
    }
    
    @Test
    void testMarkEventsAsCommitted() {
        UUID id = UUID.randomUUID();
        TestAggregate aggregate = new TestAggregate(id);
        
        aggregate.raiseTestEvent(new TestEvent("Event1", id, "data1"));
        aggregate.raiseTestEvent(new TestEvent("Event2", id, "data2"));
        
        assertEquals(2, aggregate.getUncommittedEvents().size());
        
        aggregate.markEventsAsCommitted();
        
        assertTrue(aggregate.getUncommittedEvents().isEmpty());
        // State should still reflect applied events
        assertEquals(2, aggregate.getAppliedEventCount());
    }
    
    @Test
    void testLoadFromHistory() {
        UUID id = UUID.randomUUID();
        TestAggregate aggregate = new TestAggregate(id);
        
        List<DomainEvent> history = List.of(
                new TestEvent("Event1", id, "data1"),
                new TestEvent("Event2", id, "data2"),
                new TestEvent("Event3", id, "data3")
        );
        
        aggregate.loadFromHistory(history);
        
        // Historical events should not be in uncommitted events
        assertTrue(aggregate.getUncommittedEvents().isEmpty());
        
        // Version should be updated based on event count
        assertEquals(2, aggregate.getVersion());
        
        // State should reflect all applied events
        assertEquals(3, aggregate.getAppliedEventCount());
    }
    
    @Test
    void testLoadFromHistoryThenRaiseNewEvent() {
        UUID id = UUID.randomUUID();
        TestAggregate aggregate = new TestAggregate(id);
        
        // Load historical events
        List<DomainEvent> history = List.of(
                new TestEvent("Event1", id, "data1"),
                new TestEvent("Event2", id, "data2")
        );
        aggregate.loadFromHistory(history);
        
        assertEquals(1, aggregate.getVersion());
        assertTrue(aggregate.getUncommittedEvents().isEmpty());
        
        // Raise a new event
        aggregate.raiseTestEvent(new TestEvent("Event3", id, "data3"));
        
        // Only the new event should be uncommitted
        assertEquals(1, aggregate.getUncommittedEvents().size());
        assertEquals("Event3", aggregate.getUncommittedEvents().get(0).getEventType());
        
        // State should reflect all events
        assertEquals(3, aggregate.getAppliedEventCount());
    }
    
    @Test
    void testUncommittedEventsAreUnmodifiable() {
        UUID id = UUID.randomUUID();
        TestAggregate aggregate = new TestAggregate(id);
        
        aggregate.raiseTestEvent(new TestEvent("Event1", id, "data1"));
        
        List<DomainEvent> uncommitted = aggregate.getUncommittedEvents();
        
        assertThrows(UnsupportedOperationException.class, () -> {
            uncommitted.add(new TestEvent("Event2", id, "data2"));
        });
    }
    
    @Test
    void testRaiseNullEventThrowsException() {
        UUID id = UUID.randomUUID();
        TestAggregate aggregate = new TestAggregate(id);
        
        assertThrows(IllegalArgumentException.class, () -> {
            aggregate.raiseTestEvent(null);
        });
    }
    
    @Test
    void testAggregateWithNullIdThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TestAggregate(null);
        });
    }
    
    @Test
    void testGetAggregateType() {
        UUID id = UUID.randomUUID();
        TestAggregate aggregate = new TestAggregate(id);
        
        assertEquals("TestAggregate", aggregate.getAggregateType());
    }
    
    // Test aggregate implementation
    private static class TestAggregate extends AggregateRoot<TestAggregate> {
        
        private int appliedEventCount = 0;
        
        public TestAggregate(UUID id) {
            super(id);
        }
        
        public void raiseTestEvent(DomainEvent event) {
            raiseEvent(event);
        }
        
        public int getAppliedEventCount() {
            return appliedEventCount;
        }
        
        @Override
        protected void applyEventToState(DomainEvent event) {
            appliedEventCount++;
        }
        
        @Override
        public String getAggregateType() {
            return "TestAggregate";
        }
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
}
