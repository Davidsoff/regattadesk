package com.regattadesk.draw;

import java.util.*;

/**
 * Service for generating deterministic random draws with bib assignments.
 * 
 * Uses seeded random number generation to ensure reproducibility.
 */
public class DrawGenerator {
    
    /**
     * Generates a draw with bib assignments for the given entries.
     * 
     * @param regattaId the regatta ID
     * @param entryIds the list of entry IDs to assign bibs to
     * @param seed the random seed for deterministic generation
     * @param bibAssignmentDirection "smallest" or "largest" - direction for bib assignment
     * @return DrawResult containing the seed and bib assignments
     */
    public DrawResult generateDraw(
            UUID regattaId,
            List<UUID> entryIds,
            long seed,
            String bibAssignmentDirection
    ) {
        validateInputs(regattaId, entryIds, bibAssignmentDirection);
        
        // Create a seeded random for deterministic shuffling
        Random random = new Random(seed);
        
        // Shuffle the entry IDs to create random draw order
        List<UUID> shuffled = new ArrayList<>(entryIds);
        Collections.shuffle(shuffled, random);
        
        // Assign bibs based on direction
        Map<UUID, Integer> bibAssignments = new HashMap<>();
        int entryCount = shuffled.size();
        
        if ("smallest".equals(bibAssignmentDirection)) {
            // Assign bibs starting from 1
            for (int i = 0; i < entryCount; i++) {
                bibAssignments.put(shuffled.get(i), i + 1);
            }
        } else if ("largest".equals(bibAssignmentDirection)) {
            // Assign bibs starting from the largest
            for (int i = 0; i < entryCount; i++) {
                bibAssignments.put(shuffled.get(i), entryCount - i);
            }
        } else {
            throw new IllegalArgumentException("Invalid bib assignment direction: " + bibAssignmentDirection);
        }
        
        return new DrawResult(seed, bibAssignments);
    }
    
    private void validateInputs(UUID regattaId, List<UUID> entryIds, String bibAssignmentDirection) {
        if (regattaId == null) {
            throw new IllegalArgumentException("Regatta ID cannot be null");
        }
        if (entryIds == null) {
            throw new IllegalArgumentException("Entry IDs list cannot be null");
        }
        if (entryIds.isEmpty()) {
            throw new IllegalArgumentException("Entry IDs list cannot be empty");
        }
        if (bibAssignmentDirection == null) {
            throw new IllegalArgumentException("Bib assignment direction cannot be null");
        }
        if (!bibAssignmentDirection.equals("smallest") && !bibAssignmentDirection.equals("largest")) {
            throw new IllegalArgumentException("Bib assignment direction must be 'smallest' or 'largest'");
        }
    }
}
