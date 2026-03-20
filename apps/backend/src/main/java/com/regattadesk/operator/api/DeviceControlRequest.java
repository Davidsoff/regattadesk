package com.regattadesk.operator.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;

/**
 * Request DTO for updating device control parameters.
 */
public class DeviceControlRequest {

    @JsonProperty("scan_line_position")
    @Min(value = 0, message = "scan_line_position must be non-negative")
    private Integer scanLinePosition;

    @JsonProperty("capture_rate")
    @Min(value = 1, message = "capture_rate must be positive")
    private Integer captureRate;

    public DeviceControlRequest() {
    }

    public DeviceControlRequest(Integer scanLinePosition, Integer captureRate) {
        this.scanLinePosition = scanLinePosition;
        this.captureRate = captureRate;
    }

    public Integer getScanLinePosition() {
        return scanLinePosition;
    }

    public void setScanLinePosition(Integer scanLinePosition) {
        this.scanLinePosition = scanLinePosition;
    }

    public Integer getCaptureRate() {
        return captureRate;
    }

    public void setCaptureRate(Integer captureRate) {
        this.captureRate = captureRate;
    }
}
