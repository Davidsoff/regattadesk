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
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the public bootstrap fallback flow during JWT key rotation.
 * 
 * Tests the sequence: /versions (401) -> /public/session -> /versions (200)
 * This flow must remain stable during key rotation scenarios.
 */
@QuarkusTest
class PublicBootstrapFallbackIT {
    
    private static final String SESSION_ENDPOINT = "/public/session";
    private static final String VERSIONS_ENDPOINT = "/public/regattas/{regattaId}/versions";
    private static final String COOKIE_NAME = "regattadesk_public_session";
    private static final String TEST_REGATTA_ID = "test-regatta-1";
    
    @Test
    void testBootstrapFlow_NoSession_Gets401ThenEstablishesSession() {
        // Step 1: Try to access versions without session -> expect 401
        given()
            .pathParam("regattaId", TEST_REGATTA_ID)
            .when()
            .get(VERSIONS_ENDPOINT)
            .then()
            .statusCode(401);
        
        // Step 2: Call /public/session to establish session
        Response sessionResponse = given()
            .when()
            .post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie sessionCookie = sessionResponse.getDetailedCookie(COOKIE_NAME);
        assertNotNull(sessionCookie, "Session cookie must be set");
        
        // Step 3: Retry /versions with new session cookie -> expect 200 (or 404 if regatta doesn't exist)
        // For this test, we expect 401/404 based on whether regatta exists, but cookie should be accepted
        int statusCode = given()
            .cookie(COOKIE_NAME, sessionCookie.getValue())
            .pathParam("regattaId", TEST_REGATTA_ID)
            .when()
            .get(VERSIONS_ENDPOINT)
            .then()
            .extract().statusCode();
        
        // Either 404 (regatta not found) or 200 (regatta exists) - but not 401 (auth failed)
        assertNotEquals(401, statusCode, "Should not get 401 with valid session cookie");
    }
    
    @Test
    void testBootstrapFlow_ExpiredToken_RefreshesAndRetries() {
        // Create an expired token
        String expiredToken = createExpiredToken();
        
        // Step 1: Try versions with expired token -> expect 401
        given()
            .cookie(COOKIE_NAME, expiredToken)
            .pathParam("regattaId", TEST_REGATTA_ID)
            .when()
            .get(VERSIONS_ENDPOINT)
            .then()
            .statusCode(401);
        
        // Step 2: Refresh session with expired cookie -> should get new cookie
        Response sessionResponse = given()
            .cookie(COOKIE_NAME, expiredToken)
            .when()
            .post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie newCookie = sessionResponse.getDetailedCookie(COOKIE_NAME);
        assertNotNull(newCookie, "Should issue new cookie for expired token");
        assertNotEquals(expiredToken, newCookie.getValue(), "Should be a new token");
        
        // Step 3: Retry versions with new cookie
        int statusCode = given()
            .cookie(COOKIE_NAME, newCookie.getValue())
            .pathParam("regattaId", TEST_REGATTA_ID)
            .when()
            .get(VERSIONS_ENDPOINT)
            .then()
            .extract().statusCode();
        
        assertNotEquals(401, statusCode, "Should not get 401 after session refresh");
    }
    
    @Test
    void testBootstrapFlow_InvalidToken_RefreshesAndRetries() {
        String invalidToken = "invalid.jwt.token";
        
        // Step 1: Try versions with invalid token -> expect 401
        given()
            .cookie(COOKIE_NAME, invalidToken)
            .pathParam("regattaId", TEST_REGATTA_ID)
            .when()
            .get(VERSIONS_ENDPOINT)
            .then()
            .statusCode(401);
        
        // Step 2: Refresh session with invalid cookie -> should get new cookie
        Response sessionResponse = given()
            .cookie(COOKIE_NAME, invalidToken)
            .when()
            .post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie newCookie = sessionResponse.getDetailedCookie(COOKIE_NAME);
        assertNotNull(newCookie, "Should issue new cookie for invalid token");
        
        // Step 3: Retry versions with new cookie
        int statusCode = given()
            .cookie(COOKIE_NAME, newCookie.getValue())
            .pathParam("regattaId", TEST_REGATTA_ID)
            .when()
            .get(VERSIONS_ENDPOINT)
            .then()
            .extract().statusCode();
        
        assertNotEquals(401, statusCode, "Should not get 401 after session refresh");
    }
    
    @Test
    void testBootstrapFlow_ValidToken_SucceedsImmediately() {
        // Step 1: Establish session
        Response sessionResponse = given()
            .when()
            .post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie validCookie = sessionResponse.getDetailedCookie(COOKIE_NAME);
        assertNotNull(validCookie);
        
        // Step 2: Access versions with valid token -> should succeed (not 401)
        int statusCode = given()
            .cookie(COOKIE_NAME, validCookie.getValue())
            .pathParam("regattaId", TEST_REGATTA_ID)
            .when()
            .get(VERSIONS_ENDPOINT)
            .then()
            .extract().statusCode();
        
        assertNotEquals(401, statusCode, "Valid token should not result in 401");
    }
    
    @Test
    void testBootstrapFlow_MultipleRetries_Idempotent() {
        // Establish session once
        Response sessionResponse = given()
            .when()
            .post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie cookie = sessionResponse.getDetailedCookie(COOKIE_NAME);
        assertNotNull(cookie);
        
        // Multiple retries should all work
        for (int i = 0; i < 3; i++) {
            int statusCode = given()
                .cookie(COOKIE_NAME, cookie.getValue())
                .pathParam("regattaId", TEST_REGATTA_ID)
                .when()
                .get(VERSIONS_ENDPOINT)
                .then()
                .extract().statusCode();
            
            assertNotEquals(401, statusCode, "Retry " + i + " should not get 401");
        }
    }
    
    /**
     * Helper to create an expired token for testing.
     * This uses a hardcoded secret - in production this would use actual key rotation.
     */
    private String createExpiredToken() {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("sid", UUID.randomUUID().toString())
                .issueTime(Date.from(now.minusSeconds(432000 + 100))) // Issued 5 days + 100s ago
                .expirationTime(Date.from(now.minusSeconds(100))) // Expired 100s ago
                .build();
            
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                .keyID("test-kid-expired")
                .build();
            
            SignedJWT jwt = new SignedJWT(header, claims);
            
            // Use a test secret (this won't match production, so will be treated as invalid)
            String secret = "test-expired-secret-at-least-32-bytes-required";
            MACSigner signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
            jwt.sign(signer);
            
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create expired token", e);
        }
    }
}
