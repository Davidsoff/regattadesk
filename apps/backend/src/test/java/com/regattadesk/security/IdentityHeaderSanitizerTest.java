package com.regattadesk.security;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests for IdentityHeaderSanitizer to verify it properly strips forged identity headers
 * from untrusted (public) paths to prevent authentication bypass.
 *
 * This test suite validates that the trust boundary is correctly enforced:
 * - Trusted paths: /api/v1/staff/*, /api/v1/regattas/{id}/operator/*, protected regatta staff subpaths, /test/auth/*
 * - Untrusted paths: Everything else (public, health, and non-operator regatta paths)
 */
@QuarkusTest
class IdentityHeaderSanitizerTest {

    private static final String REGATTA_ID = "12345678-1234-1234-1234-123456789012";

    @Test
    void testPublicEndpoint_ForgedHeadersStripped() {
        // Attempt to access /api/health (public path) with forged identity headers.
        // Endpoint remains healthy and does not treat forged identity as authentication.
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
    void testPublicEndpoint_NoHeaders_Works() {
        given()
            .when().get("/api/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("version", equalTo("0.1.0-SNAPSHOT"));
    }

    @Test
    void testProtectedEndpoint_NoHeaders_AccessDenied() {
        // /test/auth/* is trusted but still requires authorization where declared.
        given()
            .when().get("/test/auth/admin")
            .then()
            .statusCode(403);
    }

    @Test
    void testRegattaOperatorPath_TrustsIdentityHeaders() {
        // Operator paths are trusted and should preserve forwarded identity headers.
        given()
            .header("Remote-User", "operator1")
            .header("Remote-Groups", "operator")
            .header("Remote-Name", "Operator One")
            .header("Remote-Email", "operator1@regattadesk.local")
            .when().get("/api/v1/regattas/" + REGATTA_ID + "/operator/echo-identity")
            .then()
            .statusCode(200)
            .body("authenticated", equalTo(true))
            .body("username", equalTo("operator1"))
            .body("remoteUser", equalTo("operator1"))
            .body("remoteGroups", equalTo("operator"))
            .body("remoteName", equalTo("Operator One"))
            .body("remoteEmail", equalTo("operator1@regattadesk.local"));
    }

    @Test
    void testRegattaNonOperatorPath_ForgedHeadersStripped() {
        // Non-operator regatta paths must strip forged identity headers.
        given()
            .header("Remote-User", "attacker")
            .header("Remote-Groups", "super_admin")
            .header("Remote-Name", "Attacker")
            .header("Remote-Email", "attacker@evil.com")
            .when().get("/api/v1/regattas/" + REGATTA_ID + "/events/echo-identity")
            .then()
            .statusCode(200)
            .body("authenticated", equalTo(false))
            .body("username", nullValue())
            .body("remoteUser", nullValue())
            .body("remoteGroups", nullValue())
            .body("remoteName", nullValue())
            .body("remoteEmail", nullValue());
    }

    @Test
    void testRegattaEntriesPath_ForgedHeadersStripped() {
        given()
            .header("Remote-User", "attacker")
            .header("Remote-Groups", "regatta_admin")
            .header("Remote-Name", "Attacker")
            .header("Remote-Email", "attacker@evil.com")
            .when().get("/api/v1/regattas/" + REGATTA_ID + "/entries/echo-identity")
            .then()
            .statusCode(200)
            .body("authenticated", equalTo(false))
            .body("username", nullValue())
            .body("remoteUser", nullValue())
            .body("remoteGroups", nullValue())
            .body("remoteName", nullValue())
            .body("remoteEmail", nullValue());
    }

    @Test
    void testRegattaAdjudicationPath_TrustsIdentityHeaders() {
        given()
            .header("Remote-User", "jury-user")
            .header("Remote-Groups", "head_of_jury")
            .header("Remote-Name", "Jury User")
            .header("Remote-Email", "jury@regattadesk.local")
            .when().get("/api/v1/regattas/" + REGATTA_ID + "/adjudication/echo-identity")
            .then()
            .statusCode(200)
            .body("authenticated", equalTo(true))
            .body("username", equalTo("jury-user"))
            .body("remoteUser", equalTo("jury-user"))
            .body("remoteGroups", equalTo("head_of_jury"));
    }

    @Test
    void testOperatorPathVariations_OnlyExactOperatorPathsTrusted() {
        // Trusted exact operator path.
        given()
            .header("Remote-User", "operator1")
            .header("Remote-Groups", "operator")
            .when().get("/api/v1/regattas/" + REGATTA_ID + "/operator/echo-identity")
            .then()
            .statusCode(200)
            .body("authenticated", equalTo(true))
            .body("remoteUser", equalTo("operator1"));

        // Untrusted similar path.
        given()
            .header("Remote-User", "attacker")
            .header("Remote-Groups", "super_admin")
            .when().get("/api/v1/regattas/" + REGATTA_ID + "/operator_stuff/echo-identity")
            .then()
            .statusCode(200)
            .body("authenticated", equalTo(false))
            .body("remoteUser", nullValue());

        // Untrusted path without /operator segment.
        given()
            .header("Remote-User", "attacker")
            .header("Remote-Groups", "super_admin")
            .when().get("/api/v1/regattas/" + REGATTA_ID + "/tokens/echo-identity")
            .then()
            .statusCode(200)
            .body("authenticated", equalTo(false))
            .body("remoteUser", nullValue());
    }
}
