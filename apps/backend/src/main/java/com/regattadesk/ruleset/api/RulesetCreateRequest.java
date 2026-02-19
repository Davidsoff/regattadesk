package com.regattadesk.ruleset.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Request DTO for creating a ruleset.
 */
public class RulesetCreateRequest {

    @JsonProperty("id")
    private UUID id;
    
    @JsonProperty("name")
    @NotBlank(message = "name must not be blank")
    private String name;
    
    @JsonProperty("version")
    @NotBlank(message = "version must not be blank")
    private String version;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("age_calculation_type")
    @NotBlank(message = "age_calculation_type must not be blank")
    private String ageCalculationType;
    
    public RulesetCreateRequest() {
    }
    
    public RulesetCreateRequest(UUID id, String name, String version, String description, String ageCalculationType) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.description = description;
        this.ageCalculationType = ageCalculationType;
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
}
