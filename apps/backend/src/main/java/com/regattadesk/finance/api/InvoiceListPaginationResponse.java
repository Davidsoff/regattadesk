package com.regattadesk.finance.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "InvoiceListPaginationResponse")
public record InvoiceListPaginationResponse(
    @JsonProperty("has_more")
    boolean hasMore,
    @JsonProperty("next_cursor")
    String nextCursor
) {
}
