package com.regattadesk.operator.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for list of operator tokens.
 */
public class OperatorTokenListResponse {
    
    @JsonProperty("data")
    private List<OperatorTokenResponse> data;
    
    public OperatorTokenListResponse() {
    }
    
    public OperatorTokenListResponse(List<OperatorTokenResponse> data) {
        this.data = data;
    }
    
    public List<OperatorTokenResponse> getData() {
        return data;
    }
    
    public void setData(List<OperatorTokenResponse> data) {
        this.data = data;
    }
}
