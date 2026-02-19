package com.regattadesk.athlete.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.UUID;

public record AthleteCreateRequest(
    @JsonProperty("first_name")
    @NotBlank(message = "First name is required")
    String firstName,

    @JsonProperty("middle_name")
    String middleName,

    @JsonProperty("last_name")
    @NotBlank(message = "Last name is required")
    String lastName,

    @JsonProperty("date_of_birth")
    @NotNull(message = "Date of birth is required")
    LocalDate dateOfBirth,

    @JsonProperty("gender")
    @NotNull(message = "Gender is required")
    @Pattern(regexp = "^(M|F|X)$", message = "Gender must be M, F, or X")
    String gender,

    @JsonProperty("club_id")
    UUID clubId
) {
}
