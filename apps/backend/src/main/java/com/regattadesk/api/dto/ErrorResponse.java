package com.regattadesk.api.dto;

/**
 * Shared error response DTO for API endpoints.
 */
public class ErrorResponse {
    private final String error;

    public ErrorResponse(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }
}
