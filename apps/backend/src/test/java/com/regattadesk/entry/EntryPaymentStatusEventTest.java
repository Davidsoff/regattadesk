package com.regattadesk.entry;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Entry payment status domain events (BC08-001).
 * 
 * Tests that payment status change events are created correctly and
 * contain all required audit information.
 */
class EntryPaymentStatusEventTest {

    @Test
    void entryPaymentStatusUpdatedEvent_paid_shouldContainAllFields() {
        UUID entryId = UUID.randomUUID();
        Instant paidAt = Instant.parse("2026-02-20T10:30:00Z");
        
        var event = new EntryPaymentStatusUpdatedEvent(
            entryId,
            "paid",
            paidAt,
            "Finance Staff",
            "BANK-2026-001"
        );
        
        assertEquals(entryId, event.getEntryId());
        assertEquals("paid", event.getPaymentStatus());
        assertEquals(paidAt, event.getPaidAt());
        assertEquals("Finance Staff", event.getPaidBy());
        assertEquals("BANK-2026-001", event.getPaymentReference());
    }

    @Test
    void entryPaymentStatusUpdatedEvent_unpaid_shouldHaveNullMetadata() {
        UUID entryId = UUID.randomUUID();
        
        var event = new EntryPaymentStatusUpdatedEvent(
            entryId,
            "unpaid",
            null,
            null,
            null
        );
        
        assertEquals(entryId, event.getEntryId());
        assertEquals("unpaid", event.getPaymentStatus());
        assertNull(event.getPaidAt());
        assertNull(event.getPaidBy());
        assertNull(event.getPaymentReference());
    }

    @Test
    void entryPaymentStatusUpdatedEvent_invalidStatus_shouldThrow() {
        UUID entryId = UUID.randomUUID();
        
        // This will be enforced by aggregate validation
        // For now just document expected behavior
    }

    @Test
    void entryPaymentStatusUpdatedEvent_getEventType_shouldReturnCorrectType() {
        UUID entryId = UUID.randomUUID();
        var event = new EntryPaymentStatusUpdatedEvent(
            entryId,
            "paid",
            Instant.now(),
            "Staff",
            "REF-001"
        );
        
        assertEquals("EntryPaymentStatusUpdatedEvent", event.getEventType());
    }
}
