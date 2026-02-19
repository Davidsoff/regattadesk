package com.regattadesk.ruleset;

import com.regattadesk.eventstore.EventMetadata;
import com.regattadesk.eventstore.EventStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class RulesetResourceIT {

    @Inject
    EventStore eventStore;

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
            .when()
            .get("/api/v1/rulesets/" + rulesetId)
            .then()
            .statusCode(200)
            .body("id", equalTo(rulesetId.toString()))
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

    @Test
    void get_missingRuleset_shouldReturnNotFoundErrorShape() {
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .when()
            .get("/api/v1/rulesets/" + UUID.randomUUID())
            .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"))
            .body("error.message", equalTo("Ruleset not found"));
    }

    @Test
    void update_missingRuleset_shouldReturnNotFoundErrorShape() {
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "name": "missing",
                  "version": "v1"
                }
                """)
            .when()
            .patch("/api/v1/rulesets/" + UUID.randomUUID())
            .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"))
            .body("error.message", equalTo("Ruleset not found"));
    }

    @Test
    void create_invalidAgeCalculationType_shouldReturnBadRequestErrorShape() {
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "name": "Invalid Age Type",
                  "version": "v1.0",
                  "description": "desc",
                  "age_calculation_type": "not_supported"
                }
                """)
            .when()
            .post("/api/v1/rulesets")
            .then()
            .statusCode(400)
            .body("error.code", equalTo("BAD_REQUEST"))
            .body("error.message", equalTo("Age calculation type must be 'actual_at_start' or 'age_as_of_jan_1'"));
    }

    @Test
    void update_invalidAgeCalculationType_shouldReturnBadRequestErrorShape() {
        UUID rulesetId = UUID.randomUUID();

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body(String.format("""
                {
                  "id": "%s",
                  "name": "Valid Ruleset",
                  "version": "v1.0",
                  "description": "desc",
                  "age_calculation_type": "actual_at_start"
                }
                """, rulesetId))
            .when()
            .post("/api/v1/rulesets")
            .then()
            .statusCode(201);

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "age_calculation_type": "bad_value"
                }
                """)
            .when()
            .patch("/api/v1/rulesets/" + rulesetId)
            .then()
            .statusCode(400)
            .body("error.code", equalTo("BAD_REQUEST"))
            .body("error.message", equalTo("Age calculation type must be 'actual_at_start' or 'age_as_of_jan_1'"));
    }

    @Test
    void listRulesets_shouldReturnDataArrayContract() {
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .when()
            .get("/api/v1/rulesets")
            .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("data", hasSize(0));
    }

    @Test
    void update_publishedRuleset_shouldReturnConflict() {
        UUID rulesetId = UUID.randomUUID();
        RulesetCreatedEvent createdEvent = new RulesetCreatedEvent(
            rulesetId, "Published Ruleset", "v1.0", "desc", "actual_at_start", false
        );
        RulesetDrawPublishedEvent publishedEvent = new RulesetDrawPublishedEvent(rulesetId);

        eventStore.append(
            rulesetId,
            "Ruleset",
            -1,
            java.util.List.of(createdEvent),
            EventMetadata.builder().build()
        );
        eventStore.append(
            rulesetId,
            "Ruleset",
            1,
            java.util.List.of(publishedEvent),
            EventMetadata.builder().build()
        );

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "description": "should fail"
                }
                """)
            .when()
            .patch("/api/v1/rulesets/" + rulesetId)
            .then()
            .statusCode(409)
            .body("error.code", equalTo("CONFLICT"))
            .body("error.message", equalTo("Cannot update ruleset after draw publication"));
    }

    @Test
    void duplicate_publishedRuleset_shouldReturnConflict() {
        UUID rulesetId = UUID.randomUUID();
        RulesetCreatedEvent createdEvent = new RulesetCreatedEvent(
            rulesetId, "Published Ruleset", "v1.0", "desc", "actual_at_start", false
        );
        RulesetDrawPublishedEvent publishedEvent = new RulesetDrawPublishedEvent(rulesetId);

        eventStore.append(
            rulesetId,
            "Ruleset",
            -1,
            java.util.List.of(createdEvent),
            EventMetadata.builder().build()
        );
        eventStore.append(
            rulesetId,
            "Ruleset",
            1,
            java.util.List.of(publishedEvent),
            EventMetadata.builder().build()
        );

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "new_name": "copy",
                  "new_version": "v2.0"
                }
                """)
            .when()
            .post("/api/v1/rulesets/" + rulesetId + "/duplicate")
            .then()
            .statusCode(409)
            .body("error.code", equalTo("CONFLICT"))
            .body("error.message", equalTo("Cannot duplicate ruleset after draw publication"));
    }
}
