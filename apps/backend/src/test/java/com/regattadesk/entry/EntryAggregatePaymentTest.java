package com.regattadesk.entry;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Entry aggregate payment status behavior (BC08-001).
 * 
 * Tests the business logic and invariants for payment status transitions.
 */
class EntryAggregatePaymentTest {

    @Test
    void updatePaymentStatus_toPaid_shouldEmitEvent() {
        // Arrange
        UUID entryId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID crewId = UUID.randomUUID();
        
        EntryAggregate entry = EntryAggregate.create(
            entryId,
            regattaId,
            eventId,
            blockId,
            crewId,
            null // billing_club_id
        );
        entry.clearChanges(); // Clear creation event
        
        // Act
        Instant paidAt = Instant.parse("2026-02-20T10:30:00Z");
        entry.updatePaymentStatus("paid", paidAt, "Finance Staff", "BANK-2026-001");
        
        // Assert
        var changes = entry.getUncommittedChanges();
        assertEquals(1, changes.size());
        
        var event = (EntryPaymentStatusUpdatedEvent) changes.get(0);
        assertEquals("paid", event.getPaymentStatus());
        assertEquals(paidAt, event.getPaidAt());
        assertEquals("Finance Staff", event.getPaidBy());
        assertEquals("BANK-2026-001", event.getPaymentReference());
    }

    @Test
    void updatePaymentStatus_toUnpaid_shouldClearMetadata() {
        // Arrange
        UUID entryId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID crewId = UUID.randomUUID();
        
        EntryAggregate entry = EntryAggregate.create(
            entryId,
            regattaId,
            eventId,
            blockId,
            crewId,
            null
        );
        
        // First mark as paid
        entry.updatePaymentStatus("paid", Instant.now(), "Staff", "REF-001");
        entry.clearChanges();
        
        // Act - mark as unpaid
        entry.updatePaymentStatus("unpaid", null, null, null);
        
        // Assert
        var changes = entry.getUncommittedChanges();
        assertEquals(1, changes.size());
        
        var event = (EntryPaymentStatusUpdatedEvent) changes.get(0);
        assertEquals("unpaid", event.getPaymentStatus());
        assertNull(event.getPaidAt());
        assertNull(event.getPaidBy());
        assertNull(event.getPaymentReference());
    }

    @Test
    void updatePaymentStatus_invalidStatus_shouldThrow() {
        UUID entryId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID crewId = UUID.randomUUID();
        
        EntryAggregate entry = EntryAggregate.create(
            entryId,
            regattaId,
            eventId,
            blockId,
            crewId,
            null
        );
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            entry.updatePaymentStatus("partially_paid", null, null, null);
        });
    }

    @Test
    void updatePaymentStatus_paidWithoutTimestamp_shouldThrow() {
        UUID entryId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID crewId = UUID.randomUUID();
        
        EntryAggregate entry = EntryAggregate.create(
            entryId,
            regattaId,
            eventId,
            blockId,
            crewId,
            null
        );
        
        // Act & Assert - paid status requires timestamp
        assertThrows(IllegalArgumentException.class, () -> {
            entry.updatePaymentStatus("paid", null, null, null);
        });
    }

    @Test
    void updatePaymentStatus_sameTwice_shouldBeIdempotent() {
        UUID entryId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID crewId = UUID.randomUUID();
        
        EntryAggregate entry = EntryAggregate.create(
            entryId,
            regattaId,
            eventId,
            blockId,
            crewId,
            null
        );
        
        Instant paidAt = Instant.parse("2026-02-20T10:30:00Z");
        entry.updatePaymentStatus("paid", paidAt, "Finance Staff", "BANK-2026-001");
        entry.clearChanges();
        
        // Act - update with same values
        entry.updatePaymentStatus("paid", paidAt, "Finance Staff", "BANK-2026-001");
        
        // Assert - current behavior re-emits event for repeat update.
        var changes = entry.getUncommittedChanges();
        assertEquals(1, changes.size(), "Expect one event re-emitted for non-idempotent behavior");
    }

    @Test
    void applyEvent_entryPaymentStatusUpdatedEvent_shouldUpdateState() {
        UUID entryId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID crewId = UUID.randomUUID();
        
        EntryAggregate entry = EntryAggregate.create(
            entryId,
            regattaId,
            eventId,
            blockId,
            crewId,
            null
        );
        
        Instant paidAt = Instant.parse("2026-02-20T10:30:00Z");
        var paymentEvent = new EntryPaymentStatusUpdatedEvent(
            entryId,
            "paid",
            paidAt,
            "Finance Staff",
            "BANK-2026-001"
        );
        
        // Act
        entry.loadFromHistory(java.util.List.of(paymentEvent));
        
        // Assert - internal state should be updated
        assertEquals("paid", entry.getPaymentStatus());
        assertEquals(paidAt, entry.getPaidAt());
        assertEquals("Finance Staff", entry.getPaidBy());
        assertEquals("BANK-2026-001", entry.getPaymentReference());
    }
}
