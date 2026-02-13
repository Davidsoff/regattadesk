package com.regattadesk.operator.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Request DTO for creating an operator token.
 */
public class OperatorTokenCreateRequest {
    
    @JsonProperty("block_id")
    private UUID blockId;
    
    @JsonProperty("station")
    private String station;
    
    @JsonProperty("valid_from")
    private Instant validFrom;
    
    @JsonProperty("valid_until")
    private Instant validUntil;
    
    public OperatorTokenCreateRequest() {
    }
    
    public OperatorTokenCreateRequest(UUID blockId, String station, Instant validFrom, Instant validUntil) {
        this.blockId = blockId;
        this.station = station;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
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
}
