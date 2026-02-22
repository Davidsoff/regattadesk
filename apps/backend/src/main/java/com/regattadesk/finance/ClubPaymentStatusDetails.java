package com.regattadesk.finance;

import java.util.UUID;

public record ClubPaymentStatusDetails(
    UUID regattaId,
    UUID clubId,
    PaymentStatus paymentStatus,
    int billableEntryCount,
    int paidEntryCount
) {
}
