package com.regattadesk.operator.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for list of operator tokens.
 */
public class OperatorTokenListResponse {
    
    @JsonProperty("data")
    private List<OperatorTokenSummaryResponse> data;
    
    public OperatorTokenListResponse() {
    }
    
    public OperatorTokenListResponse(List<OperatorTokenSummaryResponse> data) {
        this.data = data;
    }
    
    public List<OperatorTokenSummaryResponse> getData() {
        return data == null ? List.of() : List.copyOf(data);
    }
    
    public void setData(List<OperatorTokenSummaryResponse> data) {
        this.data = data;
    }
}
