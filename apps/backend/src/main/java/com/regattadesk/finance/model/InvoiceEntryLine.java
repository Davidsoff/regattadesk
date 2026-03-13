package com.regattadesk.finance.model;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceEntryLine(
    UUID entryId,
    BigDecimal amount
) {
}
