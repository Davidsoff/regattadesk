package com.regattadesk.entry;

import java.time.Instant;
import java.util.UUID;

/**
 * Data transfer object for Entry (BC03-004, BC08-001).
 * 
 * Used for internal service layer communication.
 */
public record EntryDto(
    UUID id,
    UUID regattaId,
    UUID eventId,
    UUID blockId,
    UUID crewId,
    UUID billingClubId,
    String status,
    String paymentStatus,
    Instant paidAt,
    String paidBy,
    String paymentReference
) {
}
