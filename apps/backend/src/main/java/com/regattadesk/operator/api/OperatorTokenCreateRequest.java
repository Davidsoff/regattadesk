package com.regattadesk.operator.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Request DTO for creating an operator token.
 */
public class OperatorTokenCreateRequest {
    
    @JsonProperty("block_id")
    private UUID blockId;
    
    @JsonProperty("station")
    @NotBlank(message = "station must not be blank")
    private String station;
    
    @JsonProperty("valid_from")
    @NotNull(message = "valid_from is required")
    private Instant validFrom;
    
    @JsonProperty("valid_until")
    @NotNull(message = "valid_until is required")
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
