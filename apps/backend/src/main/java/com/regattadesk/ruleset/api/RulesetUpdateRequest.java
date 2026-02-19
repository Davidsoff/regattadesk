package com.regattadesk.ruleset.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for updating a ruleset.
 */
public class RulesetUpdateRequest {
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("version")
    private String version;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("age_calculation_type")
    private String ageCalculationType;
    
    public RulesetUpdateRequest() {
    }
    
    public RulesetUpdateRequest(String name, String version, String description, String ageCalculationType) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.ageCalculationType = ageCalculationType;
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
}
