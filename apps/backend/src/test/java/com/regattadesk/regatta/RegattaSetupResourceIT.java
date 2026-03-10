package com.regattadesk.regatta;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class RegattaSetupResourceIT {

    @Inject
    DataSource dataSource;

    @Test
    void regattaSetupEndpoints_supportCrudRoundTripAcrossSetupEntities() throws Exception {
        SeedData seed = seedBaseData();
        SeedData otherSeed = seedBaseData();

        String eventGroupId = given()
            .header("Remote-User", "staff-admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "name": "Junior Sweep",
                  "description": "Morning setup group",
                  "display_order": 1
                }
                """)
            .when()
            .post("/api/v1/regattas/" + seed.regattaId + "/event-groups")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("name", equalTo("Junior Sweep"))
            .extract()
            .path("id");

        given()
            .header("Remote-User", "staff-admin")
            .header("Remote-Groups", "super_admin")
            .queryParam("search", "Junior")
            .when()
            .get("/api/v1/regattas/" + seed.regattaId + "/event-groups")
            .then()
            .statusCode(200)
            .body("data.id", hasItem(eventGroupId))
            .body("pagination.has_more", equalTo(false));

        String eventId = given()
            .header("Remote-User", "staff-admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "event_group_id": "%s",
                  "category_id": "%s",
                  "boat_type_id": "%s",
                  "name": "JW4x",
                  "display_order": 1
                }
                """.formatted(eventGroupId, seed.categoryId, seed.boatTypeId))
            .when()
            .post("/api/v1/regattas/" + seed.regattaId + "/events")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("event_group_id", equalTo(eventGroupId))
            .body("name", equalTo("JW4x"))
            .extract()
            .path("id");

        String crewId = given()
            .header("Remote-User", "staff-admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "display_name": "Amstel Crew",
                  "club_id": "%s",
                  "is_composite": false,
                  "members": [
                    {
                      "athlete_id": "%s",
                      "seat_position": 1
                    }
                  ]
                }
                """.formatted(seed.clubId, seed.athleteId))
            .when()
            .post("/api/v1/regattas/" + seed.regattaId + "/crews")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("display_name", equalTo("Amstel Crew"))
            .body("members[0].athlete_id", equalTo(seed.athleteId.toString()))
            .extract()
            .path("id");

        UUID otherCrewId = seedCrew(otherSeed);

        given()
            .header("Remote-User", "staff-admin")
            .header("Remote-Groups", "super_admin")
            .when()
            .get("/api/v1/regattas/" + seed.regattaId + "/crews")
            .then()
            .statusCode(200)
            .body("data.id", hasItem(crewId))
            .body("data.display_name", hasItem("Amstel Crew"))
            .body("data.id", not(hasItem(otherCrewId.toString())));

        String entryId = given()
            .header("Remote-User", "staff-admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "event_id": "%s",
                  "block_id": "%s",
                  "crew_id": "%s",
                  "billing_club_id": "%s"
                }
                """.formatted(eventId, seed.blockId, crewId, seed.clubId))
            .when()
            .post("/api/v1/regattas/" + seed.regattaId + "/entries")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("status", equalTo("entered"))
            .extract()
            .path("id");

        given()
            .header("Remote-User", "staff-admin")
            .header("Remote-Groups", "super_admin")
            .queryParam("status", "entered")
            .when()
            .get("/api/v1/regattas/" + seed.regattaId + "/entries")
            .then()
            .statusCode(200)
            .body("data.id", hasItem(entryId));
    }

    @Test
    void withdrawalAndReinstateEndpoints_surfaceMachineReadableConflicts() throws Exception {
        SeedData seed = seedBaseData();
        UUID eventId = seedEvent(seed);
        UUID crewId = seedCrew(seed);
        UUID entryId = seedEntry(seed, eventId, crewId);

        given()
            .header("Remote-User", "staff-admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "status": "withdrawn_before_draw",
                  "reason": "Medical certificate",
                  "expected_status": "entered"
                }
                """)
            .when()
            .post("/api/v1/regattas/" + seed.regattaId + "/entries/" + entryId + "/withdraw")
            .then()
            .statusCode(200)
            .body("status", equalTo("withdrawn_before_draw"))
            .body("audit.actor", equalTo("staff-admin"))
            .body("audit.at", notNullValue());

        given()
            .header("Remote-User", "staff-admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "status": "withdrawn_before_draw",
                  "reason": "Duplicate submission",
                  "expected_status": "entered"
                }
                """)
            .when()
            .post("/api/v1/regattas/" + seed.regattaId + "/entries/" + entryId + "/withdraw")
            .then()
            .statusCode(409)
            .body("error.code", equalTo("CONFLICT"))
            .body("error.details.current_status", equalTo("withdrawn_before_draw"));

        given()
            .header("Remote-User", "staff-admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "expected_status": "withdrawn_before_draw"
                }
                """)
            .when()
            .post("/api/v1/regattas/" + seed.regattaId + "/entries/" + entryId + "/reinstate")
            .then()
            .statusCode(200)
            .body("status", equalTo("entered"));
    }

    @Test
    void withdrawEndpoint_rejectsUnsupportedStatuses() throws Exception {
        SeedData seed = seedBaseData();
        UUID eventId = seedEvent(seed);
        UUID crewId = seedCrew(seed);
        UUID entryId = seedEntry(seed, eventId, crewId);

        given()
            .header("Remote-User", "staff-admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "status": "dns",
                  "reason": "Invalid transition",
                  "expected_status": "entered"
                }
                """)
            .when()
            .post("/api/v1/regattas/" + seed.regattaId + "/entries/" + entryId + "/withdraw")
            .then()
            .statusCode(400);
    }

    private SeedData seedBaseData() throws Exception {
        UUID regattaId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        UUID athleteId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID boatTypeId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        String boatTypeCode = "BT" + boatTypeId.toString().substring(0, 6).toUpperCase();
        Instant now = Instant.parse("2026-03-10T12:00:00Z");

        try (Connection connection = dataSource.getConnection()) {
            insert(
                connection,
                "INSERT INTO regattas (id, name, time_zone, status, entry_fee, currency, draw_revision, results_revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                regattaId, "Issue 134 Regatta", "Europe/Amsterdam", "draft", 0, "EUR", 0, 0, Timestamp.from(now), Timestamp.from(now)
            );
            insert(
                connection,
                "INSERT INTO clubs (id, name, short_name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                clubId, "Amstel", "AMS", Timestamp.from(now), Timestamp.from(now)
            );
            insert(
                connection,
                "INSERT INTO athletes (id, first_name, last_name, date_of_birth, gender, club_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                athleteId, "Ada", "Lovelace", java.sql.Date.valueOf("1990-12-10"), "F", clubId, Timestamp.from(now), Timestamp.from(now)
            );
            insert(
                connection,
                "INSERT INTO categories (id, name, gender, is_global, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                categoryId, "Junior Women", "F", false, Timestamp.from(now), Timestamp.from(now)
            );
            insert(
                connection,
                "INSERT INTO boat_types (id, code, name, rowers, coxswain, sculling, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                boatTypeId, boatTypeCode, "Quad Scull", 4, false, true, Timestamp.from(now), Timestamp.from(now)
            );
            insert(
                connection,
                "INSERT INTO blocks (id, regatta_id, name, start_time, event_interval_seconds, crew_interval_seconds, display_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                blockId, regattaId, "Morning Session", Timestamp.from(now), 300, 60, 1, Timestamp.from(now), Timestamp.from(now)
            );
        }

        return new SeedData(regattaId, clubId, athleteId, categoryId, boatTypeId, blockId);
    }

    private UUID seedEvent(SeedData seed) throws Exception {
        UUID eventId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            insert(
                connection,
                "INSERT INTO events (id, regatta_id, event_group_id, category_id, boat_type_id, name, display_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                eventId, seed.regattaId, null, seed.categoryId, seed.boatTypeId, "JW4x", 1, Timestamp.from(Instant.parse("2026-03-10T12:00:00Z")), Timestamp.from(Instant.parse("2026-03-10T12:00:00Z"))
            );
        }
        return eventId;
    }

    private UUID seedCrew(SeedData seed) throws Exception {
        UUID crewId = UUID.randomUUID();
        UUID crewAthleteId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            insert(
                connection,
                "INSERT INTO crews (id, display_name, is_composite, club_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                crewId, "Amstel Crew", false, seed.clubId, Timestamp.from(Instant.parse("2026-03-10T12:00:00Z")), Timestamp.from(Instant.parse("2026-03-10T12:00:00Z"))
            );
            insert(
                connection,
                "INSERT INTO crew_athletes (id, crew_id, athlete_id, seat_position) VALUES (?, ?, ?, ?)",
                crewAthleteId, crewId, seed.athleteId, 1
            );
            insert(
                connection,
                "INSERT INTO regatta_crews (regatta_id, crew_id, created_at) VALUES (?, ?, ?)",
                seed.regattaId, crewId, Timestamp.from(Instant.parse("2026-03-10T12:00:00Z"))
            );
        }
        return crewId;
    }

    private UUID seedEntry(SeedData seed, UUID eventId, UUID crewId) throws Exception {
        UUID entryId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            insert(
                connection,
                "INSERT INTO entries (id, regatta_id, event_id, block_id, crew_id, billing_club_id, status, payment_status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entryId, seed.regattaId, eventId, seed.blockId, crewId, seed.clubId, "entered", "unpaid", Timestamp.from(Instant.parse("2026-03-10T12:00:00Z")), Timestamp.from(Instant.parse("2026-03-10T12:00:00Z"))
            );
        }
        return entryId;
    }

    private void insert(Connection connection, String sql, Object... values) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }

    private record SeedData(
        UUID regattaId,
        UUID clubId,
        UUID athleteId,
        UUID categoryId,
        UUID boatTypeId,
        UUID blockId
    ) {
    }
}
