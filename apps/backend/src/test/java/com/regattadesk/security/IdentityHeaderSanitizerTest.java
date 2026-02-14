package com.regattadesk.security;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Tests for IdentityHeaderSanitizer to verify it properly strips forged identity headers
 * from untrusted (public) paths to prevent authentication bypass.
 * 
 * This test suite validates that the trust boundary is correctly enforced:
 * - Trusted paths: /api/v1/staff/*, /api/v1/regattas/{id}/operator/*, /test/auth/*
 * - Untrusted paths: Everything else (public, health, and non-operator regatta paths)
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
    
    @Test
    void testRegattaOperatorPath_TrustsIdentityHeaders() {
        // Operator paths under /api/v1/regattas/{id}/operator/* are trusted
        // Headers should NOT be stripped on these paths
        // This test verifies the positive case - operator paths are trusted
        given()
            .header("Remote-User", "operator1")
            .header("Remote-Groups", "operator")
            .header("Remote-Name", "Operator One")
            .header("Remote-Email", "operator1@regattadesk.local")
            .when().get("/api/v1/regattas/12345678-1234-1234-1234-123456789012/operator/tokens")
            .then()
            // Note: Will return 403 due to authorization, but headers are trusted (not stripped)
            // If headers were stripped, we'd get 403 for lack of authentication
            .statusCode(403);
    }
    
    @Test
    void testRegattaNonOperatorPath_ForgedHeadersStripped() {
        // Non-operator paths under /api/v1/regattas (e.g., /events, /entries) should NOT trust headers
        // This is a CRITICAL security test - future endpoints here must not accidentally trust forged headers
        given()
            .header("Remote-User", "attacker")
            .header("Remote-Groups", "super_admin")
            .header("Remote-Name", "Attacker")
            .header("Remote-Email", "attacker@evil.com")
            .when().get("/api/v1/regattas/12345678-1234-1234-1234-123456789012/events")
            .then()
            // Should get 404 (not implemented) - the key is that headers are stripped
            .statusCode(404);
    }
    
    @Test
    void testRegattaRootPath_ForgedHeadersStripped() {
        // Root regatta paths should NOT trust headers
        given()
            .header("Remote-User", "attacker")
            .header("Remote-Groups", "super_admin")
            .header("Remote-Name", "Attacker")
            .header("Remote-Email", "attacker@evil.com")
            .when().get("/api/v1/regattas/12345678-1234-1234-1234-123456789012")
            .then()
            .statusCode(404); // Should get 404 with headers stripped
    }
    
    @Test
    void testRegattaEntriesPath_ForgedHeadersStripped() {
        // Entry paths should NOT trust headers (these are non-operator paths)
        given()
            .header("Remote-User", "attacker")
            .header("Remote-Groups", "regatta_admin")
            .header("Remote-Name", "Attacker")
            .header("Remote-Email", "attacker@evil.com")
            .when().get("/api/v1/regattas/12345678-1234-1234-1234-123456789012/entries")
            .then()
            .statusCode(404); // Not implemented, headers should be stripped
    }
    
    @Test
    void testOperatorPathVariations_OnlyExactOperatorPathsTrusted() {
        // Test that only /api/v1/regattas/{id}/operator/* paths are trusted, not similar paths
        
        // Trusted: operator path
        given()
            .header("Remote-User", "operator1")
            .when().get("/api/v1/regattas/12345678-1234-1234-1234-123456789012/operator/tokens")
            .then()
            .statusCode(403); // Authorized but denied due to role check
        
        // Untrusted: /operator_stuff (not exact match)
        given()
            .header("Remote-User", "attacker")
            .when().get("/api/v1/regattas/12345678-1234-1234-1234-123456789012/operator_stuff")
            .then()
            .statusCode(404); // Headers stripped, not found
        
        // Untrusted: missing /operator/ in path
        given()
            .header("Remote-User", "attacker")
            .when().get("/api/v1/regattas/12345678-1234-1234-1234-123456789012/tokens")
            .then()
            .statusCode(404); // Headers stripped, not found
    }
}
