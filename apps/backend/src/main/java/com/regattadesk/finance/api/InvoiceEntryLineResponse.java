package com.regattadesk.finance.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.finance.InvoiceEntryLine;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "InvoiceEntryLineResponse")
public record InvoiceEntryLineResponse(
    @JsonProperty("entry_id")
    UUID entryId,
    @Schema(minimum = "0")
    BigDecimal amount
) {
    public static InvoiceEntryLineResponse from(InvoiceEntryLine entryLine) {
        return new InvoiceEntryLineResponse(entryLine.entryId(), entryLine.amount());
    }
}
