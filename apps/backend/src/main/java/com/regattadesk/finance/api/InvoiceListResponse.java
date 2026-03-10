package com.regattadesk.finance.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "InvoiceListResponse")
public record InvoiceListResponse(
    @JsonProperty("data")
    List<InvoiceResponse> data,
    @JsonProperty("pagination")
    InvoiceListPaginationResponse pagination
) {
}
