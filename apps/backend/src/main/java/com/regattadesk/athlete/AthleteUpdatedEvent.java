package com.regattadesk.athlete;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;

import java.time.LocalDate;
import java.util.UUID;

public class AthleteUpdatedEvent implements DomainEvent {
    private final UUID athleteId;
    private final String firstName;
    private final String middleName;
    private final String lastName;
    private final LocalDate dateOfBirth;
    private final String gender;
    private final UUID clubId;

    @JsonCreator
    public AthleteUpdatedEvent(
        @JsonProperty("athleteId") UUID athleteId,
        @JsonProperty("firstName") String firstName,
        @JsonProperty("middleName") String middleName,
        @JsonProperty("lastName") String lastName,
        @JsonProperty("dateOfBirth") LocalDate dateOfBirth,
        @JsonProperty("gender") String gender,
        @JsonProperty("clubId") UUID clubId
    ) {
        this.athleteId = athleteId;
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.clubId = clubId;
    }

    @Override
    public String getEventType() {
        return "AthleteUpdated";
    }

    @Override
    public UUID getAggregateId() {
        return athleteId;
    }

    public UUID getAthleteId() { return athleteId; }
    public String getFirstName() { return firstName; }
    public String getMiddleName() { return middleName; }
    public String getLastName() { return lastName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getGender() { return gender; }
    public UUID getClubId() { return clubId; }
}
