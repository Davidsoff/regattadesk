package com.regattadesk.health;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class HealthResourceTest {
    
    @Test
    void testPublicHealthEndpoint() {
        // Public health endpoint should work without authentication
        given()
            .when().get("/api/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("version", equalTo("0.1.0-SNAPSHOT"));
    }
    
    @Test
    void testPublicHealthEndpoint_IgnoresForgedHeaders() {
        // Public health endpoint should ignore forged identity headers
        // The sanitizer strips them before the backend sees them
        given()
            .header("Remote-User", "attacker")
            .header("Remote-Groups", "super_admin")
            .when().get("/api/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("version", equalTo("0.1.0-SNAPSHOT"));
    }
}
