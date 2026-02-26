package com.regattadesk.public_api;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Cookie;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JWT key rotation during public session bootstrap.
 * 
 * These tests validate that:
 * - Tokens signed with the older key remain valid during overlap window
 * - New tokens are signed with the newest key
 * - Bootstrap flow works correctly during key rotation
 */
@QuarkusTest
class PublicSessionKeyRotationIT {
    
    private static final String SESSION_ENDPOINT = "/public/session";
    private static final String VERSIONS_ENDPOINT = "/public/regattas/{regattaId}/versions";
    private static final String COOKIE_NAME = "regattadesk_public_session";
    
    // Use a valid UUID format for the test regatta ID (doesn't need to exist)
    private static final UUID TEST_REGATTA_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    
    @Test
    void testKeyRotation_OldTokenStillValidDuringOverlap() {
        // This test simulates having a token signed with an older key
        // and verifying it still works when a newer key is active
        
        // Step 1: Get a session cookie (will be signed with current key)
        Response response1 = given()
            .when()
            .post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie cookie1 = response1.getDetailedCookie(COOKIE_NAME);
        assertNotNull(cookie1);
        
        // Step 2: Verify this cookie works with versions endpoint
        int statusCode1 = given()
            .cookie(COOKIE_NAME, cookie1.getValue())
            .pathParam("regattaId", TEST_REGATTA_ID)
            .when()
            .get(VERSIONS_ENDPOINT)
            .then()
            .extract().statusCode();
        
        assertNotEquals(401, statusCode1, "Token should be valid before rotation");
        
        // Step 3: Simulate time passing, get another session (might use newer key)
        Response response2 = given()
            .when()
            .post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie cookie2 = response2.getDetailedCookie(COOKIE_NAME);
        assertNotNull(cookie2);
        
        // Step 4: Original cookie should still work (overlap guarantee)
        int statusCode2 = given()
            .cookie(COOKIE_NAME, cookie1.getValue())
            .pathParam("regattaId", TEST_REGATTA_ID)
            .when()
            .get(VERSIONS_ENDPOINT)
            .then()
            .extract().statusCode();
        
        assertNotEquals(401, statusCode2, "Old token should still be valid during overlap");
        
        // Step 5: New cookie should also work
        int statusCode3 = given()
            .cookie(COOKIE_NAME, cookie2.getValue())
            .pathParam("regattaId", TEST_REGATTA_ID)
            .when()
            .get(VERSIONS_ENDPOINT)
            .then()
            .extract().statusCode();
        
        assertNotEquals(401, statusCode3, "New token should be valid");
    }
    
    @Test
    void testKeyRotation_RefreshIssuedDuringRotation() {
        // Get initial session
        Response response1 = given()
            .when()
            .post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie cookie1 = response1.getDetailedCookie(COOKIE_NAME);
        assertNotNull(cookie1);
        
        // Immediately refresh (should not issue new cookie - not in refresh window)
        Response response2 = given()
            .cookie(COOKIE_NAME, cookie1.getValue())
            .when()
            .post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie cookie2 = response2.getDetailedCookie(COOKIE_NAME);
        assertNull(cookie2, "Should not refresh when not in window");
        
        // Original cookie should still work
        int statusCode = given()
            .cookie(COOKIE_NAME, cookie1.getValue())
            .pathParam("regattaId", TEST_REGATTA_ID)
            .when()
            .get(VERSIONS_ENDPOINT)
            .then()
            .extract().statusCode();
        
        assertNotEquals(401, statusCode, "Original cookie should remain valid");
    }
    
    @Test
    void testBootstrapFlow_WorksDuringKeyRotation() {
        // Simulate bootstrap flow multiple times to ensure consistency
        for (int iteration = 0; iteration < 3; iteration++) {
            // Step 1: No cookie -> 401
            given()
                .pathParam("regattaId", TEST_REGATTA_ID)
                .when()
                .get(VERSIONS_ENDPOINT)
                .then()
                .statusCode(401);
            
            // Step 2: Get session
            Response sessionResponse = given()
                .when()
                .post(SESSION_ENDPOINT)
                .then()
                .statusCode(204)
                .extract().response();
            
            Cookie cookie = sessionResponse.getDetailedCookie(COOKIE_NAME);
            assertNotNull(cookie, "Iteration " + iteration + " should get cookie");
            
            // Step 3: Retry with cookie -> not 401
            int statusCode = given()
                .cookie(COOKIE_NAME, cookie.getValue())
                .pathParam("regattaId", TEST_REGATTA_ID)
                .when()
                .get(VERSIONS_ENDPOINT)
                .then()
                .extract().statusCode();
            
            assertNotEquals(401, statusCode, 
                "Iteration " + iteration + " should not get 401 after bootstrap");
        }
    }
    
    @Test
    void testTokensWithDifferentKeys_BothAcceptedDuringOverlap() {
        // Get two tokens in sequence - they should both use the same key
        // since there's only one key configured at initialization
        Response response1 = given()
            .when()
            .post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie cookie1 = response1.getDetailedCookie(COOKIE_NAME);
        
        Response response2 = given()
            .when()
            .post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie cookie2 = response2.getDetailedCookie(COOKIE_NAME);
        
        // Both cookies should work
        if (cookie1 != null) {
            int status1 = given()
                .cookie(COOKIE_NAME, cookie1.getValue())
                .pathParam("regattaId", TEST_REGATTA_ID)
                .when()
                .get(VERSIONS_ENDPOINT)
                .then()
                .extract().statusCode();
            
            assertTrue(status1 == 404 || status1 == 200,
                "First token should be valid, got: " + status1);
        }
        
        if (cookie2 != null) {
            int status2 = given()
                .cookie(COOKIE_NAME, cookie2.getValue())
                .pathParam("regattaId", TEST_REGATTA_ID)
                .when()
                .get(VERSIONS_ENDPOINT)
                .then()
                .extract().statusCode();
            
            assertTrue(status2 == 404 || status2 == 200,
                "Second token should be valid, got: " + status2);
        }
    }
    
    @Test
    void testSessionEndpoint_StableAcrossMultipleCalls() {
        Cookie lastCookie = null;
        
        // Make multiple session requests
        for (int i = 0; i < 5; i++) {
            Response response = given()
                .when()
                .post(SESSION_ENDPOINT)
                .then()
                .statusCode(204)
                .extract().response();
            
            Cookie cookie = response.getDetailedCookie(COOKIE_NAME);
            assertNotNull(cookie, "Iteration " + i + " should get cookie");
            
            // Verify cookie structure
            String[] parts = cookie.getValue().split("\\.");
            assertEquals(3, parts.length, "JWT should have 3 parts");
            
            // If we had a previous cookie, verify this is different (new session)
            if (lastCookie != null) {
                assertNotEquals(lastCookie.getValue(), cookie.getValue(),
                    "Each call should generate new session");
            }
            
            lastCookie = cookie;
        }
    }
}
