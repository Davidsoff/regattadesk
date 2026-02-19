package com.regattadesk.ruleset.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for listing rulesets.
 */
public class RulesetListResponse {
    
    @JsonProperty("data")
    private List<RulesetResponse> data;
    
    public RulesetListResponse() {
    }
    
    public RulesetListResponse(List<RulesetResponse> data) {
        this.data = data;
    }
    
    public List<RulesetResponse> getData() {
        return data;
    }
    
    public void setData(List<RulesetResponse> data) {
        this.data = data;
    }
}
