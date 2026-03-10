package com.regattadesk.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceRecord(
    UUID id,
    UUID regattaId,
    UUID clubId,
    String invoiceNumber,
    List<InvoiceEntryLine> entries,
    BigDecimal totalAmount,
    String currency,
    InvoiceStatus status,
    Instant generatedAt,
    Instant sentAt,
    Instant paidAt,
    String paidBy,
    String paymentReference
) {
}
