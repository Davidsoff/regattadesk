package com.regattadesk.finance;

import java.util.UUID;

public record BulkPaymentFailure(
    String scopeType,
    UUID id,
    String code,
    String message
) {
}
