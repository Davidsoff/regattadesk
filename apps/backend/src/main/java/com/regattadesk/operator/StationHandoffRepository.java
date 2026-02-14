package com.regattadesk.operator;

import com.regattadesk.eventstore.DomainEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for station handoff persistence and retrieval.
 */
public interface StationHandoffRepository {
    
    /**
     * Saves a station handoff.
     * 
     * @param handoff the handoff to save
     * @return the saved handoff
     */
    StationHandoff save(StationHandoff handoff);
    
    /**
     * Updates a station handoff.
     * 
     * @param handoff the handoff to update
     * @return the updated handoff
     */
    StationHandoff update(StationHandoff handoff);
    
    /**
     * Finds a station handoff by its ID.
     * 
     * @param id the handoff ID
     * @return the handoff if found
     */
    Optional<StationHandoff> findById(UUID id);
    
    /**
     * Finds pending handoffs for a specific station.
     * 
     * @param regattaId the regatta ID
     * @param station the station identifier
     * @return list of pending handoffs
     */
    List<StationHandoff> findPendingByRegattaAndStation(UUID regattaId, String station);
    
    /**
     * Finds pending handoffs for a specific token.
     * 
     * @param tokenId the token ID
     * @return list of pending handoffs
     */
    List<StationHandoff> findPendingByToken(UUID tokenId);
    
    /**
     * Appends a domain event to the event store.
     * 
     * @param event the event to append
     */
    void appendEvent(DomainEvent event);
}
