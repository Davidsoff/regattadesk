package com.regattadesk.operator;

import com.regattadesk.operator.events.CaptureSessionClosedEvent;
import com.regattadesk.operator.events.CaptureSessionStartedEvent;
import com.regattadesk.operator.events.CaptureSessionSyncStateUpdatedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for capture session lifecycle management.
 *
 * <p>Provides operations for starting, querying, updating sync state, and
 * closing capture sessions. Each mutating operation emits a domain event
 * to the event store for audit purposes.
 */
@ApplicationScoped
public class CaptureSessionService {

    private final CaptureSessionRepository repository;

    @Inject
    public CaptureSessionService(CaptureSessionRepository repository) {
        this.repository = repository;
    }

    /**
     * Starts a new capture session.
     *
     * @param regattaId              regatta scope
     * @param blockId                block scope
     * @param station                station identifier
     * @param deviceId               device identifier
     * @param sessionType            {@code start} or {@code finish}
     * @param serverTimeAtStart      server-side timestamp at session start
     * @param deviceMonotonicOffsetMs optional clock-offset in milliseconds
     * @param fps                    frames per second (must be &gt; 0)
     * @param actor                  identifier of the actor starting the session
     * @return the persisted {@link CaptureSession}
     * @throws IllegalArgumentException if required parameters are null/invalid
     */
    public CaptureSession startSession(
            UUID regattaId,
            UUID blockId,
            String station,
            String deviceId,
            CaptureSession.SessionType sessionType,
            Instant serverTimeAtStart,
            Long deviceMonotonicOffsetMs,
            int fps,
            String actor) {

        if (regattaId == null) throw new IllegalArgumentException("regattaId cannot be null");
        if (blockId == null) throw new IllegalArgumentException("blockId cannot be null");
        if (station == null || station.isBlank()) throw new IllegalArgumentException("station cannot be null or blank");
        if (deviceId == null || deviceId.isBlank()) throw new IllegalArgumentException("deviceId cannot be null or blank");
        if (sessionType == null) throw new IllegalArgumentException("sessionType cannot be null");
        if (serverTimeAtStart == null) serverTimeAtStart = Instant.now();
        if (fps <= 0) throw new IllegalArgumentException("fps must be positive");

        Instant now = Instant.now();

        CaptureSession session = new CaptureSession(
                UUID.randomUUID(),
                regattaId,
                blockId,
                station,
                deviceId,
                sessionType,
                CaptureSession.SessionState.open,
                serverTimeAtStart,
                deviceMonotonicOffsetMs,
                fps,
                true,
                false,
                null,
                null,
                null,
                now,
                now,
                null,
                null
        );

        CaptureSession saved = repository.save(session);

        repository.appendEvent(new CaptureSessionStartedEvent(
                saved.getId(),
                saved.getRegattaId(),
                saved.getBlockId(),
                saved.getStation(),
                saved.getDeviceId(),
                saved.getSessionType().name(),
                saved.getFps(),
                now,
                actor != null ? actor : "operator"
        ));

        return saved;
    }

    /**
     * Returns the capture session with the given ID if it belongs to the
     * specified regatta, or empty if not found / wrong regatta.
     *
     * @param sessionId the session ID
     * @param regattaId the regatta scope (used for ownership check)
     * @return the session if found and scoped correctly
     */
    public Optional<CaptureSession> getSession(UUID sessionId, UUID regattaId) {
        if (sessionId == null) throw new IllegalArgumentException("sessionId cannot be null");
        if (regattaId == null) throw new IllegalArgumentException("regattaId cannot be null");

        return repository.findById(sessionId)
                .filter(s -> s.getRegattaId().equals(regattaId));
    }

    /**
     * Lists all capture sessions for a regatta.
     *
     * @param regattaId the regatta scope
     * @return list of sessions ordered by creation time descending
     */
    public List<CaptureSession> listSessions(UUID regattaId) {
        if (regattaId == null) throw new IllegalArgumentException("regattaId cannot be null");
        return repository.findByRegattaId(regattaId);
    }

    /**
     * Lists capture sessions for a specific block within a regatta.
     *
     * @param regattaId the regatta scope
     * @param blockId   the block scope
     * @return list of sessions
     */
    public List<CaptureSession> listSessionsByBlock(UUID regattaId, UUID blockId) {
        if (regattaId == null) throw new IllegalArgumentException("regattaId cannot be null");
        if (blockId == null) throw new IllegalArgumentException("blockId cannot be null");
        return repository.findByRegattaAndBlock(regattaId, blockId);
    }

