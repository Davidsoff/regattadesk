package com.regattadesk.finance;

import java.util.Locale;

public enum InvoiceGenerationJobStatus {
    PENDING("pending"),
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed");

    private final String value;

    InvoiceGenerationJobStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static InvoiceGenerationJobStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Invoice generation job status is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (InvoiceGenerationJobStatus status : values()) {
            if (status.value.equals(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unsupported invoice generation job status: " + value);
    }
}
