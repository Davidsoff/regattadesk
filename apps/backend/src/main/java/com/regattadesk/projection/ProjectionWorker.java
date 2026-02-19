package com.regattadesk.projection;

import com.regattadesk.eventstore.EventEnvelope;
import com.regattadesk.eventstore.EventStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Worker that processes events and updates projections.
 * 
 * Provides idempotent replay support by tracking checkpoints.
 * Each event is processed exactly once per projection.
 */
@ApplicationScoped
public class ProjectionWorker {
    
    private static final Logger LOG = LoggerFactory.getLogger(ProjectionWorker.class);
    private static final int DEFAULT_BATCH_SIZE = 100;
    
    @Inject
    EventStore eventStore;
    
    @Inject
    ProjectionCheckpointRepository checkpointRepository;
    
    /**
     * Processes events for a single projection handler.
     * 
     * Reads events from the event store starting from the last checkpoint
     * and applies them to the projection handler in batches.
     * 
     * @param handler the projection handler
     * @return the number of events processed
     */
    public int processProjection(ProjectionHandler handler) {
        return processProjection(handler, DEFAULT_BATCH_SIZE);
    }
    
    /**
     * Processes events for a single projection handler with a custom batch size.
     * 
     * @param handler the projection handler
     * @param batchSize number of events to process per batch
     * @return the number of events processed
     */
    public int processProjection(ProjectionHandler handler, int batchSize) {
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be > 0");
        }
        
        String projectionName = handler.getProjectionName();
        LOG.debug("Processing projection: {}", projectionName);
        
        // Get the last checkpoint
        Optional<UUID> lastProcessedEventId = checkpointRepository.getLastProcessedEventId(projectionName);
        
        // Read events from the event store
        List<EventEnvelope> events = readEventsFromStore(lastProcessedEventId, batchSize);
        
        if (events.isEmpty()) {
            LOG.debug("No new events to process for projection: {}", projectionName);
            return 0;
        }
        
        // Process events
        int processed = 0;
        for (EventEnvelope event : events) {
            boolean handled = handler.canHandle(event);
            processEvent(handler, event, handled);
            if (handled) {
                processed++;
            }
        }
        
        LOG.debug("Processed {} events for projection: {}", processed, projectionName);
        return processed;
    }
    
    /**
     * Processes a single event within a transaction.
     * 
     * The transaction ensures that both the projection update and checkpoint
     * are saved atomically. If either fails, both are rolled back.
     */
    @Transactional
    protected void processEvent(ProjectionHandler handler, EventEnvelope event, boolean handled) {
        try {
            if (handled) {
                // Apply the event to the projection
                handler.handle(event);
            }
            
            // Save the checkpoint
            ProjectionCheckpoint checkpoint = new ProjectionCheckpoint(
                    handler.getProjectionName(),
                    event.getEventId(),
                    event.getCreatedAt()
            );
            checkpointRepository.saveCheckpoint(checkpoint);
            
            LOG.trace("Processed event {} for projection {} (handled={})",
                    event.getEventId(), handler.getProjectionName(), handled);
            
        } catch (Exception e) {
            LOG.error("Failed to process event {} for projection {}", 
                    event.getEventId(), handler.getProjectionName(), e);
            throw new RuntimeException("Failed to process event for projection " + handler.getProjectionName(), e);
        }
    }
    
    /**
     * Reads events from the event store.
     * 
     * If lastProcessedEventId is present, reads events after that event.
     * Otherwise, reads from the beginning.
     */
    private List<EventEnvelope> readEventsFromStore(Optional<UUID> lastProcessedEventId, int batchSize) {
        if (lastProcessedEventId.isPresent()) {
            // Read events after the last processed event
            // For now, we use readGlobal with offset 0, but in production
            // you'd want to track the global position more efficiently
            return readEventsAfter(lastProcessedEventId.get(), batchSize);
        } else {
            // Read from the beginning
            return eventStore.readGlobal(batchSize, 0);
        }
    }
    
    /**
     * Reads events after a specific event ID.
     * 
     * This is a simple implementation that reads globally and filters.
     * In production, you'd want to optimize this with proper indexing.
     * 
     * IMPORTANT: This implementation has scalability limitations:
     * - Maximum effective batch size is batchSize * 10
     * - May miss events if more than batchSize * 10 events exist beyond the checkpoint
     * - High memory usage for large event stores
     * 
     * TODO: Replace with cursor-based or sequence-number-based approach before production use
     */
    private List<EventEnvelope> readEventsAfter(UUID eventId, int batchSize) {
        int readLimit = batchSize * 10;
        List<EventEnvelope> allEvents = eventStore.readGlobal(readLimit, 0);
        
        // Find the position of the last processed event
        int position = -1;
        for (int i = 0; i < allEvents.size(); i++) {
            if (allEvents.get(i).getEventId().equals(eventId)) {
                position = i;
                break;
            }
        }
        
        // Return events after the last processed event
        if (position >= 0 && position < allEvents.size() - 1) {
            int endIndex = Math.min(position + 1 + batchSize, allEvents.size());
            List<EventEnvelope> result = allEvents.subList(position + 1, endIndex);
            
            // Warn if we're approaching the read limit
            if (allEvents.size() >= readLimit) {
                LOG.warn("Projection worker read limit reached ({}). Some events may not be processed. " +
                        "Consider implementing cursor-based event reading.", readLimit);
            }
            
            return result;
        }
        
        return List.of();
    }
}
