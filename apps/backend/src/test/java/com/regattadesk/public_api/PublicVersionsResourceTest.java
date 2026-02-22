package com.regattadesk.public_api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Cookie;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PublicVersionsResource.
 * 
 * Tests the /public/regattas/{regatta_id}/versions endpoint including:
 * - 200 success flow with valid session
 * - 401 unauthorized flow when session is missing or invalid
 * - Bootstrap retry scenario (401 -> create session -> retry -> 200)
 * - Cache-Control headers
 */
@QuarkusTest
class PublicVersionsResourceTest {
    
    private static final String COOKIE_NAME = "regattadesk_public_session";
    private static final String SESSION_ENDPOINT = "/public/session";
    
    @Inject
    DataSource dataSource;
    
    private UUID testRegattaId;
    
    @BeforeEach
    void setUp() throws Exception {
        testRegattaId = UUID.randomUUID();
        
        // Insert test regatta with known revisions
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO regattas (id, name, description, time_zone, status, entry_fee, currency, " +
                 "draw_revision, results_revision, created_at, updated_at) " +
                 "VALUES (?, 'Test Regatta', 'Test Description', 'Europe/Amsterdam', 'published', 25.00, 'EUR', 3, 5, now(), now())"
             )) {
            stmt.setObject(1, testRegattaId);
            stmt.executeUpdate();
        }
    }
    
    @Test
    void testGetVersions_WithValidSession() {
        // First, get a valid session cookie
        Response sessionResponse = given()
            .when().post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie sessionCookie = sessionResponse.getDetailedCookie(COOKIE_NAME);
        assertNotNull(sessionCookie);
        
        // Now request versions with valid session
        given()
            .cookie(COOKIE_NAME, sessionCookie.getValue())
            .when().get("/public/regattas/" + testRegattaId + "/versions")
            .then()
            .statusCode(200)
            .header("Cache-Control", "no-store, must-revalidate")
            .contentType("application/json")
            .body("draw_revision", equalTo(3))
            .body("results_revision", equalTo(5));
    }
    
    @Test
    void testGetVersions_WithoutSession() {
        // Request without session cookie should return 401
        given()
            .when().get("/public/regattas/" + testRegattaId + "/versions")
            .then()
            .statusCode(401)
            .header("Cache-Control", "no-store, must-revalidate");
    }
    
    @Test
    void testGetVersions_WithInvalidSession() {
        // Request with invalid session cookie should return 401
        given()
            .cookie(COOKIE_NAME, "invalid.jwt.token")
            .when().get("/public/regattas/" + testRegattaId + "/versions")
            .then()
            .statusCode(401)
            .header("Cache-Control", "no-store, must-revalidate");
    }
    
    @Test
    void testGetVersions_WithEmptySession() {
        // Request with empty session cookie should return 401
        given()
            .cookie(COOKIE_NAME, "")
            .when().get("/public/regattas/" + testRegattaId + "/versions")
            .then()
            .statusCode(401)
            .header("Cache-Control", "no-store, must-revalidate");
    }
    
    @Test
    void testGetVersions_NotFound() {
        // First, get a valid session cookie
        Response sessionResponse = given()
            .when().post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie sessionCookie = sessionResponse.getDetailedCookie(COOKIE_NAME);
        assertNotNull(sessionCookie);
        
        // Request versions for non-existent regatta
        UUID nonExistentId = UUID.randomUUID();
        given()
            .cookie(COOKIE_NAME, sessionCookie.getValue())
            .when().get("/public/regattas/" + nonExistentId + "/versions")
            .then()
            .statusCode(404)
            .header("Cache-Control", "no-store, must-revalidate");
    }
    
    @Test
    void testBootstrapRetryScenario() {
        // Step 1: Call /versions without session - should return 401
        Response initialResponse = given()
            .when().get("/public/regattas/" + testRegattaId + "/versions")
            .then()
            .statusCode(401)
            .extract().response();
        
        // Step 2: Call /public/session to get a session cookie
        Response sessionResponse = given()
            .when().post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie sessionCookie = sessionResponse.getDetailedCookie(COOKIE_NAME);
        assertNotNull(sessionCookie, "Session cookie should be created");
        
        // Step 3: Retry /versions with the new session cookie - should succeed
        given()
            .cookie(COOKIE_NAME, sessionCookie.getValue())
            .when().get("/public/regattas/" + testRegattaId + "/versions")
            .then()
            .statusCode(200)
            .header("Cache-Control", "no-store, must-revalidate")
            .contentType("application/json")
            .body("draw_revision", equalTo(3))
            .body("results_revision", equalTo(5));
    }
    
    @Test
    void testCacheControlHeader() {
        // Get a valid session
        Response sessionResponse = given()
            .when().post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie sessionCookie = sessionResponse.getDetailedCookie(COOKIE_NAME);
        
        // Verify Cache-Control header is always set to no-store, must-revalidate
        given()
            .cookie(COOKIE_NAME, sessionCookie.getValue())
            .when().get("/public/regattas/" + testRegattaId + "/versions")
            .then()
            .statusCode(200)
            .header("Cache-Control", equalTo("no-store, must-revalidate"));
    }
    
    @Test
    void testResponseStructure() {
        // Get a valid session
        Response sessionResponse = given()
            .when().post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie sessionCookie = sessionResponse.getDetailedCookie(COOKIE_NAME);
        
        // Verify response structure matches OpenAPI spec
        given()
            .cookie(COOKIE_NAME, sessionCookie.getValue())
            .when().get("/public/regattas/" + testRegattaId + "/versions")
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("$", hasKey("draw_revision"))
            .body("$", hasKey("results_revision"))
            .body("draw_revision", isA(Integer.class))
            .body("results_revision", isA(Integer.class))
            .body("draw_revision", greaterThanOrEqualTo(0))
            .body("results_revision", greaterThanOrEqualTo(0));
    }
}
