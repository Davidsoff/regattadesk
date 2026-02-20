package com.regattadesk.bibpool;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BibPoolAggregate.
 * 
 * Tests verify:
 * - Bib pool creation with range and explicit list modes
 * - Bib pool updates
 * - Bib pool deletion
 * - Validation of bib allocation modes and constraints
 * - Priority and overflow pool handling
 */
class BibPoolAggregateTest {
    
    @Test
    void testCreateBibPoolWithRange() {
        UUID poolId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        String name = "Block A Pool";
        
        BibPoolAggregate pool = BibPoolAggregate.createWithRange(
            poolId,
            regattaId,
            blockId,
            name,
            1,
            100,
            5,
            false
        );
        
        assertEquals(poolId, pool.getId());
        assertEquals(1, pool.getUncommittedEvents().size());
        
        BibPoolCreatedEvent event = (BibPoolCreatedEvent) pool.getUncommittedEvents().get(0);
        assertEquals("BibPoolCreated", event.getEventType());
        assertEquals(poolId, event.getPoolId());
        assertEquals(regattaId, event.getRegattaId());
        assertEquals(blockId, event.getBlockId());
        assertEquals(name, event.getName());
        assertEquals("range", event.getAllocationMode());
        assertEquals(1, event.getStartBib());
        assertEquals(100, event.getEndBib());
        assertNull(event.getBibNumbers());
        assertEquals(5, event.getPriority());
        assertEquals(false, event.isOverflow());
    }
    
    @Test
    void testCreateBibPoolWithExplicitList() {
        UUID poolId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        String name = "Special Numbers Pool";
        List<Integer> bibNumbers = Arrays.asList(7, 13, 21, 42);
        
        BibPoolAggregate pool = BibPoolAggregate.createWithExplicitList(
            poolId,
            regattaId,
            blockId,
            name,
            bibNumbers,
            10,
            false
        );
        
        assertEquals(poolId, pool.getId());
        assertEquals(1, pool.getUncommittedEvents().size());
        
        BibPoolCreatedEvent event = (BibPoolCreatedEvent) pool.getUncommittedEvents().get(0);
        assertEquals("BibPoolCreated", event.getEventType());
        assertEquals("explicit_list", event.getAllocationMode());
        assertNull(event.getStartBib());
        assertNull(event.getEndBib());
        assertEquals(bibNumbers, event.getBibNumbers());
    }
    
    @Test
    void testCreateOverflowPool() {
        UUID poolId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        String name = "Overflow Pool";
        
        BibPoolAggregate pool = BibPoolAggregate.createWithRange(
            poolId,
            regattaId,
            null, // Overflow pool has no block
            name,
            200,
            299,
            999, // High priority for overflow
            true // is overflow
        );
        
        assertEquals(poolId, pool.getId());
        BibPoolCreatedEvent event = (BibPoolCreatedEvent) pool.getUncommittedEvents().get(0);
        assertNull(event.getBlockId());
        assertEquals(true, event.isOverflow());
    }
    
    @Test
    void testCreateBibPoolWithInvalidRangeThrowsException() {
        UUID poolId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        
        // End bib less than start bib
        assertThrows(IllegalArgumentException.class, () -> {
            BibPoolAggregate.createWithRange(poolId, regattaId, blockId, "Pool", 100, 50, 5, false);
        });
    }
    
