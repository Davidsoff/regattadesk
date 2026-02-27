package com.regattadesk.investigation;

/**
 * Investigation outcome enum (BC07-001).
 * 
 * Represents the possible outcomes of an investigation.
 */
public enum InvestigationOutcome {
    /**
     * Investigation closed with no action taken.
     */
    NO_ACTION,
    
    /**
     * Investigation resulted in penalty seconds applied to entry.
     */
    PENALTY,
    
    /**
     * Entry was excluded from the race.
     */
    EXCLUDED,
    
    /**
     * Entry was disqualified (DSQ).
     */
    DSQ
}
