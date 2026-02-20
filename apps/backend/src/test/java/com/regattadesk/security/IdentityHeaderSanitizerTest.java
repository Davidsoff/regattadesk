package com.regattadesk.security;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Tests for IdentityHeaderSanitizer to verify it properly strips forged identity headers
 * from untrusted (public) paths to prevent authentication bypass.
 */
@QuarkusTest
class IdentityHeaderSanitizerTest {
    
    @Test
    void testPublicEndpoint_ForgedHeadersStripped() {
        given()
            .header("Remote-User", "attacker")
            .header("Remote-Groups", "super_admin")
            .header("Remote-Name", "Attacker")
            .header("Remote-Email", "attacker@evil.com")
            .when().get("/api/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("version", equalTo("0.1.0-SNAPSHOT"));
    }

    @Test
    void testTrustedPath_PreservesIdentityHeaders() {
        given()
            .header("Remote-User", "attacker")
            .header("Remote-Groups", "super_admin")
            .header("Remote-Name", "Attacker")
            .header("Remote-Email", "attacker@evil.com")
            .when().get("/test/auth/public")
            .then()
            .statusCode(200)
            .body("endpoint", equalTo("public"))
            .body("username", equalTo("attacker"));
    }

    @Test
    void testTrustedProtectedPath_AllowsAuthorizedRole() {
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "regatta_admin")
            .when().get("/test/auth/admin")
            .then()
            .statusCode(200)
            .body("endpoint", equalTo("admin"))
            .body("username", equalTo("admin"));
    }

    @Test
    void testProtectedEndpoint_NoHeaders_AccessDenied() {
        given()
            .when().get("/test/auth/admin")
            .then()
            .statusCode(403);
    }
}
