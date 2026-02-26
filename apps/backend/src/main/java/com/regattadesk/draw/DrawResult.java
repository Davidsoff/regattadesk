package com.regattadesk.draw;

import java.util.Map;
import java.util.UUID;

/**
 * Result of a draw generation operation.
 * Contains the seed used and the bib assignments generated.
 */
public class DrawResult {
    private final long seed;
    private final Map<UUID, Integer> bibAssignments;
    
    public DrawResult(long seed, Map<UUID, Integer> bibAssignments) {
        this.seed = seed;
        this.bibAssignments = bibAssignments;
    }
    
    public long getSeed() {
        return seed;
    }
    
    public Map<UUID, Integer> getBibAssignments() {
        return bibAssignments;
    }
}
