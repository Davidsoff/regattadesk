package com.regattadesk.finance;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceEntryLine(
    UUID entryId,
    BigDecimal amount
) {
}
