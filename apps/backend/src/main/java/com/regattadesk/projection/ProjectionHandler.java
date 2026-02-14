package com.regattadesk.projection;

import com.regattadesk.eventstore.EventEnvelope;

/**
 * Interface for projection handlers that transform events into read models.
 * 
 * Projection handlers are invoked by the projection worker for each event.
 * They are responsible for updating read-model tables based on domain events.
 */
public interface ProjectionHandler {
    
    /**
     * Returns the name of this projection handler.
     * Used for checkpointing and logging.
     */
    String getProjectionName();
    
    /**
     * Determines if this handler should process the given event.
     * 
     * @param event the event to check
     * @return true if the handler should process this event
     */
    boolean canHandle(EventEnvelope event);
    
    /**
     * Handles an event and updates the read model.
     * 
     * This method is called within a transaction boundary.
     * If an exception is thrown, the transaction will be rolled back
     * and the checkpoint will not be updated.
     * 
     * @param event the event to handle
     */
    void handle(EventEnvelope event);
}
