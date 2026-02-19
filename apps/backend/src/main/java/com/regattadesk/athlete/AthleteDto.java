package com.regattadesk.athlete;

import java.time.LocalDate;
import java.util.UUID;

public record AthleteDto(
    UUID id,
    String firstName,
    String middleName,
    String lastName,
    LocalDate dateOfBirth,
    String gender,
    UUID clubId
) {
}