    /**
     * Lists open capture sessions for a regatta, optionally filtered by station.
     *
     * @param regattaId the regatta scope
     * @param station   optional station filter
     * @return list of open sessions
     */
    public List<CaptureSession> listOpenSessions(UUID regattaId, String station) {
        if (regattaId == null) throw new IllegalArgumentException("regattaId cannot be null");
        return repository.findOpenByRegattaId(regattaId, station);
    }

    /**
     * Updates the sync state of an open capture session.
     *
     * @param sessionId              the session ID
     * @param regattaId              the regatta scope for ownership check
     * @param isSynced               whether the device is currently synced
     * @param driftExceededThreshold whether clock drift exceeded the configured threshold
     * @param unsyncedReason         diagnostic reason when not synced (may be {@code null})
     * @param actor                  identifier of the actor performing the update
     * @return the updated {@link CaptureSession}
     * @throws IllegalStateException    if the session is already closed
     * @throws CaptureSessionNotFoundException if not found in this regatta
     */
    public CaptureSession updateSyncState(
            UUID sessionId,
            UUID regattaId,
            boolean isSynced,
            boolean driftExceededThreshold,
            String unsyncedReason,
            String actor) {

        CaptureSession existing = requireSession(sessionId, regattaId);

        Instant now = Instant.now();
        CaptureSession updated = existing.withSyncState(isSynced, driftExceededThreshold, unsyncedReason, now);

        CaptureSession saved = repository.update(updated);

        repository.appendEvent(new CaptureSessionSyncStateUpdatedEvent(
                saved.getId(),
                saved.getRegattaId(),
                saved.isSynced(),
                saved.isDriftExceededThreshold(),
                saved.getUnsyncedReason(),
                now,
                actor != null ? actor : "operator"
        ));

        return saved;
    }

    /**
     * Updates device control parameters (scan-line position, capture rate) for an open capture session.
     *
     * @param sessionId         the session ID
     * @param regattaId         the regatta scope for ownership check
     * @param scanLinePosition  scan-line position (may be {@code null})
     * @param captureRate       capture rate (may be {@code null})
     * @param actor             identifier of the actor performing the update
     * @return the updated {@link CaptureSession}
     * @throws IllegalStateException    if the session is already closed
     * @throws CaptureSessionNotFoundException if not found in this regatta
     */
    public CaptureSession updateDeviceControls(
            UUID sessionId,
            UUID regattaId,
            Integer scanLinePosition,
            Integer captureRate,
            String actor) {

        CaptureSession existing = requireSession(sessionId, regattaId);

        if (scanLinePosition != null && scanLinePosition < 0) {
            throw new IllegalArgumentException("scanLinePosition must be non-negative");
        }
        if (captureRate != null && captureRate <= 0) {
            throw new IllegalArgumentException("captureRate must be positive");
        }

        Instant now = Instant.now();
        CaptureSession updated = existing.withDeviceControls(scanLinePosition, captureRate, now);

        CaptureSession saved = repository.update(updated);

        return saved;
    }

    /**
     * Closes an open capture session.
     *
     * @param sessionId   the session ID
     * @param regattaId   the regatta scope for ownership check
     * @param closeReason optional reason for closing (may be {@code null})
     * @param actor       identifier of the actor closing the session
     * @return the closed {@link CaptureSession}
     * @throws IllegalStateException    if the session is already closed
     * @throws CaptureSessionNotFoundException if not found in this regatta
     */
    public CaptureSession closeSession(
            UUID sessionId,
            UUID regattaId,
            String closeReason,
            String actor) {

        CaptureSession existing = requireSession(sessionId, regattaId);

        Instant now = Instant.now();
        CaptureSession closed = existing.close(closeReason, now);

        CaptureSession saved = repository.update(closed);

        repository.appendEvent(new CaptureSessionClosedEvent(
                saved.getId(),
                saved.getRegattaId(),
                saved.getCloseReason(),
                saved.getClosedAt(),
                actor != null ? actor : "operator"
        ));

        return saved;
    }

    // ---- Helpers ---------------------------------------------------------

    private CaptureSession requireSession(UUID sessionId, UUID regattaId) {
        return getSession(sessionId, regattaId)
                .orElseThrow(() -> new CaptureSessionNotFoundException(sessionId));
    }

    // ---- Nested exception -----------------------------------------------

    /** Thrown when a requested capture session cannot be found in this regatta. */
    public static class CaptureSessionNotFoundException extends RuntimeException {
        private final UUID sessionId;

        public CaptureSessionNotFoundException(UUID sessionId) {
            super("Capture session not found: " + sessionId);
            this.sessionId = sessionId;
        }

        public UUID getSessionId() {
            return sessionId;
        }
    }
}
