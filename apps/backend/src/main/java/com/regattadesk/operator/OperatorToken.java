package com.regattadesk.operator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents an operator QR token for station-scoped authentication.
 * 
 * Operator tokens are issued by staff and grant time-limited, station-scoped access
 * to operator workflows. Tokens can be revoked and have explicit validity windows.
 * 
 * This class is immutable and thread-safe.
 */
public final class OperatorToken {
    
    private final UUID id;
    private final UUID regattaId;
    private final UUID blockId;
    private final String station;
    private final String token;
    private final String pin;
    private final Instant validFrom;
    private final Instant validUntil;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;
    
    /**
     * Creates a new OperatorToken with the specified properties.
     * 
     * @param id the unique token identifier
     * @param regattaId the regatta scope
     * @param blockId the optional block scope (can be null)
     * @param station the station identifier
     * @param token the unique token string
     * @param pin the optional PIN for handoff verification
     * @param validFrom the validity start time (inclusive)
     * @param validUntil the validity end time (exclusive)
     * @param active whether the token is active (not revoked)
     * @param createdAt the creation timestamp
     * @param updatedAt the last update timestamp
     * @throws IllegalArgumentException if required fields are null or invalid
     */
    public OperatorToken(
            UUID id,
            UUID regattaId,
            UUID blockId,
            String station,
            String token,
            String pin,
            Instant validFrom,
            Instant validUntil,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        
        if (id == null) {
            throw new IllegalArgumentException("Token ID cannot be null");
        }
        if (regattaId == null) {
            throw new IllegalArgumentException("Regatta ID cannot be null");
        }
        if (station == null || station.isBlank()) {
            throw new IllegalArgumentException("Station cannot be null or blank");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token cannot be null or blank");
        }
        if (validFrom == null) {
            throw new IllegalArgumentException("Valid from cannot be null");
        }
        if (validUntil == null) {
            throw new IllegalArgumentException("Valid until cannot be null");
        }
        if (!validUntil.isAfter(validFrom)) {
            throw new IllegalArgumentException("Valid until must be after valid from");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Created at cannot be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("Updated at cannot be null");
        }
        
        this.id = id;
        this.regattaId = regattaId;
        this.blockId = blockId;
        this.station = station;
        this.token = token;
        this.pin = pin;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    public UUID getId() {
        return id;
    }
    
    public UUID getRegattaId() {
        return regattaId;
    }
    
    public UUID getBlockId() {
        return blockId;
    }
    
    public String getStation() {
        return station;
    }
    
    public String getToken() {
        return token;
    }
    
    public String getPin() {
        return pin;
    }
    
    public Instant getValidFrom() {
        return validFrom;
    }
    
    public Instant getValidUntil() {
        return validUntil;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    /**
     * Checks if this token is valid at the given instant.
     * 
     * A token is valid if:
     * - It is active (not revoked)
     * - The check time is within the validity window [validFrom, validUntil)
     * 
     * @param checkTime the instant to check validity at
     * @return true if the token is valid at the given time
     */
    public boolean isValidAt(Instant checkTime) {
        if (!active) {
            return false;
        }
        if (checkTime == null) {
            return false;
        }
        return !checkTime.isBefore(validFrom) && checkTime.isBefore(validUntil);
    }
    
    /**
     * Checks if this token is currently valid.
     * 
     * @return true if the token is valid at the current time
     */
    public boolean isCurrentlyValid() {
        return isValidAt(Instant.now());
    }
    
    /**
     * Checks if this token is expired (past its validity window).
     * 
     * @return true if the current time is past the validUntil time
     */
    public boolean isExpired() {
        return Instant.now().isAfter(validUntil) || Instant.now().equals(validUntil);
    }
    
    /**
     * Checks if this token is scoped to a specific block.
     * 
     * @return true if blockId is not null
     */
    public boolean hasBlockScope() {
        return blockId != null;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperatorToken that = (OperatorToken) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "OperatorToken{" +
               "id=" + id +
               ", regattaId=" + regattaId +
               ", blockId=" + blockId +
               ", station='" + station + '\'' +
               ", validFrom=" + validFrom +
               ", validUntil=" + validUntil +
               ", active=" + active +
               '}';
    }
}
