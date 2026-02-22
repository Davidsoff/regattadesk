package com.regattadesk.entry;

import com.regattadesk.aggregate.AggregateRoot;
import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Entry aggregate root (BC03-004, BC08-001).
 * 
 * Represents an entry in a regatta event. Manages entry lifecycle including
 * payment status, withdrawals, and status changes.
 * 
 * Payment status transitions are auditable through event sourcing.
 */
public class EntryAggregate extends AggregateRoot<EntryAggregate> {
    private UUID regattaId;
    private UUID eventId;
    private UUID blockId;
    private UUID crewId;
    private UUID billingClubId;
    private String status;
    private String paymentStatus;
    private Instant paidAt;
    private String paidBy;
    private String paymentReference;

    private static final List<String> VALID_PAYMENT_STATUSES = List.of("unpaid", "paid");

    public EntryAggregate(UUID id) {
        super(id);
        this.status = "entered";
        this.paymentStatus = "unpaid";
    }

    /**
     * Creates a new entry for an event.
     */
    public static EntryAggregate create(
            UUID entryId,
            UUID regattaId,
            UUID eventId,
            UUID blockId,
            UUID crewId,
            UUID billingClubId
    ) {
        validateCreate(regattaId, eventId, blockId, crewId);

        var aggregate = new EntryAggregate(entryId);
        aggregate.raiseEvent(new EntryCreatedEvent(
                entryId,
                regattaId,
                eventId,
                blockId,
                crewId,
                billingClubId
        ));
        return aggregate;
    }

    /**
     * Updates the payment status of the entry (BC08-001).
     * 
     * This method enforces payment status invariants:
     * - Status must be either "paid" or "unpaid"
     * - When marking as paid, timestamp and metadata are required
     * - When marking as unpaid, metadata is cleared
     * 
     * @param newPaymentStatus "paid" or "unpaid"
     * @param paidAt timestamp when payment was received (required for "paid")
     * @param paidBy identifier of who recorded the payment (optional)
     * @param paymentReference payment reference/transaction ID (optional)
     * @throws IllegalArgumentException if status is invalid or paid without timestamp
     */
    public void updatePaymentStatus(
            String newPaymentStatus,
            Instant paidAt,
            String paidBy,
            String paymentReference
    ) {
        validatePaymentStatus(newPaymentStatus, paidAt);

        raiseEvent(new EntryPaymentStatusUpdatedEvent(
                getId(),
                newPaymentStatus,
                paidAt,
                paidBy,
                paymentReference
        ));
    }

    private static void validateCreate(UUID regattaId, UUID eventId, UUID blockId, UUID crewId) {
        if (regattaId == null) {
            throw new IllegalArgumentException("Regatta ID is required");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID is required");
        }
        if (blockId == null) {
            throw new IllegalArgumentException("Block ID is required");
        }
        if (crewId == null) {
            throw new IllegalArgumentException("Crew ID is required");
        }
    }

    private static void validatePaymentStatus(String paymentStatus, Instant paidAt) {
        if (paymentStatus == null || !VALID_PAYMENT_STATUSES.contains(paymentStatus)) {
            throw new IllegalArgumentException(
                "Payment status must be one of: " + String.join(", ", VALID_PAYMENT_STATUSES)
            );
        }
        
        // When marking as paid, timestamp is required
        if ("paid".equals(paymentStatus) && paidAt == null) {
            throw new IllegalArgumentException("Paid timestamp is required when marking entry as paid");
        }
    }

    @Override
    protected void applyEventToState(DomainEvent event) {
        if (event instanceof EntryCreatedEvent e) {
            this.regattaId = e.getRegattaId();
            this.eventId = e.getEventId();
            this.blockId = e.getBlockId();
            this.crewId = e.getCrewId();
            this.billingClubId = e.getBillingClubId();
            this.status = "entered";
            this.paymentStatus = "unpaid";
        } else if (event instanceof EntryPaymentStatusUpdatedEvent e) {
            this.paymentStatus = e.getPaymentStatus();
            this.paidAt = e.getPaidAt();
            this.paidBy = e.getPaidBy();
            this.paymentReference = e.getPaymentReference();
        }
    }
    
    @Override
    public String getAggregateType() {
        return "Entry";
    }

    // Getters for testing and state access
    public UUID getRegattaId() { return regattaId; }
    public UUID getEventId() { return eventId; }
    public UUID getBlockId() { return blockId; }
    public UUID getCrewId() { return crewId; }
    public UUID getBillingClubId() { return billingClubId; }
    public String getStatus() { return status; }
    public String getPaymentStatus() { return paymentStatus; }
    public Instant getPaidAt() { return paidAt; }
    public String getPaidBy() { return paidBy; }
    public String getPaymentReference() { return paymentReference; }
    
    /**
     * Helper for testing - get uncommitted changes.
     */
    public List<DomainEvent> getUncommittedChanges() {
        return getUncommittedEvents();
    }
    
    /**
     * Helper for testing - clear uncommitted changes.
     */
    public void clearChanges() {
        markEventsAsCommitted();
    }
}
