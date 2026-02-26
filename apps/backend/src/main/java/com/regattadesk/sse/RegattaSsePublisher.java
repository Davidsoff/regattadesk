package com.regattadesk.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;

/**
 * SSE publisher for per-regatta live update streams.
 * 
 * Manages broadcaster instances per regatta and handles:
 * - Snapshot events on connection
 * - Incremental revision events
 * - Heartbeat/keepalive ticks
 * - Per-client connection caps
 * - Deterministic event IDs
 * 
 * Implementation notes:
 * - One broadcaster per regatta (created on-demand)
 * - Broadcasters are never removed (acceptable for v0.1 scope)
 * - Connection cap enforcement at resource level
 * - Heartbeat comments sent every 15 seconds
 */
@ApplicationScoped
public class RegattaSsePublisher {
    
    private static final Logger LOG = Logger.getLogger(RegattaSsePublisher.class);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);
    
    @Inject
    ObjectMapper objectMapper;
    
    // Per-regatta broadcasters
    private final Map<UUID, BroadcastProcessor<String>> broadcasters = new ConcurrentHashMap<>();
    
    // Per-regatta event sequence counters
    private final Map<UUID, AtomicInteger> sequenceCounters = new ConcurrentHashMap<>();
    
    /**
     * Gets or creates a Multi stream for a regatta.
     * Includes heartbeat ticks to keep connections alive.
     * 
     * @param regattaId the regatta UUID
     * @return Multi stream of SSE-formatted strings
     */
    public Multi<String> getStream(UUID regattaId) {
        return getStream(regattaId, true);
    }
    
    /**
     * Gets or creates a Multi stream for a regatta.
     * 
     * @param regattaId the regatta UUID
     * @param includeHeartbeat whether to include heartbeat ticks
     * @return Multi stream of SSE-formatted strings
     */
    Multi<String> getStream(UUID regattaId, boolean includeHeartbeat) {
        BroadcastProcessor<String> broadcaster = broadcasters.computeIfAbsent(regattaId, id -> {
            LOG.infof("Creating new SSE broadcaster for regatta %s", id);
            return BroadcastProcessor.create();
        });
        
        // Get the broadcast stream
        Multi<String> eventStream = Multi.createFrom().publisher(broadcaster);
        
        if (!includeHeartbeat) {
            return eventStream;
        }
        
        // Add heartbeat
        Multi<String> heartbeat = Multi.createFrom().ticks().every(HEARTBEAT_INTERVAL)
            .map(tick -> ":keepalive\n\n");
        
        // Merge event stream with heartbeat
        return Multi.createBy().merging().streams(eventStream, heartbeat);
    }
    
    /**
     * Broadcasts a snapshot event to all clients connected to a regatta stream.
     * 
     * @param regattaId the regatta UUID
     * @param drawRevision current draw revision
     * @param resultsRevision current results revision
     */
    public void broadcastSnapshot(UUID regattaId, int drawRevision, int resultsRevision) {
        SseEvent event = new SseEvent(drawRevision, resultsRevision);
        broadcast(regattaId, SseEventType.SNAPSHOT, event, drawRevision, resultsRevision);
    }
    
    /**
     * Broadcasts a draw revision change event.
     * 
     * @param regattaId the regatta UUID
     * @param newDrawRevision new draw revision
     * @param resultsRevision current results revision
     * @param reason optional reason for the change
     */
    public void broadcastDrawRevision(UUID regattaId, int newDrawRevision, int resultsRevision, String reason) {
        SseEvent event = new SseEvent(newDrawRevision, resultsRevision, reason);
        broadcast(regattaId, SseEventType.DRAW_REVISION, event, newDrawRevision, resultsRevision);
    }
    
    /**
     * Broadcasts a results revision change event.
     * 
     * @param regattaId the regatta UUID
     * @param drawRevision current draw revision
     * @param newResultsRevision new results revision
     * @param reason optional reason for the change
     */
    public void broadcastResultsRevision(UUID regattaId, int drawRevision, int newResultsRevision, String reason) {
        SseEvent event = new SseEvent(drawRevision, newResultsRevision, reason);
        broadcast(regattaId, SseEventType.RESULTS_REVISION, event, drawRevision, newResultsRevision);
    }
    
    /**
     * Internal broadcast method that formats and sends SSE events.
     */
    private void broadcast(UUID regattaId, SseEventType eventType, SseEvent event, 
                          int drawRevision, int resultsRevision) {
        BroadcastProcessor<String> broadcaster = broadcasters.get(regattaId);
        if (broadcaster == null) {
            LOG.warnf("No broadcaster found for regatta %s, event will be lost", regattaId);
            return;
        }
        
        try {
            // Get next sequence number
            AtomicInteger counter = sequenceCounters.computeIfAbsent(regattaId, id -> new AtomicInteger(0));
            int sequence = counter.getAndIncrement();
            
            // Generate deterministic event ID
            String eventId = SseEventIdGenerator.generate(regattaId, drawRevision, resultsRevision, sequence);
            
            // Serialize event payload
            String data = objectMapper.writeValueAsString(event);
            
            // Format SSE message
            String sseMessage = formatSseMessage(eventId, eventType.getEventName(), data);
            
            // Broadcast to all connected clients
            broadcaster.onNext(sseMessage);
            
            LOG.debugf("Broadcasted %s event for regatta %s with id %s", 
                      eventType.getEventName(), regattaId, eventId);
            
        } catch (Exception e) {
            LOG.errorf(e, "Failed to broadcast %s event for regatta %s", eventType.getEventName(), regattaId);
        }
    }
    
    /**
     * Formats an SSE message according to the SSE specification.
     * 
     * Format:
     * id: {eventId}
     * event: {eventType}
     * data: {jsonData}
     * 
     * (blank line)
     */
    private String formatSseMessage(String eventId, String eventType, String data) {
        return String.format("id: %s\nevent: %s\ndata: %s\n\n", eventId, eventType, data);
    }
    
    /**
     * Resets the sequence counter for a regatta.
     * Used primarily for testing.
     * 
     * @param regattaId the regatta UUID
     */
    void resetSequenceCounter(UUID regattaId) {
        sequenceCounters.remove(regattaId);
    }
    
    /**
     * Gets the current sequence counter value for a regatta.
     * Used primarily for testing.
     * 
     * @param regattaId the regatta UUID
     * @return current sequence value, or 0 if not initialized
     */
    int getSequenceCounter(UUID regattaId) {
        AtomicInteger counter = sequenceCounters.get(regattaId);
        return counter != null ? counter.get() : 0;
    }
}
