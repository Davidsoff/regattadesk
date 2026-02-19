package com.regattadesk.ruleset;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class RulesetResourceIT {

    @Test
    void createGetPatchAndDuplicate_shouldRoundTripThroughEventStore() {
        UUID rulesetId = UUID.randomUUID();

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body(String.format("""
                {
                  "id": "%s",
                  "name": "Initial Ruleset",
                  "version": "v1.0",
                  "description": "Initial description",
                  "age_calculation_type": "actual_at_start"
                }
                """, rulesetId))
            .when()
            .post("/api/v1/rulesets")
            .then()
            .statusCode(201)
            .body("id", equalTo(rulesetId.toString()))
            .body("name", equalTo("Initial Ruleset"))
            .body("version", equalTo("v1.0"))
            .body("description", equalTo("Initial description"))
            .body("age_calculation_type", equalTo("actual_at_start"));

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .when()
            .get("/api/v1/rulesets/" + rulesetId)
            .then()
            .statusCode(200)
            .body("id", equalTo(rulesetId.toString()))
            .body("name", equalTo("Initial Ruleset"))
            .body("version", equalTo("v1.0"));

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "description": "Updated description",
                  "age_calculation_type": "age_as_of_jan_1"
                }
                """)
            .when()
            .patch("/api/v1/rulesets/" + rulesetId)
            .then()
            .statusCode(200)
            .body("id", equalTo(rulesetId.toString()))
            .body("name", equalTo("Initial Ruleset"))
            .body("version", equalTo("v1.0"))
            .body("description", equalTo("Updated description"))
            .body("age_calculation_type", equalTo("age_as_of_jan_1"));

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "new_name": "Duplicated Ruleset",
                  "new_version": "v2.0"
                }
                """)
            .when()
            .post("/api/v1/rulesets/" + rulesetId + "/duplicate")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("name", equalTo("Duplicated Ruleset"))
            .body("version", equalTo("v2.0"))
            .body("description", equalTo("Updated description"))
            .body("age_calculation_type", equalTo("age_as_of_jan_1"));
    }

    @Test
    void create_withProvidedId_shouldBeIdempotent() {
        UUID rulesetId = UUID.randomUUID();

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body(String.format("""
                {
                  "id": "%s",
                  "name": "Idempotent Ruleset",
                  "version": "v1.0",
                  "description": "First write",
                  "age_calculation_type": "actual_at_start"
                }
                """, rulesetId))
            .when()
            .post("/api/v1/rulesets")
            .then()
            .statusCode(201)
            .body("id", equalTo(rulesetId.toString()))
            .body("name", equalTo("Idempotent Ruleset"));

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body(String.format("""
                {
                  "id": "%s",
                  "name": "Conflicting Retry Name",
                  "version": "v9.9",
                  "description": "Retry write",
                  "age_calculation_type": "age_as_of_jan_1"
                }
                """, rulesetId))
            .when()
            .post("/api/v1/rulesets")
            .then()
            .statusCode(201)
            .body("id", equalTo(rulesetId.toString()))
            .body("name", equalTo("Idempotent Ruleset"))
            .body("version", equalTo("v1.0"));
    }

    @Test
    void get_withoutAuthHeaders_shouldBeForbidden() {
        given()
            .when()
            .get("/api/v1/rulesets/" + UUID.randomUUID())
            .then()
            .statusCode(403);
    }

    @Test
    void duplicate_missingSource_shouldReturnNotFoundErrorShape() {
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "new_name": "No Source",
                  "new_version": "v1.0"
                }
                """)
            .when()
            .post("/api/v1/rulesets/" + UUID.randomUUID() + "/duplicate")
            .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"))
            .body("error.message", equalTo("Ruleset not found"));
    }
}
