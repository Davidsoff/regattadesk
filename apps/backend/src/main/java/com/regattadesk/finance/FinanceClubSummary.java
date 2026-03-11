package com.regattadesk.finance;

import java.util.UUID;

public record FinanceClubSummary(
    UUID clubId,
    String clubName,
    String paymentStatus,
    int paidEntries,
    int unpaidEntries
) {
}
