package com.regattadesk.ruleset.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for duplicating a ruleset with a new name and version.
 */
public class RulesetDuplicateRequest {

    @JsonProperty("new_name")
    @NotBlank(message = "new_name must not be blank")
    private String newName;

    @JsonProperty("new_version")
    @NotBlank(message = "new_version must not be blank")
    private String newVersion;

    public RulesetDuplicateRequest() {
    }

    public RulesetDuplicateRequest(String newName, String newVersion) {
        this.newName = newName;
        this.newVersion = newVersion;
    }

    public String getNewName() {
        return newName;
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }

    public String getNewVersion() {
        return newVersion;
    }

    public void setNewVersion(String newVersion) {
        this.newVersion = newVersion;
    }
}
