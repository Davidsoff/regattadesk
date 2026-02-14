package com.regattadesk.operator.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.operator.OperatorToken;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary DTO for listing operator tokens without exposing secrets.
 */
public class OperatorTokenSummaryResponse {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("regatta_id")
    private UUID regattaId;

    @JsonProperty("block_id")
    private UUID blockId;

    @JsonProperty("station")
    private String station;

    @JsonProperty("valid_from")
    private Instant validFrom;

    @JsonProperty("valid_until")
    private Instant validUntil;

    @JsonProperty("is_active")
    private boolean active;

    public OperatorTokenSummaryResponse() {
    }

    public OperatorTokenSummaryResponse(OperatorToken token) {
        this.id = token.getId();
        this.regattaId = token.getRegattaId();
        this.blockId = token.getBlockId();
        this.station = token.getStation();
        this.validFrom = token.getValidFrom();
        this.validUntil = token.getValidUntil();
        this.active = token.isActive();
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

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public boolean isActive() {
        return active;
    }
}
