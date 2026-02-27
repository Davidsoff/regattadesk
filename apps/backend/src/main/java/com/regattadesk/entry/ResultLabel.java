package com.regattadesk.entry;

/**
 * Result label enum (BC07-002).
 * 
 * Represents the label applied to an entry result based on its approval
 * and modification status.
 */
public enum ResultLabel {
    /**
     * Result is computed but not event-approved.
     */
    PROVISIONAL,
    
    /**
     * Manual adjustment or penalty applied (still provisional until approval).
     */
    EDITED,
    
    /**
     * Event approved - result is official.
     */
    OFFICIAL
}
