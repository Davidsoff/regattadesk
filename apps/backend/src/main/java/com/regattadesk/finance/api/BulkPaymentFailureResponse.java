package com.regattadesk.finance.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.finance.model.BulkPaymentFailure;

import java.util.UUID;

public record BulkPaymentFailureResponse(
    @JsonProperty("scope_type")
    String scopeType,
    UUID id,
    String code,
    String message
) {
    static BulkPaymentFailureResponse from(BulkPaymentFailure failure) {
        return new BulkPaymentFailureResponse(
            failure.scopeType(),
            failure.id(),
            failure.code(),
            failure.message()
        );
    }
}
