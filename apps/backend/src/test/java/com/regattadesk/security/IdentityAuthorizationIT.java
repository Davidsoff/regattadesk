package com.regattadesk.security;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Integration tests for identity extraction and role-based authorization.
 * 
 * These tests verify the complete request processing pipeline including:
 * - Identity header extraction from forwarded headers
 * - Principal injection into SecurityContext
 * - Role-based authorization enforcement
 * - Proper handling of authenticated and unauthenticated requests
 */
@QuarkusTest
class IdentityAuthorizationIT {
    
    @Test
    void testPublicEndpoint_NoHeaders() {
        // Public endpoint should work without identity headers
        given()
            .when().get("/test/auth/public")
            .then()
            .statusCode(200)
            .body("endpoint", equalTo("public"))
            .body("username", equalTo("anonymous"));
    }
    
    @Test
    void testPublicEndpoint_WithHeaders() {
        // Public endpoint should work with identity headers (user info visible)
        given()
            .header("Remote-User", "testuser")
            .header("Remote-Name", "Test User")
            .header("Remote-Email", "test@example.com")
            .header("Remote-Groups", "regatta_admin")
            .when().get("/test/auth/public")
            .then()
            .statusCode(200)
            .body("endpoint", equalTo("public"))
            .body("username", equalTo("testuser"));
    }
    
    @Test
    void testProtectedEndpoint_NoHeaders() {
        // Protected endpoint should return 403 without identity headers
        given()
            .when().get("/test/auth/admin")
            .then()
            .statusCode(403);
    }
    
    @Test
    void testProtectedEndpoint_WithCorrectRole() {
        // Should succeed with correct role
        given()
            .header("Remote-User", "admin")
            .header("Remote-Name", "Admin User")
            .header("Remote-Email", "admin@example.com")
            .header("Remote-Groups", "regatta_admin")
            .when().get("/test/auth/admin")
            .then()
            .statusCode(200)
            .body("endpoint", equalTo("admin"))
            .body("username", equalTo("admin"));
    }
    
    @Test
    void testProtectedEndpoint_WithWrongRole() {
        // Should fail with wrong role
        given()
            .header("Remote-User", "operator")
            .header("Remote-Name", "Operator User")
            .header("Remote-Email", "operator@example.com")
            .header("Remote-Groups", "operator")
            .when().get("/test/auth/admin")
            .then()
            .statusCode(403);
    }
    
    @Test
    void testProtectedEndpoint_MultipleRolesAllowed() {
        // Endpoint allows INFO_DESK or REGATTA_ADMIN
        
        // Test with INFO_DESK role
        given()
            .header("Remote-User", "deskuser")
            .header("Remote-Groups", "info_desk")
            .when().get("/test/auth/desk")
            .then()
            .statusCode(200)
            .body("endpoint", equalTo("desk"))
            .body("username", equalTo("deskuser"));
        
        // Test with REGATTA_ADMIN role
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "regatta_admin")
            .when().get("/test/auth/desk")
            .then()
            .statusCode(200)
            .body("endpoint", equalTo("desk"))
            .body("username", equalTo("admin"));
    }
    
    @Test
    void testProtectedEndpoint_UserWithMultipleRoles() {
        // User with multiple roles should be authorized if one matches
        given()
            .header("Remote-User", "multiuser")
            .header("Remote-Groups", "operator,info_desk,financial_manager")
            .when().get("/test/auth/desk")
            .then()
            .statusCode(200)
            .body("endpoint", equalTo("desk"))
            .body("username", equalTo("multiuser"));
    }
    
    @Test
    void testSuperAdminEndpoint_WithSuperAdmin() {
        // Super admin should access super admin endpoint
        given()
            .header("Remote-User", "superadmin")
            .header("Remote-Groups", "super_admin")
            .when().get("/test/auth/super")
            .then()
            .statusCode(200)
            .body("endpoint", equalTo("super"))
            .body("username", equalTo("superadmin"));
    }
    
    @Test
    void testSuperAdminEndpoint_WithRegularAdmin() {
        // Regular admin should not access super admin endpoint
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "regatta_admin")
            .when().get("/test/auth/super")
            .then()
            .statusCode(403);
    }
    
    @Test
    void testMalformedHeaders_BlankUser() {
        // Blank user header should fail authorization
        given()
            .header("Remote-User", "   ")
            .header("Remote-Groups", "regatta_admin")
            .when().get("/test/auth/admin")
            .then()
            .statusCode(403);
    }
    
    @Test
    void testHeaderParsing_WhitespaceHandling() {
        // Headers with extra whitespace should be handled gracefully
        given()
            .header("Remote-User", "  testuser  ")
            .header("Remote-Name", "  Test User  ")
            .header("Remote-Email", "  test@example.com  ")
            .header("Remote-Groups", " regatta_admin , info_desk ")
            .when().get("/test/auth/admin")
            .then()
            .statusCode(200)
            .body("username", equalTo("testuser"));
    }
    
    @Test
    void testRoleAuthorization_AllDefinedRoles() {
        // Test that all role types can be parsed and validated
        String[] roles = {
            "super_admin", "regatta_admin", "head_of_jury",
            "info_desk", "financial_manager", "operator"
        };
        
        for (String role : roles) {
            given()
                .header("Remote-User", "user_" + role)
                .header("Remote-Groups", role)
                .when().get("/test/auth/public")
                .then()
                .statusCode(200)
                .body("username", equalTo("user_" + role));
        }
    }
}
