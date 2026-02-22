package com.regattadesk.finance;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain model for entry payment status transitions.
 */
public class EntryPaymentStatusModel {
    private PaymentStatus paymentStatus;
    private Instant paidAt;
    private String paidBy;
    private String paymentReference;

    public EntryPaymentStatusModel(
        PaymentStatus paymentStatus,
        Instant paidAt,
        String paidBy,
        String paymentReference
    ) {
        this.paymentStatus = Objects.requireNonNull(paymentStatus, "paymentStatus cannot be null");
        this.paidAt = paidAt;
        this.paidBy = paidBy;
        this.paymentReference = paymentReference;
    }

    public TransitionResult transitionTo(
        PaymentStatus targetStatus,
        String nextPaymentReference,
        String actor,
        Instant now
    ) {
        Objects.requireNonNull(targetStatus, "targetStatus cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        String normalizedReference = normalizeBlank(nextPaymentReference);
        String normalizedActor = normalizeBlank(actor);

        if (targetStatus == paymentStatus
            && Objects.equals(normalizedReference, paymentReference)) {
            return TransitionResult.noChange();
        }

        PaymentStatus previousStatus = paymentStatus;
        Instant previousPaidAt = paidAt;
        String previousPaidBy = paidBy;
        String previousPaymentReference = paymentReference;

        this.paymentStatus = targetStatus;
        this.paymentReference = normalizedReference;

        if (targetStatus == PaymentStatus.PAID) {
            this.paidAt = now;
            this.paidBy = normalizedActor;
        } else {
            this.paidAt = null;
            this.paidBy = null;
        }

        return TransitionResult.changed(
            previousStatus,
            previousPaidAt,
            previousPaidBy,
            previousPaymentReference,
            this.paymentStatus,
            this.paidAt,
            this.paidBy,
            this.paymentReference
        );
    }

    private static String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    public record TransitionResult(
        boolean changed,
        PaymentStatus previousStatus,
        Instant previousPaidAt,
        String previousPaidBy,
        String previousPaymentReference,
        PaymentStatus nextStatus,
        Instant nextPaidAt,
        String nextPaidBy,
        String nextPaymentReference
    ) {
        private static TransitionResult noChange() {
            return new TransitionResult(false, null, null, null, null, null, null, null, null);
        }

        private static TransitionResult changed(
            PaymentStatus previousStatus,
            Instant previousPaidAt,
            String previousPaidBy,
            String previousPaymentReference,
            PaymentStatus nextStatus,
            Instant nextPaidAt,
            String nextPaidBy,
            String nextPaymentReference
        ) {
            return new TransitionResult(
                true,
                previousStatus,
                previousPaidAt,
                previousPaidBy,
                previousPaymentReference,
                nextStatus,
                nextPaidAt,
                nextPaidBy,
                nextPaymentReference
            );
        }
    }
}
