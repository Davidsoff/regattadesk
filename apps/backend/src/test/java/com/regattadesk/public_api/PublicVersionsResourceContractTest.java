package com.regattadesk.public_api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Cookie;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import javax.sql.DataSource;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Contract tests for PublicVersionsResource.
 * 
 * Verifies API contract compliance including:
 * - Response payload structure matches OpenAPI specification
 * - HTTP status codes for all scenarios
 * - Required headers (Cache-Control)
 * - Content-Type headers
 * 
 * These tests serve as contract documentation and will be integrated with
 * Pact consumer-driven contract testing in future iterations.
 */
@QuarkusTest
@Tag("contract")
class PublicVersionsResourceContractTest {
    
    private static final String COOKIE_NAME = "regattadesk_public_session";
    private static final String SESSION_ENDPOINT = "/public/session";
    
    @Inject
    DataSource dataSource;
    
    private UUID testRegattaId;
    private Cookie validSessionCookie;
    
    @BeforeEach
    void setUp() throws Exception {
        testRegattaId = UUID.randomUUID();
        
        // Insert test regatta
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO regattas (id, name, description, time_zone, status, entry_fee, currency, " +
                 "draw_revision, results_revision, created_at, updated_at) " +
                 "VALUES (?, 'Contract Test Regatta', 'Test', 'Europe/Amsterdam', 'published', 25.00, 'EUR', 2, 4, now(), now())"
             )) {
            stmt.setObject(1, testRegattaId);
            stmt.executeUpdate();
        }
        
        // Get a valid session cookie for tests
        Response sessionResponse = given()
            .when().post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        validSessionCookie = sessionResponse.getDetailedCookie(COOKIE_NAME);
    }
    
    @Test
    void contract_GET_versions_returns_200_with_valid_session() {
        given()
            .cookie(COOKIE_NAME, validSessionCookie.getValue())
        .when()
            .get("/public/regattas/" + testRegattaId + "/versions")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .header("Cache-Control", "no-store, must-revalidate")
            .body("draw_revision", isA(Integer.class))
            .body("results_revision", isA(Integer.class))
            .body("draw_revision", greaterThanOrEqualTo(0))
            .body("results_revision", greaterThanOrEqualTo(0));
    }
    
    @Test
    void contract_GET_versions_returns_401_without_session() {
        given()
            // No session cookie
        .when()
            .get("/public/regattas/" + testRegattaId + "/versions")
        .then()
            .statusCode(401)
            .header("Cache-Control", "no-store, must-revalidate");
    }
    
    @Test
    void contract_GET_versions_returns_401_with_invalid_session() {
        given()
            .cookie(COOKIE_NAME, "invalid.token.value")
        .when()
            .get("/public/regattas/" + testRegattaId + "/versions")
        .then()
            .statusCode(401)
            .header("Cache-Control", "no-store, must-revalidate");
    }
    
    @Test
    void contract_GET_versions_returns_404_for_nonexistent_regatta() {
        UUID nonExistentId = UUID.randomUUID();
        
        given()
            .cookie(COOKIE_NAME, validSessionCookie.getValue())
        .when()
            .get("/public/regattas/" + nonExistentId + "/versions")
        .then()
            .statusCode(404)
            .header("Cache-Control", "no-store, must-revalidate");
    }
    
    @Test
    void contract_response_has_exactly_required_fields() {
        given()
            .cookie(COOKIE_NAME, validSessionCookie.getValue())
        .when()
            .get("/public/regattas/" + testRegattaId + "/versions")
        .then()
            .statusCode(200)
            .body("size()", equalTo(2))
            .body("$", hasKey("draw_revision"))
            .body("$", hasKey("results_revision"));
    }
    
    @Test
    void contract_cache_control_must_prevent_caching() {
        given()
            .cookie(COOKIE_NAME, validSessionCookie.getValue())
        .when()
            .get("/public/regattas/" + testRegattaId + "/versions")
        .then()
            .statusCode(200)
            .header("Cache-Control", containsString("no-store"))
            .header("Cache-Control", containsString("must-revalidate"));
    }
    
    @Test
    void contract_bootstrap_retry_flow() {
        // Step 1: Initial request without session returns 401
        given()
        .when()
            .get("/public/regattas/" + testRegattaId + "/versions")
        .then()
            .statusCode(401)
            .header("Cache-Control", "no-store, must-revalidate");
        
        // Step 2: Create session
        Response sessionResponse = given()
        .when()
            .post(SESSION_ENDPOINT)
        .then()
            .statusCode(204)
            .extract().response();
        
        Cookie newSessionCookie = sessionResponse.getDetailedCookie(COOKIE_NAME);
        
        // Step 3: Retry with session returns 200
        given()
            .cookie(COOKIE_NAME, newSessionCookie.getValue())
        .when()
            .get("/public/regattas/" + testRegattaId + "/versions")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .header("Cache-Control", "no-store, must-revalidate")
            .body("draw_revision", isA(Integer.class))
            .body("results_revision", isA(Integer.class));
    }
    
    @Test
    void contract_field_types_match_openapi_spec() {
        given()
            .cookie(COOKIE_NAME, validSessionCookie.getValue())
        .when()
            .get("/public/regattas/" + testRegattaId + "/versions")
        .then()
            .statusCode(200)
            // OpenAPI spec: type: integer, minimum: 0
            .body("draw_revision", isA(Integer.class))
            .body("results_revision", isA(Integer.class))
            .body("draw_revision", greaterThanOrEqualTo(0))
            .body("results_revision", greaterThanOrEqualTo(0));
    }
}
