package com.regattadesk.operator.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for closing a capture session.
 */
public class CaptureSessionCloseRequest {

    @JsonProperty("close_reason")
    private String closeReason;

    public CaptureSessionCloseRequest() {
    }

    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }
}
