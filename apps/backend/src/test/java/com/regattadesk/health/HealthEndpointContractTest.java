package com.regattadesk.health;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Contract test example demonstrating API contract verification.
 * 
 * This is a placeholder showing the pattern for Pact contract tests.
 * In a full implementation with Pact:
 * 
 * 1. Consumer side (Frontend/Operator/Public apps) would define expectations:
 *    - Using Pact consumer DSL
 *    - Generate pact files
 *    - Store in pacts/ directory or Pact Broker
 * 
 * 2. Provider side (Backend) would verify contracts:
 *    - @Provider("regattadesk-backend")
 *    - @PactFolder("pacts") or @PactBroker
 *    - @State annotations for setup
 *    - Verify all consumer expectations
 * 
 * Example consumer contract (conceptual):
 * ```
 * pact.given("health endpoint is available")
 *     .uponReceiving("a request for health status")
 *     .path("/api/health")
 *     .method("GET")
 *     .willRespondWith()
 *     .status(200)
 *     .body(new PactDslJsonBody()
 *         .stringValue("status", "healthy"));
 * ```
 * 
 * This test serves as a basic contract verification pattern
 * until full Pact integration is implemented.
 */
@QuarkusTest
class HealthEndpointContractTest {

    @Test
    void healthEndpointShouldReturnExpectedContract() {
        // Verify the health endpoint returns the expected structure
        given()
            .when()
            .get("/api/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))
            .body("checks.database", equalTo("ok"))
            .body("checks.eventstore", equalTo("ok"));
    }

    @Test
    void healthEndpointShouldReturnJsonContentType() {
        given()
            .when()
            .get("/api/health")
            .then()
            .statusCode(200)
            .contentType("application/json");
    }

    @Test
    void healthEndpointShouldNotRequireAuthentication() {
        // Health endpoint should be publicly accessible
        given()
            .when()
            .get("/api/health")
            .then()
            .statusCode(200);
    }

    @Test
    void quarkusHealthEndpointShouldFollowMicroProfileHealthSpec() {
        // Verify Quarkus health endpoint follows spec
        given()
            .when()
            .get("/q/health")
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("status", equalTo("UP"));
    }
}
