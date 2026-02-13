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
    void testSecureHealthEndpoint_NoAuth() {
        // Secure endpoint should return 403 without authentication
        given()
            .when().get("/api/health/secure")
            .then()
            .statusCode(403);
    }
    
    @Test
    void testSecureHealthEndpoint_WithRegattaAdmin() {
        // Should succeed with REGATTA_ADMIN role
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "regatta_admin")
            .when().get("/api/health/secure")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("version", equalTo("0.1.0-SNAPSHOT"))
            .body("authenticatedUser", equalTo("admin"));
    }
    
    @Test
    void testSecureHealthEndpoint_WithSuperAdmin() {
        // Should succeed with SUPER_ADMIN role
        given()
            .header("Remote-User", "superadmin")
            .header("Remote-Groups", "super_admin")
            .when().get("/api/health/secure")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("authenticatedUser", equalTo("superadmin"));
    }
    
    @Test
    void testSecureHealthEndpoint_WithWrongRole() {
        // Should fail with wrong role (e.g., OPERATOR)
        given()
            .header("Remote-User", "operator")
            .header("Remote-Groups", "operator")
            .when().get("/api/health/secure")
            .then()
            .statusCode(403);
    }
}
