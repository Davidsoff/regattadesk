package com.regattadesk.sse;

import java.util.UUID;

/**
 * Generates deterministic SSE event IDs tied to revision sequence.
 * 
 * Event ID format: {regatta_id}:{draw_revision}:{results_revision}:{sequence}
 * 
 * This format ensures:
 * - IDs are deterministic and can be reproduced
 * - IDs are monotonically increasing within a regatta
 * - Clients can use Last-Event-ID for resume/replay
 * - Event ordering is preserved
 */
public class SseEventIdGenerator {
    
    /**
     * Generates an event ID for a regatta revision state.
     * 
     * @param regattaId the regatta UUID
     * @param drawRevision current draw revision
     * @param resultsRevision current results revision
     * @param sequence sequence number for this revision state (0 for snapshot, 1+ for updates)
     * @return deterministic event ID string
     */
    public static String generate(UUID regattaId, int drawRevision, int resultsRevision, int sequence) {
        if (regattaId == null) {
            throw new IllegalArgumentException("Regatta ID cannot be null");
        }
        if (drawRevision < 0) {
            throw new IllegalArgumentException("Draw revision cannot be negative");
        }
        if (resultsRevision < 0) {
            throw new IllegalArgumentException("Results revision cannot be negative");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("Sequence cannot be negative");
        }
        
        return String.format("%s:%d:%d:%d", regattaId, drawRevision, resultsRevision, sequence);
    }
    
    /**
     * Parses an event ID to extract regatta ID.
     * 
     * @param eventId the event ID string
     * @return the regatta UUID, or null if eventId is invalid
     */
    public static UUID parseRegattaId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return null;
        }
        
        String[] parts = eventId.split(":");
        if (parts.length != 4) {
            return null;
        }
        
        try {
            return UUID.fromString(parts[0]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * Parses an event ID to extract draw revision.
     * 
     * @param eventId the event ID string
     * @return the draw revision, or -1 if eventId is invalid
     */
    public static int parseDrawRevision(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return -1;
        }
        
        String[] parts = eventId.split(":");
        if (parts.length != 4) {
            return -1;
        }
        
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
    
    /**
     * Parses an event ID to extract results revision.
     * 
     * @param eventId the event ID string
     * @return the results revision, or -1 if eventId is invalid
     */
    public static int parseResultsRevision(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return -1;
        }
        
        String[] parts = eventId.split(":");
        if (parts.length != 4) {
            return -1;
        }
        
        try {
            return Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
    
    /**
     * Parses an event ID to extract sequence number.
     * 
     * @param eventId the event ID string
     * @return the sequence number, or -1 if eventId is invalid
     */
    public static int parseSequence(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return -1;
        }
        
        String[] parts = eventId.split(":");
        if (parts.length != 4) {
            return -1;
        }
        
        try {
            return Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
