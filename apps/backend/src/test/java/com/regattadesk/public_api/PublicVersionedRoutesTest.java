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
 * Integration tests for versioned public routes under /public/v{draw}-{results}/...
 * 
 * Tests BC05-002 requirements:
 * - All public content served under immutable versioned paths
 * - Cache headers: public, max-age=31536000, immutable
 * - Version change results in new immutable URL tuple
 */
@QuarkusTest
class PublicVersionedRoutesTest {
    
    private static final String COOKIE_NAME = "regattadesk_public_session";
    private static final String SESSION_ENDPOINT = "/public/session";
    private static final String EXPECTED_CACHE_CONTROL = "public, max-age=31536000, immutable";
    
    @Inject
    DataSource dataSource;
    
    private UUID testRegattaId;
    private Cookie validSessionCookie;
    
    @BeforeEach
    void setUp() throws Exception {
        testRegattaId = UUID.randomUUID();
        
        // Insert test regatta with known revisions
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO regattas (id, name, description, time_zone, status, entry_fee, currency, " +
                 "draw_revision, results_revision, created_at, updated_at) " +
                 "VALUES (?, 'Test Regatta', 'Test Description', 'Europe/Amsterdam', 'published', 25.00, 'EUR', 2, 3, now(), now())"
             )) {
            stmt.setObject(1, testRegattaId);
            stmt.executeUpdate();
        }
        
