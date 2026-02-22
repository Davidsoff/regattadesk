package com.regattadesk.finance;

import java.time.Instant;
import java.util.UUID;

public record EntryPaymentStatusDetails(
    UUID entryId,
    UUID regattaId,
    UUID billingClubId,
    PaymentStatus paymentStatus,
    Instant paidAt,
    String paidBy,
    String paymentReference
) {
}
