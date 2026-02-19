package com.regattadesk.athlete;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
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
            .body("first_name", equalTo("Ada"));

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
            .body("date_of_birth", equalTo("1991-01-01"));

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
            .body("error.code", equalTo("BAD_REQUEST"));
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