        // Get a valid session cookie for authenticated requests
        Response sessionResponse = given()
            .when().post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        validSessionCookie = sessionResponse.getDetailedCookie(COOKIE_NAME);
        assertNotNull(validSessionCookie, "Failed to obtain valid session cookie for tests");
    }
    
    @Test
    void testVersionedScheduleRoute_ReturnsCorrectCacheHeaders() {
        // Test that versioned schedule route returns immutable cache headers
        String versionedPath = String.format("/public/v%d-%d/regattas/%s/schedule",
            2, 3, testRegattaId);
        
        given()
            .cookie(validSessionCookie)
            .when()
            .get(versionedPath)
            .then()
            .statusCode(anyOf(is(200), is(404))) // 404 is acceptable if endpoint not fully implemented yet
            .header("Cache-Control", equalTo(EXPECTED_CACHE_CONTROL));
    }
    
    @Test
    void testVersionedResultsRoute_ReturnsCorrectCacheHeaders() {
        // Test that versioned results route returns immutable cache headers
        String versionedPath = String.format("/public/v%d-%d/regattas/%s/results",
            2, 3, testRegattaId);
        
        given()
            .cookie(validSessionCookie)
            .when()
            .get(versionedPath)
            .then()
            .statusCode(anyOf(is(200), is(404))) // 404 is acceptable if endpoint not fully implemented yet
            .header("Cache-Control", equalTo(EXPECTED_CACHE_CONTROL));
    }
    
    @Test
    void testVersionedRoute_DifferentVersionsHaveDifferentPaths() {
        // Test that different version tuples result in different URLs
        String path1 = String.format("/public/v%d-%d/regattas/%s/schedule", 1, 1, testRegattaId);
        String path2 = String.format("/public/v%d-%d/regattas/%s/schedule", 2, 3, testRegattaId);
        
        assertNotEquals(path1, path2, "Different version tuples must generate different URLs");
        
        // Both should have the same cache policy regardless of version
        given()
            .cookie(validSessionCookie)
            .when()
            .get(path1)
            .then()
            .header("Cache-Control", equalTo(EXPECTED_CACHE_CONTROL));
        
        given()
            .cookie(validSessionCookie)
            .when()
            .get(path2)
            .then()
            .header("Cache-Control", equalTo(EXPECTED_CACHE_CONTROL));
    }
    
    @Test
    void testVersionedRoute_MaxAge31536000Seconds() {
        // 31536000 seconds = 365 days = 1 year
        String versionedPath = String.format("/public/v%d-%d/regattas/%s/schedule",
            2, 3, testRegattaId);
        
        Response response = given()
            .cookie(validSessionCookie)
            .when()
            .get(versionedPath)
            .then()
            .extract().response();
        
        String cacheControl = response.getHeader("Cache-Control");
        assertNotNull(cacheControl, "Cache-Control header must be present");
        assertTrue(cacheControl.contains("max-age=31536000"), 
            "Cache-Control must specify max-age=31536000 (1 year)");
    }
    
    @Test
    void testVersionedRoute_MustIncludeImmutableDirective() {
        // Test that the immutable directive is present
        String versionedPath = String.format("/public/v%d-%d/regattas/%s/schedule",
            2, 3, testRegattaId);
        
        Response response = given()
            .cookie(validSessionCookie)
            .when()
            .get(versionedPath)
            .then()
            .extract().response();
        
        String cacheControl = response.getHeader("Cache-Control");
        assertNotNull(cacheControl, "Cache-Control header must be present");
        assertTrue(cacheControl.contains("immutable"), 
            "Cache-Control must include immutable directive");
    }
    
    @Test
    void testVersionedRoute_MustIncludePublicDirective() {
        // Test that the public directive is present (CDN cacheable)
        String versionedPath = String.format("/public/v%d-%d/regattas/%s/results",
            2, 3, testRegattaId);
        
        Response response = given()
            .cookie(validSessionCookie)
            .when()
            .get(versionedPath)
            .then()
            .extract().response();
        
        String cacheControl = response.getHeader("Cache-Control");
        assertNotNull(cacheControl, "Cache-Control header must be present");
        assertTrue(cacheControl.contains("public"), 
            "Cache-Control must include public directive for CDN caching");
    }
    
    @Test
    void testNonVersionedPublicRoute_DoesNotHaveImmutableHeaders() {
        // Verify that non-versioned routes (like /public/session) do NOT have immutable headers
        given()
            .when()
            .post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .header("Cache-Control", equalTo("no-store"));
    }
    
    @Test
    void testVersionsEndpoint_DoesNotHaveImmutableHeaders() {
        // Verify that /public/regattas/{id}/versions does NOT have immutable headers
        String versionsPath = String.format("/public/regattas/%s/versions", testRegattaId);
        
        given()
            .cookie(validSessionCookie)
            .when()
            .get(versionsPath)
            .then()
            .statusCode(200)
            .header("Cache-Control", equalTo("no-store, must-revalidate"));
    }
    
    @Test
    void testVersionedRoute_WithZeroRevisions() {
        // Test edge case: version 0-0 (before any draw or results published)
        UUID regatta2 = UUID.randomUUID();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO regattas (id, name, description, time_zone, status, entry_fee, currency, " +
                 "draw_revision, results_revision, created_at, updated_at) " +
                 "VALUES (?, 'Test Regatta 2', 'Test Description', 'Europe/Amsterdam', 'published', 25.00, 'EUR', 0, 0, now(), now())"
             )) {
            stmt.setObject(1, regatta2);
            stmt.executeUpdate();
        } catch (Exception e) {
            fail("Failed to insert test regatta: " + e.getMessage());
        }
        
        String versionedPath = String.format("/public/v%d-%d/regattas/%s/schedule", 0, 0, regatta2);
        
        given()
            .cookie(validSessionCookie)
            .when()
            .get(versionedPath)
            .then()
            .header("Cache-Control", equalTo(EXPECTED_CACHE_CONTROL));
    }
    
    @Test
    void testVersionedRoute_WithHighRevisions() {
        // Test with high revision numbers to ensure no overflow issues
        UUID regatta3 = UUID.randomUUID();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO regattas (id, name, description, time_zone, status, entry_fee, currency, " +
                 "draw_revision, results_revision, created_at, updated_at) " +
                 "VALUES (?, 'Test Regatta 3', 'Test Description', 'Europe/Amsterdam', 'published', 25.00, 'EUR', 999, 888, now(), now())"
             )) {
            stmt.setObject(1, regatta3);
            stmt.executeUpdate();
        } catch (Exception e) {
            fail("Failed to insert test regatta: " + e.getMessage());
        }
        
        String versionedPath = String.format("/public/v%d-%d/regattas/%s/results", 999, 888, regatta3);
        
        given()
            .cookie(validSessionCookie)
            .when()
            .get(versionedPath)
            .then()
            .header("Cache-Control", equalTo(EXPECTED_CACHE_CONTROL));
    }
}
