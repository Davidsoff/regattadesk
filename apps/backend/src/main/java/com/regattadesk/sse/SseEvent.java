package com.regattadesk.sse;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * SSE event payload for regatta live updates.
 * 
 * Payload structure as per BC05-003 specification:
 * {
 *   "draw_revision": int,
 *   "results_revision": int,
 *   "reason": string (optional)
 * }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SseEvent {
    
    @JsonProperty("draw_revision")
    private final int drawRevision;
    
    @JsonProperty("results_revision")
    private final int resultsRevision;
    
    @JsonProperty("reason")
    private final String reason;
    
    /**
     * Creates an SSE event with optional reason.
     */
    @JsonCreator
    public SseEvent(
            @JsonProperty("draw_revision") int drawRevision,
            @JsonProperty("results_revision") int resultsRevision,
            @JsonProperty("reason") String reason) {
        this.drawRevision = drawRevision;
        this.resultsRevision = resultsRevision;
        this.reason = reason;
    }
    
    /**
     * Creates an SSE event without reason.
     */
    public SseEvent(int drawRevision, int resultsRevision) {
        this(drawRevision, resultsRevision, null);
    }
    
    public int getDrawRevision() {
        return drawRevision;
    }
    
    public int getResultsRevision() {
        return resultsRevision;
    }
    
    public String getReason() {
        return reason;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SseEvent sseEvent = (SseEvent) o;
        return drawRevision == sseEvent.drawRevision &&
               resultsRevision == sseEvent.resultsRevision &&
               Objects.equals(reason, sseEvent.reason);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(drawRevision, resultsRevision, reason);
    }
    
    @Override
    public String toString() {
        return "SseEvent{" +
               "drawRevision=" + drawRevision +
               ", resultsRevision=" + resultsRevision +
               ", reason='" + reason + '\'' +
               '}';
    }
}
