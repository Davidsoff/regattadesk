package com.regattadesk.finance.api;

import java.util.List;

public record InvoiceListResponse(
    List<InvoiceResponse> invoices,
    InvoiceListPaginationResponse pagination
) {
}
