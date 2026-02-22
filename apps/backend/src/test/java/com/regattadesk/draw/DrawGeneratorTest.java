package com.regattadesk.draw;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DrawGenerator service.
 * 
 * Tests verify:
 * - Deterministic draw generation using seeded random
 * - Draw reproducibility with same seed
 * - Different results with different seeds
 * - Bib assignment with smallest/largest direction
 */
class DrawGeneratorTest {
    
    @Test
    void testGenerateDrawWithSeed() {
        UUID regattaId = UUID.randomUUID();
        long seed = 12345L;
        List<UUID> entryIds = Arrays.asList(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        DrawGenerator generator = new DrawGenerator();
        DrawResult result = generator.generateDraw(regattaId, entryIds, seed, "smallest");
        
        assertNotNull(result);
        assertEquals(seed, result.getSeed());
        assertEquals(entryIds.size(), result.getBibAssignments().size());
        
        // Verify all entries got bib assignments
        for (UUID entryId : entryIds) {
            assertTrue(result.getBibAssignments().containsKey(entryId));
        }
    }
    
    @Test
    void testDrawReproducibilityWithSameSeed() {
        UUID regattaId = UUID.randomUUID();
        long seed = 98765L;
        List<UUID> entryIds = Arrays.asList(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        DrawGenerator generator = new DrawGenerator();
        
        // Generate draw twice with same seed
        DrawResult result1 = generator.generateDraw(regattaId, entryIds, seed, "smallest");
        DrawResult result2 = generator.generateDraw(regattaId, entryIds, seed, "smallest");
        
        // Results should be identical
        assertEquals(result1.getBibAssignments(), result2.getBibAssignments());
    }
    
    @Test
    void testDrawDifferenceWithDifferentSeeds() {
        UUID regattaId = UUID.randomUUID();
        long seed1 = 111L;
        long seed2 = 222L;
        List<UUID> entryIds = Arrays.asList(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        DrawGenerator generator = new DrawGenerator();
        
        // Generate draw with different seeds
        DrawResult result1 = generator.generateDraw(regattaId, entryIds, seed1, "smallest");
        DrawResult result2 = generator.generateDraw(regattaId, entryIds, seed2, "smallest");
        
        // Results should be different (with high probability)
        assertNotEquals(result1.getBibAssignments(), result2.getBibAssignments());
    }
    
    @Test
    void testBibAssignmentWithSmallestDirection() {
        UUID regattaId = UUID.randomUUID();
        long seed = 42L;
        List<UUID> entryIds = Arrays.asList(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        DrawGenerator generator = new DrawGenerator();
        DrawResult result = generator.generateDraw(regattaId, entryIds, seed, "smallest");
        
        // Get all assigned bibs
        List<Integer> assignedBibs = result.getBibAssignments().values().stream()
            .sorted()
            .toList();
        
        // With "smallest" direction, bibs should start from 1
        assertEquals(1, assignedBibs.get(0));
        assertEquals(2, assignedBibs.get(1));
        assertEquals(3, assignedBibs.get(2));
    }
    
    @Test
    void testBibAssignmentWithLargestDirection() {
        UUID regattaId = UUID.randomUUID();
        long seed = 42L;
        List<UUID> entryIds = Arrays.asList(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        DrawGenerator generator = new DrawGenerator();
        DrawResult result = generator.generateDraw(regattaId, entryIds, seed, "largest");
        
        // Get all assigned bibs in descending order
        List<Integer> assignedBibs = result.getBibAssignments().values().stream()
            .sorted((a, b) -> b.compareTo(a))
            .toList();
        
        // With "largest" direction, higher bibs should be used first
        // The actual values depend on the number of entries but should be descending
        assertTrue(assignedBibs.get(0) > assignedBibs.get(1));
        assertTrue(assignedBibs.get(1) > assignedBibs.get(2));
    }
    
    @Test
    void testGenerateDrawWithEmptyEntriesListThrowsException() {
        UUID regattaId = UUID.randomUUID();
        long seed = 123L;
        
        DrawGenerator generator = new DrawGenerator();
        
        assertThrows(IllegalArgumentException.class, () -> {
            generator.generateDraw(regattaId, List.of(), seed, "smallest");
        });
    }
    
    @Test
    void testGenerateDrawWithNullEntriesListThrowsException() {
        UUID regattaId = UUID.randomUUID();
        long seed = 123L;
        
        DrawGenerator generator = new DrawGenerator();
        
        assertThrows(IllegalArgumentException.class, () -> {
            generator.generateDraw(regattaId, null, seed, "smallest");
        });
    }
    
    @Test
    void testGenerateDrawWithNullRegattaIdThrowsException() {
        long seed = 123L;
        List<UUID> entryIds = Arrays.asList(UUID.randomUUID());
        
        DrawGenerator generator = new DrawGenerator();
        
        assertThrows(IllegalArgumentException.class, () -> {
            generator.generateDraw(null, entryIds, seed, "smallest");
        });
    }
    
    @Test
    void testGenerateDrawWithInvalidDirectionThrowsException() {
        UUID regattaId = UUID.randomUUID();
        long seed = 123L;
        List<UUID> entryIds = Arrays.asList(UUID.randomUUID());
        
        DrawGenerator generator = new DrawGenerator();
        
        assertThrows(IllegalArgumentException.class, () -> {
            generator.generateDraw(regattaId, entryIds, seed, "invalid");
        });
    }
}
