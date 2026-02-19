package com.regattadesk.operator;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for operator token API endpoints.
 * 
 * These tests verify the complete token lifecycle:
 * - Token creation with valid parameters
 * - Token listing for a regatta
 * - Token revocation
 * - Authorization enforcement (requires REGATTA_ADMIN or SUPER_ADMIN)
 */
@QuarkusTest
class OperatorTokenResourceIT {
    
    @Test
    void testCreateToken_Success() {
        UUID regattaId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant validFrom = now;
        Instant validUntil = now.plus(8, ChronoUnit.HOURS);
        
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body(String.format("""
                {
                    "station": "start-line",
                    "valid_from": "%s",
                    "valid_until": "%s"
                }
                """, validFrom, validUntil))
            .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/tokens")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("regatta_id", equalTo(regattaId.toString()))
            .body("station", equalTo("start-line"))
            .body("token", notNullValue())
            .body("pin", notNullValue())
            .body("is_active", equalTo(true))
            .body("valid_from", notNullValue())
            .body("valid_until", notNullValue());
    }
    
    @Test
    void testCreateToken_WithBlockId() {
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant validFrom = now;
        Instant validUntil = now.plus(8, ChronoUnit.HOURS);
        
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "regatta_admin")
            .contentType("application/json")
            .body(String.format("""
                {
                    "block_id": "%s",
                    "station": "finish-line",
                    "valid_from": "%s",
                    "valid_until": "%s"
                }
                """, blockId, validFrom, validUntil))
            .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/tokens")
            .then()
            .statusCode(201)
            .body("block_id", equalTo(blockId.toString()))
            .body("station", equalTo("finish-line"));
    }
    
    @Test
    void testCreateToken_MissingStation() {
        UUID regattaId = UUID.randomUUID();
        Instant now = Instant.now();
        
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body(String.format("""
                {
                    "valid_from": "%s",
                    "valid_until": "%s"
                }
                """, now, now.plus(1, ChronoUnit.HOURS)))
            .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/tokens")
            .then()
            .statusCode(400);
    }
    
    @Test
    void testCreateToken_InvalidValidityWindow() {
        UUID regattaId = UUID.randomUUID();
        Instant now = Instant.now();
        
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body(String.format("""
                {
                    "station": "start-line",
                    "valid_from": "%s",
                    "valid_until": "%s"
                }
                """, now, now.minus(1, ChronoUnit.HOURS)))
            .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/tokens")
            .then()
            .statusCode(400)
            .body("error.message", containsString("after"));
    }
    
    @Test
    void testCreateToken_Unauthorized() {
        UUID regattaId = UUID.randomUUID();
        Instant now = Instant.now();
        
        given()
            .header("Remote-User", "operator")
            .header("Remote-Groups", "operator")
            .contentType("application/json")
            .body(String.format("""
                {
                    "station": "start-line",
                    "valid_from": "%s",
                    "valid_until": "%s"
                }
                """, now, now.plus(1, ChronoUnit.HOURS)))
            .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/tokens")
            .then()
            .statusCode(403);
    }
    
    @Test
    void testListTokens_Success() {
        UUID regattaId = UUID.randomUUID();
        // First create a token
        Instant now = Instant.now();
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body(String.format("""
                {
                    "station": "test-station",
                    "valid_from": "%s",
                    "valid_until": "%s"
                }
                """, now, now.plus(1, ChronoUnit.HOURS)))
            .post("/api/v1/regattas/" + regattaId + "/operator/tokens");
        
        // List tokens
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .when()
            .get("/api/v1/regattas/" + regattaId + "/operator/tokens")
            .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("data", not(empty()));
    }
    
    @Test
    void testListTokens_Unauthorized() {
        UUID regattaId = UUID.randomUUID();
        given()
            .header("Remote-User", "user")
            .header("Remote-Groups", "info_desk")
            .when()
            .get("/api/v1/regattas/" + regattaId + "/operator/tokens")
            .then()
            .statusCode(403);
    }
    
    @Test
    void testRevokeToken_Success() {
        UUID regattaId = UUID.randomUUID();
        // First create a token
        Instant now = Instant.now();
        String tokenId = given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body(String.format("""
                {
                    "station": "revoke-test",
                    "valid_from": "%s",
                    "valid_until": "%s"
                }
                """, now, now.plus(1, ChronoUnit.HOURS)))
            .post("/api/v1/regattas/" + regattaId + "/operator/tokens")
            .then()
            .statusCode(201)
            .extract()
            .path("id");
        
        // Revoke the token
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/tokens/" + tokenId + "/revoke")
            .then()
            .statusCode(200)
            .body("message", containsString("revoked"));
    }
    
    @Test
    void testRevokeToken_NotFound() {
        UUID regattaId = UUID.randomUUID();
        UUID nonExistentTokenId = UUID.randomUUID();
        
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/tokens/" + nonExistentTokenId + "/revoke")
            .then()
            .statusCode(404)
            .body("error.message", containsString("not found"));
    }
    
    @Test
    void testRevokeToken_WrongRegatta() {
        // Create a token in one regatta
        UUID regatta1 = UUID.randomUUID();
        Instant now = Instant.now();
        String tokenId = given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body(String.format("""
                {
                    "station": "test",
                    "valid_from": "%s",
                    "valid_until": "%s"
                }
                """, now, now.plus(1, ChronoUnit.HOURS)))
            .post("/api/v1/regattas/" + regatta1 + "/operator/tokens")
            .then()
            .statusCode(201)
            .extract()
            .path("id");
        
        // Try to revoke it from a different regatta
        UUID regatta2 = UUID.randomUUID();
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .when()
            .post("/api/v1/regattas/" + regatta2 + "/operator/tokens/" + tokenId + "/revoke")
            .then()
            .statusCode(404)
            .body("error.message", containsString("not found"));
    }
    
    @Test
    void testRevokeToken_Unauthorized() {
        UUID regattaId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        
        given()
            .header("Remote-User", "user")
            .header("Remote-Groups", "operator")
            .contentType("application/json")
            .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/tokens/" + tokenId + "/revoke")
            .then()
            .statusCode(403);
    }

    @Test
    void testRevokeToken_Idempotent() {
        UUID regattaId = UUID.randomUUID();
        Instant now = Instant.now();
        String tokenId = given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body(String.format("""
                {
                    "station": "idempotent-test",
                    "valid_from": "%s",
                    "valid_until": "%s"
                }
                """, now, now.plus(1, ChronoUnit.HOURS)))
            .post("/api/v1/regattas/" + regattaId + "/operator/tokens")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .post("/api/v1/regattas/" + regattaId + "/operator/tokens/" + tokenId + "/revoke")
            .then()
            .statusCode(200);

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .post("/api/v1/regattas/" + regattaId + "/operator/tokens/" + tokenId + "/revoke")
            .then()
            .statusCode(200);
    }
}
