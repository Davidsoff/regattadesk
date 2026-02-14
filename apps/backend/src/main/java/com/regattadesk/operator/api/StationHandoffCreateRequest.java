package com.regattadesk.operator.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a station handoff.
 */
public class StationHandoffCreateRequest {
    
    @NotBlank(message = "requesting_device_id is required")
    private String requestingDeviceId;
    
    public StationHandoffCreateRequest() {
    }
    
    public StationHandoffCreateRequest(String requestingDeviceId) {
        this.requestingDeviceId = requestingDeviceId;
    }
    
    public String getRequestingDeviceId() {
        return requestingDeviceId;
    }
    
    public void setRequestingDeviceId(String requestingDeviceId) {
        this.requestingDeviceId = requestingDeviceId;
    }
}