    @Test
    void testCreateBibPoolWithNullNameThrowsException() {
        UUID poolId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            BibPoolAggregate.createWithRange(poolId, regattaId, blockId, null, 1, 100, 5, false);
        });
    }
    
    @Test
    void testCreateBibPoolWithBlankNameThrowsException() {
        UUID poolId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            BibPoolAggregate.createWithRange(poolId, regattaId, blockId, "  ", 1, 100, 5, false);
        });
    }
    
    @Test
    void testCreateBibPoolWithNullRegattaIdThrowsException() {
        UUID poolId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            BibPoolAggregate.createWithRange(poolId, null, blockId, "Pool", 1, 100, 5, false);
        });
    }
    
    @Test
    void testCreateBibPoolWithEmptyExplicitListThrowsException() {
        UUID poolId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            BibPoolAggregate.createWithExplicitList(
                poolId, regattaId, blockId, "Pool", List.of(), 5, false
            );
        });
    }
    
    @Test
    void testCreateBibPoolWithNullExplicitListThrowsException() {
        UUID poolId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            BibPoolAggregate.createWithExplicitList(
                poolId, regattaId, blockId, "Pool", null, 5, false
            );
        });
    }
    
    @Test
    void testCreateBibPoolWithNegativePriorityThrowsException() {
        UUID poolId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            BibPoolAggregate.createWithRange(poolId, regattaId, blockId, "Pool", 1, 100, -1, false);
        });
    }
    
    @Test
    void testUpdateBibPoolRange() {
        UUID poolId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        
        BibPoolAggregate pool = BibPoolAggregate.createWithRange(
            poolId, regattaId, blockId, "Pool", 1, 100, 5, false
        );
        pool.markEventsAsCommitted();
        
        pool.updateRange("Updated Pool", 1, 150, 10);
        
        assertEquals(1, pool.getUncommittedEvents().size());
        BibPoolUpdatedEvent event = (BibPoolUpdatedEvent) pool.getUncommittedEvents().get(0);
        assertEquals("BibPoolUpdated", event.getEventType());
        assertEquals(poolId, event.getPoolId());
        assertEquals("Updated Pool", event.getName());
        assertEquals(1, event.getStartBib());
        assertEquals(150, event.getEndBib());
        assertEquals(10, event.getPriority());
    }
    
    @Test
    void testUpdateBibPoolExplicitList() {
        UUID poolId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        List<Integer> originalList = Arrays.asList(1, 2, 3);
        
        BibPoolAggregate pool = BibPoolAggregate.createWithExplicitList(
            poolId, regattaId, blockId, "Pool", originalList, 5, false
        );
        pool.markEventsAsCommitted();
        
        List<Integer> newList = Arrays.asList(4, 5, 6, 7);
        pool.updateExplicitList("Updated Pool", newList, 8);
        
        assertEquals(1, pool.getUncommittedEvents().size());
        BibPoolUpdatedEvent event = (BibPoolUpdatedEvent) pool.getUncommittedEvents().get(0);
        assertEquals("BibPoolUpdated", event.getEventType());
        assertEquals(newList, event.getBibNumbers());
    }
    
    @Test
    void testDeleteBibPool() {
        UUID poolId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        
        BibPoolAggregate pool = BibPoolAggregate.createWithRange(
            poolId, regattaId, blockId, "Pool", 1, 100, 5, false
        );
        pool.markEventsAsCommitted();
        
        pool.delete();
        
        assertEquals(1, pool.getUncommittedEvents().size());
        BibPoolDeletedEvent event = (BibPoolDeletedEvent) pool.getUncommittedEvents().get(0);
        assertEquals("BibPoolDeleted", event.getEventType());
        assertEquals(poolId, event.getPoolId());
    }
    
    @Test
    void testDeleteAlreadyDeletedPoolThrowsException() {
        UUID poolId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        
        BibPoolAggregate pool = BibPoolAggregate.createWithRange(
            poolId, regattaId, blockId, "Pool", 1, 100, 5, false
        );
        pool.markEventsAsCommitted();
        pool.delete();
        
        assertThrows(IllegalStateException.class, () -> {
            pool.delete();
        });
    }
    
    @Test
    void testUpdateDeletedPoolThrowsException() {
        UUID poolId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        
        BibPoolAggregate pool = BibPoolAggregate.createWithRange(
            poolId, regattaId, blockId, "Pool", 1, 100, 5, false
        );
        pool.markEventsAsCommitted();
        pool.delete();
        
        assertThrows(IllegalStateException.class, () -> {
            pool.updateRange("Updated", 1, 100, 5);
        });
    }
}
