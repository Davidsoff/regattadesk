package com.regattadesk.finance.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.finance.InvoiceEntryLine;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceEntryLineResponse(
    @JsonProperty("entry_id")
    UUID entryId,
    BigDecimal amount
) {
    public static InvoiceEntryLineResponse from(InvoiceEntryLine entryLine) {
        return new InvoiceEntryLineResponse(entryLine.entryId(), entryLine.amount());
    }
}
