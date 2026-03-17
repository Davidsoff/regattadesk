package com.regattadesk.finance;

import java.util.UUID;

public record FinanceEntrySummary(
    UUID entryId,
    String crewName,
    String clubName,
    String paymentStatus
) {
}
