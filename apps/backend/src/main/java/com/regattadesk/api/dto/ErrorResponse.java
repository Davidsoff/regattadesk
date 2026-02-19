package com.regattadesk.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Shared error response DTO for API endpoints.
 */
public class ErrorResponse {
    private final ErrorBody error;

    public ErrorResponse(String message) {
        this("BAD_REQUEST", message, null);
    }

    public ErrorResponse(String code, String message) {
        this(code, message, null);
    }

    public ErrorResponse(String code, String message, Map<String, Object> details) {
        this.error = new ErrorBody(code, message, details);
    }

    public ErrorBody getError() {
        return error;
    }

    public static ErrorResponse badRequest(String message) {
        return new ErrorResponse("BAD_REQUEST", message);
    }

    public static ErrorResponse notFound(String message) {
        return new ErrorResponse("NOT_FOUND", message);
    }

    public static ErrorResponse conflict(String message) {
        return new ErrorResponse("CONFLICT", message);
    }

    public static ErrorResponse gone(String code, String message) {
        return new ErrorResponse(code, message);
    }

    public static ErrorResponse internalError(String message) {
        return new ErrorResponse("INTERNAL_ERROR", message);
    }

    public static final class ErrorBody {
        private final String code;
        private final String message;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Map<String, Object> details;

        private ErrorBody(String code, String message, Map<String, Object> details) {
            this.code = code;
            this.message = message;
            this.details = details;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public Map<String, Object> getDetails() {
            return details;
        }
    }
}
