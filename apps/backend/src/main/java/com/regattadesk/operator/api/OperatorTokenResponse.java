package com.regattadesk.operator.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.operator.OperatorToken;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for operator token.
 */
public class OperatorTokenResponse {
    
    @JsonProperty("id")
    private UUID id;
    
    @JsonProperty("regatta_id")
    private UUID regattaId;
    
    @JsonProperty("block_id")
    private UUID blockId;
    
    @JsonProperty("station")
    private String station;
    
    @JsonProperty("token")
    private String token;
    
    @JsonProperty("pin")
    private String pin;
    
    @JsonProperty("valid_from")
    private Instant validFrom;
    
    @JsonProperty("valid_until")
    private Instant validUntil;
    
    @JsonProperty("is_active")
    private boolean active;
    
    public OperatorTokenResponse() {
    }
    
    public OperatorTokenResponse(OperatorToken token) {
        this.id = token.getId();
        this.regattaId = token.getRegattaId();
        this.blockId = token.getBlockId();
        this.station = token.getStation();
        this.token = token.getToken();
        this.pin = token.getPin();
        this.validFrom = token.getValidFrom();
        this.validUntil = token.getValidUntil();
        this.active = token.isActive();
    }
    
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getRegattaId() {
        return regattaId;
    }
    
    public void setRegattaId(UUID regattaId) {
        this.regattaId = regattaId;
    }
    
    public UUID getBlockId() {
        return blockId;
    }
    
    public void setBlockId(UUID blockId) {
        this.blockId = blockId;
    }
    
    public String getStation() {
        return station;
    }
    
    public void setStation(String station) {
        this.station = station;
    }
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public String getPin() {
        return pin;
    }
    
    public void setPin(String pin) {
        this.pin = pin;
    }
    
    public Instant getValidFrom() {
        return validFrom;
    }
    
    public void setValidFrom(Instant validFrom) {
        this.validFrom = validFrom;
    }
    
    public Instant getValidUntil() {
        return validUntil;
    }
    
    public void setValidUntil(Instant validUntil) {
        this.validUntil = validUntil;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
}
