package com.regattadesk.operator.api;

import com.regattadesk.operator.StationHandoff;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for station handoff information.
 */
public class StationHandoffResponse {
    
    private final UUID id;
    private final UUID regattaId;
    private final UUID tokenId;
    private final String station;
    private final String requestingDeviceId;
    private final String status;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant completedAt;
    private final String pin; // Only included in reveal responses
    
    public StationHandoffResponse(StationHandoff handoff) {
        this(handoff, null);
    }
    
    public StationHandoffResponse(StationHandoff handoff, String pin) {
        this.id = handoff.getId();
        this.regattaId = handoff.getRegattaId();
        this.tokenId = handoff.getTokenId();
        this.station = handoff.getStation();
        this.requestingDeviceId = handoff.getRequestingDeviceId();
        this.status = handoff.getStatus().name();
        this.createdAt = handoff.getCreatedAt();
        this.expiresAt = handoff.getExpiresAt();
        this.completedAt = handoff.getCompletedAt();
        this.pin = pin;
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
    
    public String getStatus() {
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
    
    public String getPin() {
        return pin;
    }
}
