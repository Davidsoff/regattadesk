package com.regattadesk.operator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a station handoff request between two operator devices.
 * 
 * Allows secure transfer of station control from an active device to a new device
 * using PIN verification without interrupting ongoing capture operations.
 * 
 * Each {@code StationHandoff} instance is an immutable, thread-safe snapshot
 * of handoff state at a specific point in time. State transitions produce a
 * new instance instead of mutating existing instances.
 */
public final class StationHandoff {
    
    private final UUID id;
    private final UUID regattaId;
    private final UUID tokenId;
    private final String station;
    private final String requestingDeviceId;
    private final String pin;
    private final HandoffStatus status;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant completedAt;
    
    /**
     * Creates a new StationHandoff with the specified properties.
     * 
     * @param id the unique handoff identifier
     * @param regattaId the regatta scope
     * @param tokenId the operator token being used
     * @param station the station identifier
     * @param requestingDeviceId the device requesting handoff
     * @param pin the PIN for verification
     * @param status the current handoff status
     * @param createdAt the creation timestamp
     * @param expiresAt the expiration timestamp
     * @param completedAt the completion timestamp (nullable)
     * @throws IllegalArgumentException if required fields are null or invalid
     */
    public StationHandoff(
            UUID id,
            UUID regattaId,
            UUID tokenId,
            String station,
            String requestingDeviceId,
            String pin,
            HandoffStatus status,
            Instant createdAt,
            Instant expiresAt,
            Instant completedAt) {
        
        if (id == null) {
            throw new IllegalArgumentException("Handoff ID cannot be null");
        }
        if (regattaId == null) {
            throw new IllegalArgumentException("Regatta ID cannot be null");
        }
        if (tokenId == null) {
            throw new IllegalArgumentException("Token ID cannot be null");
        }
        if (station == null || station.isBlank()) {
            throw new IllegalArgumentException("Station cannot be null or blank");
        }
        if (requestingDeviceId == null || requestingDeviceId.isBlank()) {
            throw new IllegalArgumentException("Requesting device ID cannot be null or blank");
        }
        if (pin == null || pin.isBlank()) {
            throw new IllegalArgumentException("PIN cannot be null or blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Created at cannot be null");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Expires at cannot be null");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Expires at must be after created at");
        }
        
        this.id = id;
        this.regattaId = regattaId;
        this.tokenId = tokenId;
        this.station = station;
        this.requestingDeviceId = requestingDeviceId;
        this.pin = pin;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.completedAt = completedAt;
    }
    
    public UUID getId() {
        return id;
    }
    
    public UUID getRegattaId() {
        return regattaId;
    }
    
    public UUID getTokenId() {
        return tokenId;
    }
    
    public String getStation() {
        return station;
    }
    
    public String getRequestingDeviceId() {
        return requestingDeviceId;
    }
    
    public String getPin() {
        return pin;
    }
    
    public HandoffStatus getStatus() {
        return status;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getExpiresAt() {
        return expiresAt;
    }
    
    public Instant getCompletedAt() {
        return completedAt;
    }
    
    /**
     * Checks if this handoff is expired at the given instant.
     * 
     * @param checkTime the instant to check expiration at
     * @return true if the handoff is expired
     */
    public boolean isExpiredAt(Instant checkTime) {
        if (checkTime == null) {
            return false;
        }
        return !checkTime.isBefore(expiresAt);
    }
    
    /**
     * Checks if this handoff is currently expired.
     * 
     * @return true if the handoff is expired at the current time
     */
    public boolean isExpired() {
        return isExpiredAt(Instant.now());
    }
    
    /**
     * Checks if this handoff is pending (not completed or cancelled).
     * 
     * @return true if status is PENDING
     */
    public boolean isPending() {
        return status == HandoffStatus.PENDING;
    }
    
    /**
     * Checks if this handoff is completed.
     * 
     * @return true if status is COMPLETED
     */
    public boolean isCompleted() {
        return status == HandoffStatus.COMPLETED;
    }
    
    /**
     * Checks if this handoff is cancelled.
     * 
     * @return true if status is CANCELLED
     */
    public boolean isCancelled() {
        return status == HandoffStatus.CANCELLED;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StationHandoff that = (StationHandoff) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "StationHandoff{" +
               "id=" + id +
               ", regattaId=" + regattaId +
               ", tokenId=" + tokenId +
               ", station='" + station + '\'' +
               ", status=" + status +
               ", createdAt=" + createdAt +
               ", expiresAt=" + expiresAt +
               '}';
    }
    
    /**
     * Status of a station handoff request.
     */
    public enum HandoffStatus {
        /** Handoff is pending PIN verification */
        PENDING,
        /** Handoff completed successfully */
        COMPLETED,
        /** Handoff was cancelled */
        CANCELLED
    }
}
