package com.regattadesk.finance;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryPaymentStatusModelTest {

    @Test
    void transitionUnpaidToPaid_setsAuditMetadata() {
        EntryPaymentStatusModel model = new EntryPaymentStatusModel(
            PaymentStatus.UNPAID,
            null,
            null,
            null
        );

        Instant now = Instant.parse("2026-02-22T10:15:30Z");
        var result = model.transitionTo(PaymentStatus.PAID, "INV-2026-001", "finance-user", now);

        assertTrue(result.changed());
        assertEquals(PaymentStatus.UNPAID, result.previousStatus());
        assertEquals(PaymentStatus.PAID, result.nextStatus());
        assertEquals("INV-2026-001", result.nextPaymentReference());
        assertEquals("finance-user", result.nextPaidBy());
        assertEquals(now, result.nextPaidAt());
    }

    @Test
    void transitionPaidToUnpaid_clearsPaidMetadata() {
        EntryPaymentStatusModel model = new EntryPaymentStatusModel(
            PaymentStatus.PAID,
            Instant.parse("2026-02-20T09:00:00Z"),
            "finance-user",
            "INV-2026-001"
        );

        var result = model.transitionTo(PaymentStatus.UNPAID, null, "finance-user", Instant.now());

        assertTrue(result.changed());
        assertEquals(PaymentStatus.PAID, result.previousStatus());
        assertEquals(PaymentStatus.UNPAID, result.nextStatus());
        assertNull(result.nextPaidAt());
        assertNull(result.nextPaidBy());
        assertNull(result.nextPaymentReference());
    }

    @Test
    void transitionSameStatusAndReference_isNoOp() {
        EntryPaymentStatusModel model = new EntryPaymentStatusModel(
            PaymentStatus.UNPAID,
            null,
            null,
            null
        );

        var result = model.transitionTo(PaymentStatus.UNPAID, null, "finance-user", Instant.now());

        assertFalse(result.changed());
        assertNull(result.nextStatus());
        assertNull(result.previousStatus());
    }

    @Test
    void transitionSameStatusDifferentReference_isChange() {
        EntryPaymentStatusModel model = new EntryPaymentStatusModel(
            PaymentStatus.PAID,
            Instant.parse("2026-02-20T09:00:00Z"),
            "finance-user",
            "REF-OLD"
        );

        var result = model.transitionTo(PaymentStatus.PAID, "REF-NEW", "finance-user", Instant.now());

        assertTrue(result.changed());
        assertEquals(PaymentStatus.PAID, result.previousStatus());
        assertEquals(PaymentStatus.PAID, result.nextStatus());
        assertEquals("REF-NEW", result.nextPaymentReference());
        assertNotNull(result.nextPaidAt());
    }
}
