package com.regattadesk.operator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a capture session for a line-scan operator station.
 *
 * <p>A capture session tracks the lifecycle of a continuous recording window
 * at a start or finish station. It records sync state, device clock offset,
 * and close lifecycle for audit and offline-sync purposes.
 *
 * <p>Each {@code CaptureSession} instance is an immutable, thread-safe snapshot
 * of session state at a specific point in time. State transitions produce a
 * new instance instead of mutating existing instances.
 */
public final class CaptureSession {

    /** Allowed session types for start vs finish stations. */
    public enum SessionType {
        start, finish
    }

    /** Lifecycle states for a capture session. */
    public enum SessionState {
        open, closed
    }

    private final UUID id;
    private final UUID regattaId;
    private final UUID blockId;
    private final String station;
    private final String deviceId;
    private final SessionType sessionType;
    private final SessionState state;
    private final Instant serverTimeAtStart;
    private final Long deviceMonotonicOffsetMs;
    private final int fps;
    private final boolean isSynced;
    private final boolean driftExceededThreshold;
    private final String unsyncedReason;
    private final Instant closedAt;
    private final String closeReason;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * Creates a new CaptureSession with the specified properties.
     *
     * @throws IllegalArgumentException if required fields are null or invalid
     */
    public CaptureSession(
            UUID id,
            UUID regattaId,
            UUID blockId,
            String station,
            String deviceId,
            SessionType sessionType,
            SessionState state,
            Instant serverTimeAtStart,
            Long deviceMonotonicOffsetMs,
            int fps,
            boolean isSynced,
            boolean driftExceededThreshold,
            String unsyncedReason,
            Instant closedAt,
            String closeReason,
            Instant createdAt,
            Instant updatedAt) {

        if (id == null) throw new IllegalArgumentException("id cannot be null");
        if (regattaId == null) throw new IllegalArgumentException("regattaId cannot be null");
        if (blockId == null) throw new IllegalArgumentException("blockId cannot be null");
        if (station == null || station.isBlank()) throw new IllegalArgumentException("station cannot be null or blank");
        if (deviceId == null || deviceId.isBlank()) throw new IllegalArgumentException("deviceId cannot be null or blank");
        if (sessionType == null) throw new IllegalArgumentException("sessionType cannot be null");
        if (state == null) throw new IllegalArgumentException("state cannot be null");
        if (serverTimeAtStart == null) throw new IllegalArgumentException("serverTimeAtStart cannot be null");
        if (fps <= 0) throw new IllegalArgumentException("fps must be positive");
        if (createdAt == null) throw new IllegalArgumentException("createdAt cannot be null");
        if (updatedAt == null) throw new IllegalArgumentException("updatedAt cannot be null");

        this.id = id;
        this.regattaId = regattaId;
        this.blockId = blockId;
        this.station = station;
        this.deviceId = deviceId;
        this.sessionType = sessionType;
        this.state = state;
        this.serverTimeAtStart = serverTimeAtStart;
        this.deviceMonotonicOffsetMs = deviceMonotonicOffsetMs;
        this.fps = fps;
        this.isSynced = isSynced;
        this.driftExceededThreshold = driftExceededThreshold;
        this.unsyncedReason = unsyncedReason;
        this.closedAt = closedAt;
        this.closeReason = closeReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ---- Accessor methods ------------------------------------------------

    public UUID getId() { return id; }
    public UUID getRegattaId() { return regattaId; }
    public UUID getBlockId() { return blockId; }
    public String getStation() { return station; }
    public String getDeviceId() { return deviceId; }
    public SessionType getSessionType() { return sessionType; }
    public SessionState getState() { return state; }
    public Instant getServerTimeAtStart() { return serverTimeAtStart; }
    public Long getDeviceMonotonicOffsetMs() { return deviceMonotonicOffsetMs; }
    public int getFps() { return fps; }
    public boolean isSynced() { return isSynced; }
    public boolean isDriftExceededThreshold() { return driftExceededThreshold; }
    public String getUnsyncedReason() { return unsyncedReason; }
    public Instant getClosedAt() { return closedAt; }
    public String getCloseReason() { return closeReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // ---- State helpers ---------------------------------------------------

    /** Returns {@code true} when this session is still open. */
    public boolean isOpen() { return state == SessionState.open; }

    /** Returns {@code true} when this session has been closed. */
    public boolean isClosed() { return state == SessionState.closed; }

    /** Returns {@code true} when this is a start-station session. */
    public boolean isStartSession() { return sessionType == SessionType.start; }

    /** Returns {@code true} when this is a finish-station session. */
    public boolean isFinishSession() { return sessionType == SessionType.finish; }

    // ---- Transition factories --------------------------------------------

    /**
     * Produces a new instance with updated sync state fields.
     *
     * @param newIsSynced            updated synced flag
     * @param newDriftExceeded       updated drift threshold flag
     * @param newUnsyncedReason      updated unsynced reason (may be {@code null})
     * @param now                    timestamp for updatedAt
     * @return new {@code CaptureSession} instance with updated sync state
     * @throws IllegalStateException if the session is already closed
     */
    public CaptureSession withSyncState(
            boolean newIsSynced,
            boolean newDriftExceeded,
            String newUnsyncedReason,
            Instant now) {

        if (isClosed()) {
            throw new IllegalStateException("Cannot update sync state of a closed session");
        }
        return new CaptureSession(
                id, regattaId, blockId, station, deviceId,
                sessionType, state,
                serverTimeAtStart, deviceMonotonicOffsetMs, fps,
                newIsSynced, newDriftExceeded, newUnsyncedReason,
                closedAt, closeReason,
                createdAt, now);
    }

    /**
     * Produces a new closed instance.
     *
     * @param reason  close reason (may be {@code null})
     * @param now     timestamp used for both {@code closedAt} and {@code updatedAt}
     * @return new {@code CaptureSession} instance in closed state
     * @throws IllegalStateException if the session is already closed
     */
    public CaptureSession close(String reason, Instant now) {
        if (isClosed()) {
            throw new IllegalStateException("Session is already closed");
        }
        return new CaptureSession(
                id, regattaId, blockId, station, deviceId,
                sessionType, SessionState.closed,
                serverTimeAtStart, deviceMonotonicOffsetMs, fps,
                isSynced, driftExceededThreshold, unsyncedReason,
                now, reason,
                createdAt, now);
    }

    // ---- Object contract -------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CaptureSession other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CaptureSession{id=" + id
                + ", regattaId=" + regattaId
                + ", station='" + station + '\''
                + ", sessionType=" + sessionType
                + ", state=" + state
                + '}';
    }
}
