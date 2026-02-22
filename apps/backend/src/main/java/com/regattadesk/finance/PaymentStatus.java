package com.regattadesk.finance;

import java.util.Locale;

public enum PaymentStatus {
    UNPAID("unpaid"),
    PAID("paid");

    private final String value;

    PaymentStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static PaymentStatus fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Payment status is required");
        }

        String normalized = raw.toLowerCase(Locale.ROOT);
        for (PaymentStatus status : values()) {
            if (status.value.equals(normalized)) {
                return status;
            }
        }

        throw new IllegalArgumentException("Payment status must be one of: unpaid, paid");
    }
}
