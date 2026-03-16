package com.regattadesk.finance.model;

import java.util.Locale;

public enum InvoiceStatus {
    DRAFT("draft"),
    SENT("sent"),
    PAID("paid"),
    CANCELLED("cancelled");

    private final String value;

    InvoiceStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static InvoiceStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Invoice status is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (InvoiceStatus status : values()) {
            if (status.value.equals(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unsupported invoice status: " + value);
    }
}
