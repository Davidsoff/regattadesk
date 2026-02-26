package com.regattadesk.sse;

/**
 * SSE event types for regatta live updates.
 * 
 * Event types as per BC05-003 specification:
 * - snapshot: Initial state snapshot on connection
 * - draw_revision: Draw revision changed (schedule/bib assignments updated)
 * - results_revision: Results revision changed (times/penalties/approvals updated)
 */
public enum SseEventType {
    /**
     * Initial snapshot event sent when client connects.
     * Contains current draw and results revision.
     */
    SNAPSHOT("snapshot"),
    
    /**
     * Draw revision changed event.
     * Sent when draw is published or schedule/bib assignments change.
     */
    DRAW_REVISION("draw_revision"),
    
    /**
     * Results revision changed event.
     * Sent when results, times, penalties, or approvals change.
     */
    RESULTS_REVISION("results_revision");
    
    private final String eventName;
    
    SseEventType(String eventName) {
        this.eventName = eventName;
    }
    
    public String getEventName() {
        return eventName;
    }
}
