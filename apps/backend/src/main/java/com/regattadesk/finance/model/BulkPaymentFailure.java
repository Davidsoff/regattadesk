package com.regattadesk.finance.model;

import java.util.UUID;

public record BulkPaymentFailure(
    String scopeType,
    UUID id,
    String code,
    String message
) {
}
