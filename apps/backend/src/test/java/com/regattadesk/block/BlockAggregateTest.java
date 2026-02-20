package com.regattadesk.block;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BlockAggregate.
 * 
 * Tests verify:
 * - Block creation with scheduling parameters
 * - Block update with new intervals
 * - Block deletion
 * - Validation of scheduling constraints
 */
class BlockAggregateTest {
    
    @Test
    void testCreateBlock() {
        UUID blockId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        String name = "Morning Block";
        Instant startTime = Instant.parse("2026-06-15T08:00:00Z");
        int eventIntervalSeconds = 300; // 5 minutes
        int crewIntervalSeconds = 60; // 1 minute
        int displayOrder = 1;
        
        BlockAggregate block = BlockAggregate.create(
            blockId,
            regattaId,
            name,
            startTime,
            eventIntervalSeconds,
            crewIntervalSeconds,
            displayOrder
        );
        
        assertEquals(blockId, block.getId());
        assertEquals(1, block.getUncommittedEvents().size());
        
        BlockCreatedEvent event = (BlockCreatedEvent) block.getUncommittedEvents().get(0);
        assertEquals("BlockCreated", event.getEventType());
        assertEquals(blockId, event.getBlockId());
        assertEquals(regattaId, event.getRegattaId());
        assertEquals(name, event.getName());
        assertEquals(startTime, event.getStartTime());
        assertEquals(eventIntervalSeconds, event.getEventIntervalSeconds());
        assertEquals(crewIntervalSeconds, event.getCrewIntervalSeconds());
        assertEquals(displayOrder, event.getDisplayOrder());
    }
    
    @Test
    void testCreateBlockWithNullNameThrowsException() {
        UUID blockId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        Instant startTime = Instant.parse("2026-06-15T08:00:00Z");
        
        assertThrows(IllegalArgumentException.class, () -> {
            BlockAggregate.create(blockId, regattaId, null, startTime, 300, 60, 1);
        });
    }
    
    @Test
    void testCreateBlockWithBlankNameThrowsException() {
        UUID blockId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        Instant startTime = Instant.parse("2026-06-15T08:00:00Z");
        
        assertThrows(IllegalArgumentException.class, () -> {
            BlockAggregate.create(blockId, regattaId, "  ", startTime, 300, 60, 1);
        });
    }
    
    @Test
    void testCreateBlockWithNullRegattaIdThrowsException() {
        UUID blockId = UUID.randomUUID();
        Instant startTime = Instant.parse("2026-06-15T08:00:00Z");
        
        assertThrows(IllegalArgumentException.class, () -> {
            BlockAggregate.create(blockId, null, "Block", startTime, 300, 60, 1);
        });
    }
    
    @Test
    void testCreateBlockWithNullStartTimeThrowsException() {
        UUID blockId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            BlockAggregate.create(blockId, regattaId, "Block", null, 300, 60, 1);
        });
    }
    
    @Test
    void testCreateBlockWithNegativeEventIntervalThrowsException() {
        UUID blockId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        Instant startTime = Instant.parse("2026-06-15T08:00:00Z");
        
        assertThrows(IllegalArgumentException.class, () -> {
            BlockAggregate.create(blockId, regattaId, "Block", startTime, -1, 60, 1);
        });
    }
    
    @Test
    void testCreateBlockWithNegativeCrewIntervalThrowsException() {
        UUID blockId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        Instant startTime = Instant.parse("2026-06-15T08:00:00Z");
        
        assertThrows(IllegalArgumentException.class, () -> {
            BlockAggregate.create(blockId, regattaId, "Block", startTime, 300, -1, 1);
        });
    }
    
    @Test
    void testUpdateBlock() {
        UUID blockId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        Instant startTime = Instant.parse("2026-06-15T08:00:00Z");
        
        BlockAggregate block = BlockAggregate.create(
            blockId, regattaId, "Block", startTime, 300, 60, 1
        );
        block.markEventsAsCommitted();
        
        Instant newStartTime = Instant.parse("2026-06-15T09:00:00Z");
        block.update("Updated Block", newStartTime, 600, 90, 2);
        
        assertEquals(1, block.getUncommittedEvents().size());
        BlockUpdatedEvent event = (BlockUpdatedEvent) block.getUncommittedEvents().get(0);
        assertEquals("BlockUpdated", event.getEventType());
        assertEquals(blockId, event.getBlockId());
        assertEquals("Updated Block", event.getName());
        assertEquals(newStartTime, event.getStartTime());
        assertEquals(600, event.getEventIntervalSeconds());
        assertEquals(90, event.getCrewIntervalSeconds());
        assertEquals(2, event.getDisplayOrder());
    }
    
    @Test
    void testUpdateBlockWithNullNameThrowsException() {
        UUID blockId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        Instant startTime = Instant.parse("2026-06-15T08:00:00Z");
        
        BlockAggregate block = BlockAggregate.create(
            blockId, regattaId, "Block", startTime, 300, 60, 1
        );
        
        assertThrows(IllegalArgumentException.class, () -> {
            block.update(null, startTime, 300, 60, 1);
        });
    }
    
    @Test
    void testDeleteBlock() {
        UUID blockId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        Instant startTime = Instant.parse("2026-06-15T08:00:00Z");
        
        BlockAggregate block = BlockAggregate.create(
            blockId, regattaId, "Block", startTime, 300, 60, 1
        );
        block.markEventsAsCommitted();
        
        block.delete();
        
        assertEquals(1, block.getUncommittedEvents().size());
        BlockDeletedEvent event = (BlockDeletedEvent) block.getUncommittedEvents().get(0);
        assertEquals("BlockDeleted", event.getEventType());
        assertEquals(blockId, event.getBlockId());
    }
    
    @Test
    void testDeleteAlreadyDeletedBlockThrowsException() {
        UUID blockId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        Instant startTime = Instant.parse("2026-06-15T08:00:00Z");
        
        BlockAggregate block = BlockAggregate.create(
            blockId, regattaId, "Block", startTime, 300, 60, 1
        );
        block.markEventsAsCommitted();
        block.delete();
        
        assertThrows(IllegalStateException.class, () -> {
            block.delete();
        });
    }
    
    @Test
    void testUpdateDeletedBlockThrowsException() {
        UUID blockId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        Instant startTime = Instant.parse("2026-06-15T08:00:00Z");
        
        BlockAggregate block = BlockAggregate.create(
            blockId, regattaId, "Block", startTime, 300, 60, 1
        );
        block.markEventsAsCommitted();
        block.delete();
        
        assertThrows(IllegalStateException.class, () -> {
            block.update("Updated", startTime, 300, 60, 1);
        });
    }
}
