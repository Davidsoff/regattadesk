package com.regattadesk.operator.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO for list of station handoffs.
 */
@Schema(name = "StationHandoffListResponse")
public class StationHandoffListResponse {

    @JsonProperty("data")
    private List<StationHandoffResponse> data;

    public StationHandoffListResponse() {
    }

    public StationHandoffListResponse(List<StationHandoffResponse> data) {
        this.data = data;
    }

    public List<StationHandoffResponse> getData() {
        return data == null ? List.of() : List.copyOf(data);
    }

    public void setData(List<StationHandoffResponse> data) {
        this.data = data;
    }
}