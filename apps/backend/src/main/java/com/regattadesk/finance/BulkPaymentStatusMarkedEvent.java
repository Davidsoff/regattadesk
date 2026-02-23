package com.regattadesk.finance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.util.List;
import java.util.UUID;

public class BulkPaymentStatusMarkedEvent implements DomainEvent {
    private final UUID regattaId;
    private final String targetStatus;
    private final int totalRequested;
    private final int processedCount;
    private final int updatedCount;
    private final int unchangedCount;
    private final int failedCount;
    private final List<BulkPaymentFailure> failures;
    private final String requestedBy;
    private final String paymentReference;
    private final String idempotencyKey;

    @JsonCreator
    public BulkPaymentStatusMarkedEvent(
        @JsonProperty("regattaId") UUID regattaId,
        @JsonProperty("targetStatus") String targetStatus,
        @JsonProperty("totalRequested") int totalRequested,
        @JsonProperty("processedCount") int processedCount,
        @JsonProperty("updatedCount") int updatedCount,
        @JsonProperty("unchangedCount") int unchangedCount,
        @JsonProperty("failedCount") int failedCount,
        @JsonProperty("failures") List<BulkPaymentFailure> failures,
        @JsonProperty("requestedBy") String requestedBy,
        @JsonProperty("paymentReference") String paymentReference,
        @JsonProperty("idempotencyKey") String idempotencyKey
    ) {
        this.regattaId = regattaId;
        this.targetStatus = targetStatus;
        this.totalRequested = totalRequested;
        this.processedCount = processedCount;
        this.updatedCount = updatedCount;
        this.unchangedCount = unchangedCount;
        this.failedCount = failedCount;
        this.failures = failures == null ? List.of() : List.copyOf(failures);
        this.requestedBy = requestedBy;
        this.paymentReference = paymentReference;
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public String getEventType() {
        return "BulkPaymentStatusMarked";
    }

    @Override
    public UUID getAggregateId() {
        return regattaId;
    }

    public UUID getRegattaId() {
        return regattaId;
    }

    public String getTargetStatus() {
        return targetStatus;
    }

    public int getTotalRequested() {
        return totalRequested;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public int getUnchangedCount() {
        return unchangedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public List<BulkPaymentFailure> getFailures() {
        return failures;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
