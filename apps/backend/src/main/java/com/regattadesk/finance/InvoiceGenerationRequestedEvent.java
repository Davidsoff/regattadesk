package com.regattadesk.finance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceGenerationRequestedEvent(
    @JsonProperty("job_id")
    UUID jobId,
    @JsonProperty("regatta_id")
    UUID regattaId,
    @JsonProperty("requested_by")
    String requestedBy,
    @JsonProperty("requested_club_ids")
    List<UUID> requestedClubIds,
    @JsonProperty("idempotency_key")
    String idempotencyKey,
    @JsonProperty("requested_at")
    Instant requestedAt
) implements DomainEvent {
    @Override
    public String getEventType() {
        return "InvoiceGenerationRequested";
    }

    @Override
    public UUID getAggregateId() {
        return jobId;
    }
}
