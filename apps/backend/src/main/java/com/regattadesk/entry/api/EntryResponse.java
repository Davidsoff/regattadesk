package com.regattadesk.entry.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.entry.EntryDto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for Entry (BC03-004, BC08-001).
 */
public record EntryResponse(
    UUID id,
    
    @JsonProperty("regatta_id")
    UUID regattaId,
    
    @JsonProperty("event_id")
    UUID eventId,
    
    @JsonProperty("block_id")
    UUID blockId,
    
    @JsonProperty("crew_id")
    UUID crewId,
    
    @JsonProperty("billing_club_id")
    UUID billingClubId,
    
    String status,
    
    @JsonProperty("payment_status")
    String paymentStatus,
    
    @JsonProperty("paid_at")
    Instant paidAt,
    
    @JsonProperty("paid_by")
    String paidBy,
    
    @JsonProperty("payment_reference")
    String paymentReference
) {
    public static EntryResponse from(EntryDto dto) {
        return new EntryResponse(
            dto.id(),
            dto.regattaId(),
            dto.eventId(),
            dto.blockId(),
            dto.crewId(),
            dto.billingClubId(),
            dto.status(),
            dto.paymentStatus(),
            dto.paidAt(),
            dto.paidBy(),
            dto.paymentReference()
        );
    }
}
