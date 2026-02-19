package com.regattadesk.athlete;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AthleteResourceIT {

    @Test
    void createGetPatchDelete_shouldRoundTrip() {
        String athleteId = given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "first_name": "Ada",
                  "middle_name": "M",
                  "last_name": "Lovelace",
                  "date_of_birth": "1990-12-10",
                  "gender": "F"
                }
                """)
            .when()
            .post("/api/v1/athletes")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("first_name", equalTo("Ada"))
            .body("last_name", equalTo("Lovelace"))
            .body("date_of_birth", equalTo("1990-12-10"))
            .body("gender", equalTo("F"))
            .extract()
            .path("id");

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .when()
            .get("/api/v1/athletes/" + athleteId)
            .then()
            .statusCode(200)
            .body("id", equalTo(athleteId))
            .body("first_name", equalTo("Ada"))
            .body("last_name", equalTo("Lovelace"))
            .body("date_of_birth", equalTo("1990-12-10"))
            .body("gender", equalTo("F"));

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "middle_name": "Byron",
                  "date_of_birth": "1991-01-01"
                }
                """)
            .when()
            .patch("/api/v1/athletes/" + athleteId)
            .then()
            .statusCode(200)
            .body("id", equalTo(athleteId))
            .body("middle_name", equalTo("Byron"))
            .body("first_name", equalTo("Ada"))
            .body("last_name", equalTo("Lovelace"))
            .body("date_of_birth", equalTo("1991-01-01"))
            .body("gender", equalTo("F"));

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .when()
            .delete("/api/v1/athletes/" + athleteId)
            .then()
            .statusCode(204);

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .when()
            .get("/api/v1/athletes/" + athleteId)
            .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    void list_federationParametersMustBePaired() {
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .queryParam("federation_code", "FISA")
            .when()
            .get("/api/v1/athletes")
            .then()
            .statusCode(400)
            .body("error.code", equalTo("BAD_REQUEST"))
            .body(
                "error.message",
                equalTo("Both federation_code and federation_external_id must be provided together or both omitted")
            );
    }

    @Test
    void list_shouldReturnPaginationContractAndIncludeCreatedAthlete() {
        String uniqueLastName = "ListContract-" + java.util.UUID.randomUUID();

        String athleteId = given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body(String.format("""
                {
                  "first_name": "Grace",
                  "last_name": "%s",
                  "date_of_birth": "1985-03-14",
                  "gender": "F"
                }
                """, uniqueLastName))
            .when()
            .post("/api/v1/athletes")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .queryParam("search", uniqueLastName)
            .when()
            .get("/api/v1/athletes")
            .then()
            .statusCode(200)
            .body("data.size()", greaterThanOrEqualTo(1))
            .body("data.id", hasItem(athleteId))
            .body("pagination.has_more", equalTo(false))
            .body("pagination.next_cursor", equalTo(null));
    }

    @Test
    void update_missingAthlete_shouldReturnNotFoundErrorShape() {
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "first_name": "Missing"
                }
                """)
            .when()
            .patch("/api/v1/athletes/" + java.util.UUID.randomUUID())
            .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"))
            .body("error.message", equalTo("Athlete not found"));
    }

    @Test
    void create_futureDateOfBirth_shouldReturnBadRequest() {
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "first_name": "Future",
                  "last_name": "Athlete",
                  "date_of_birth": "2999-01-01",
                  "gender": "M"
                }
                """)
            .when()
            .post("/api/v1/athletes")
            .then()
            .statusCode(400)
            .body("error.code", equalTo("BAD_REQUEST"))
            .body("error.message", equalTo("Date of birth cannot be in the future"));
    }

    @Test
    void delete_missingAthlete_shouldReturnNotFoundErrorShape() {
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .when()
            .delete("/api/v1/athletes/" + java.util.UUID.randomUUID())
            .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"))
            .body("error.message", equalTo("Athlete not found"));
    }

    @Test
    void get_withoutAuthHeaders_shouldBeForbidden() {
        given()
            .when()
            .get("/api/v1/athletes")
            .then()
            .statusCode(403);
    }
}
