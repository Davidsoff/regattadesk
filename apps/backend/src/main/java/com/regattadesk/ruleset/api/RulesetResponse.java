package com.regattadesk.ruleset.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.ruleset.RulesetAggregate;

import java.util.UUID;

/**
 * Response DTO for a ruleset.
 */
public class RulesetResponse {
    
    @JsonProperty("id")
    private UUID id;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("version")
    private String version;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("age_calculation_type")
    private String ageCalculationType;
    
    @JsonProperty("is_global")
    private boolean isGlobal;
    
    public RulesetResponse() {
    }
    
    public RulesetResponse(RulesetAggregate ruleset) {
        this.id = ruleset.getId();
        this.name = ruleset.getName();
        this.version = ruleset.getRulesetVersion();
        this.description = ruleset.getDescription();
        this.ageCalculationType = ruleset.getAgeCalculationType();
        this.isGlobal = ruleset.isGlobal();
    }
    
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getAgeCalculationType() {
        return ageCalculationType;
    }
    
    public void setAgeCalculationType(String ageCalculationType) {
        this.ageCalculationType = ageCalculationType;
    }
    
    public boolean isGlobal() {
        return isGlobal;
    }
    
    public void setGlobal(boolean global) {
        isGlobal = global;
    }
}
