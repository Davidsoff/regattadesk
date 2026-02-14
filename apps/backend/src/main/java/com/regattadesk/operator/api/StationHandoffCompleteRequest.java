package com.regattadesk.operator.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for completing a station handoff with PIN.
 */
public class StationHandoffCompleteRequest {
    
    @NotBlank(message = "pin is required")
    private String pin;
    
    public StationHandoffCompleteRequest() {
    }
    
    public StationHandoffCompleteRequest(String pin) {
        this.pin = pin;
    }
    
    public String getPin() {
        return pin;
    }
    
    public void setPin(String pin) {
        this.pin = pin;
    }
}
