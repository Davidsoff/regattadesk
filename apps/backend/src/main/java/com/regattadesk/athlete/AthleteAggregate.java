package com.regattadesk.athlete;

import com.regattadesk.aggregate.AggregateRoot;
import com.regattadesk.eventstore.DomainEvent;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class AthleteAggregate extends AggregateRoot<AthleteAggregate> {
    private String firstName;
    private String middleName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String gender;
    private UUID clubId;
    private boolean deleted;

    private static final List<String> VALID_GENDERS = Arrays.asList("M", "F", "X");

    public AthleteAggregate(UUID id) {
        super(id);
        this.deleted = false;
    }

    public static AthleteAggregate create(
            UUID athleteId,
            String firstName,
            String middleName,
            String lastName,
            LocalDate dateOfBirth,
            String gender,
            UUID clubId
    ) {
        validateCreate(firstName, lastName, dateOfBirth, gender);

        var aggregate = new AthleteAggregate(athleteId);
        aggregate.raiseEvent(new AthleteCreatedEvent(
                athleteId,
                firstName,
                middleName,
                lastName,
                dateOfBirth,
                gender,
                clubId
        ));
        return aggregate;
    }

    public void update(
            String firstName,
            String middleName,
            String lastName,
            String gender,
            UUID clubId
    ) {
        if (deleted) {
            throw new IllegalStateException("Cannot update deleted athlete");
        }
        validateUpdate(firstName, lastName, gender);

        raiseEvent(new AthleteUpdatedEvent(
                getId(),
                firstName,
                middleName,
                lastName,
                gender,
                clubId
        ));
    }

    public void delete() {
        if (deleted) {
            throw new IllegalStateException("Athlete already deleted");
        }
        raiseEvent(new AthleteDeletedEvent(getId()));
    }

    private static void validateCreate(String firstName, String lastName, LocalDate dateOfBirth, String gender) {
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("First name is required");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("Last name is required");
        }
        if (dateOfBirth == null) {
            throw new IllegalArgumentException("Date of birth is required");
        }
        if (dateOfBirth.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Date of birth cannot be in the future");
        }
        if (gender == null || !VALID_GENDERS.contains(gender)) {
            throw new IllegalArgumentException("Gender must be one of: M, F, X");
        }
    }

    private static void validateUpdate(String firstName, String lastName, String gender) {
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("First name is required");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("Last name is required");
        }
        if (gender == null || !VALID_GENDERS.contains(gender)) {
            throw new IllegalArgumentException("Gender must be one of: M, F, X");
        }
    }

    @Override
    protected void applyEventToState(DomainEvent event) {
        if (event instanceof AthleteCreatedEvent e) {
            this.firstName = e.getFirstName();
            this.middleName = e.getMiddleName();
            this.lastName = e.getLastName();
            this.dateOfBirth = e.getDateOfBirth();
            this.gender = e.getGender();
            this.clubId = e.getClubId();
            this.deleted = false;
        } else if (event instanceof AthleteUpdatedEvent e) {
            this.firstName = e.getFirstName();
            this.middleName = e.getMiddleName();
            this.lastName = e.getLastName();
            this.gender = e.getGender();
            this.clubId = e.getClubId();
        } else if (event instanceof AthleteDeletedEvent e) {
            this.deleted = true;
        }
    }

    @Override
    public String getAggregateType() {
        return "Athlete";
    }

    // Getters for testing
    public String getFirstName() {
        return firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public UUID getClubId() {
        return clubId;
    }

    public boolean isDeleted() {
        return deleted;
    }
}
