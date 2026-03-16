package com.regattadesk.operator.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for list of station handoffs.
 */
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