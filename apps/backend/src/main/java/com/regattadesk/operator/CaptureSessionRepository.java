package com.regattadesk.operator;

import com.regattadesk.eventstore.DomainEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for capture session persistence and retrieval.
 */
public interface CaptureSessionRepository {

    /**
     * Persists a new capture session.
     *
     * @param session the session to save
     * @return the saved session
     */
    CaptureSession save(CaptureSession session);

    /**
     * Updates an existing capture session (sync state or close).
     *
     * @param session the session to update
     * @return the updated session
     */
    CaptureSession update(CaptureSession session);

    /**
     * Finds a capture session by its primary key.
     *
     * @param id the session ID
     * @return the session if found
     */
    Optional<CaptureSession> findById(UUID id);

    /**
     * Lists all capture sessions for a regatta, ordered by creation time descending.
     *
     * @param regattaId the regatta ID
     * @return list of sessions
     */
    List<CaptureSession> findByRegattaId(UUID regattaId);

    /**
     * Lists capture sessions for a specific block within a regatta.
     *
     * @param regattaId the regatta ID
     * @param blockId   the block ID
     * @return list of sessions
     */
    List<CaptureSession> findByRegattaAndBlock(UUID regattaId, UUID blockId);

    /**
     * Lists open sessions for a regatta, optionally filtered by station.
     *
     * @param regattaId the regatta ID
     * @param station   optional station filter (may be {@code null})
     * @return list of open sessions
     */
    List<CaptureSession> findOpenByRegattaId(UUID regattaId, String station);

    /**
     * Appends a domain event to the event store.
     *
     * @param event the event to append
     */
    void appendEvent(DomainEvent event);
}
