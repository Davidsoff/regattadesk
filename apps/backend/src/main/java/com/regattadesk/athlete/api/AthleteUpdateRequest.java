package com.regattadesk.athlete.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.UUID;

public record AthleteUpdateRequest(
    @JsonProperty("first_name")
    String firstName,

    @JsonProperty("middle_name")
    String middleName,

    @JsonProperty("last_name")
    String lastName,

    @JsonProperty("date_of_birth")
    LocalDate dateOfBirth,

    @JsonProperty("gender")
    String gender,

    @JsonProperty("club_id")
    UUID clubId
) {
}
