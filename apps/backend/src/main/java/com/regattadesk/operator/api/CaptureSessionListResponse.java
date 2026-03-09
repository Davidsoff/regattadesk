package com.regattadesk.operator.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO wrapping a list of capture sessions.
 */
public class CaptureSessionListResponse {

    @JsonProperty("capture_sessions")
    private final List<CaptureSessionResponse> captureSessions;

    public CaptureSessionListResponse(List<CaptureSessionResponse> captureSessions) {
        this.captureSessions = captureSessions;
    }

    public List<CaptureSessionResponse> getCaptureSessions() {
        return captureSessions;
    }
}
