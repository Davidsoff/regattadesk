package com.regattadesk.athlete.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.athlete.AthleteDto;

import java.time.LocalDate;
import java.util.UUID;

public record AthleteResponse(
    @JsonProperty("id")
    UUID id,

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
    public static AthleteResponse from(AthleteDto dto) {
        return new AthleteResponse(
            dto.id(),
            dto.firstName(),
            dto.middleName(),
            dto.lastName(),
            dto.dateOfBirth(),
            dto.gender(),
            dto.clubId()
        );
    }
}
