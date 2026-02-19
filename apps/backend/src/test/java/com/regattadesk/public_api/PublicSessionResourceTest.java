package com.regattadesk.public_api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Cookie;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PublicSessionResource.
 */
@QuarkusTest
class PublicSessionResourceTest {
    
    private static final String COOKIE_NAME = "regattadesk_public_session";
    private static final String ENDPOINT = "/public/session";
    
    @Test
    void testCreateSession_NoExistingCookie() {
        Response response = given()
            .when().post(ENDPOINT)
            .then()
            .statusCode(204)
            .header("Cache-Control", "no-store")
            .extract().response();
        
        // Verify cookie is set
        Cookie cookie = response.getDetailedCookie(COOKIE_NAME);
        assertNotNull(cookie, "Cookie should be set");
        assertNotNull(cookie.getValue(), "Cookie value should not be null");
        assertFalse(cookie.getValue().isEmpty(), "Cookie value should not be empty");
        assertEquals("/", cookie.getPath());
        assertEquals(432000, cookie.getMaxAge()); // 5 days
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecured());
        assertEquals("Lax", cookie.getSameSite());
    }
    
    @Test
    void testRefreshSession_ValidCookieNotInRefreshWindow() {
        // First, get a fresh cookie
        Response createResponse = given()
            .when().post(ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie firstCookie = createResponse.getDetailedCookie(COOKIE_NAME);
        assertNotNull(firstCookie);
        
        // Immediately use the cookie again - should not refresh since it's fresh
        Response refreshResponse = given()
            .cookie(COOKIE_NAME, firstCookie.getValue())
            .when().post(ENDPOINT)
            .then()
            .statusCode(204)
            .header("Cache-Control", "no-store")
            .extract().response();
        
        // Cookie should NOT be set again since it's not in refresh window
        Cookie secondCookie = refreshResponse.getDetailedCookie(COOKIE_NAME);
        assertNull(secondCookie, "Cookie should not be refreshed when not in refresh window");
    }
    
    @Test
    void testRefreshSession_InvalidCookie() {
        // Use an invalid cookie
        Response response = given()
            .cookie(COOKIE_NAME, "invalid.jwt.token")
            .when().post(ENDPOINT)
            .then()
            .statusCode(204)
            .header("Cache-Control", "no-store")
            .extract().response();
        
        // Should issue a new cookie
        Cookie cookie = response.getDetailedCookie(COOKIE_NAME);
        assertNotNull(cookie, "New cookie should be issued for invalid token");
        assertNotNull(cookie.getValue());
        assertFalse(cookie.getValue().isEmpty());
    }
    
    @Test
    void testRefreshSession_EmptyCookie() {
        // Use an empty cookie
        Response response = given()
            .cookie(COOKIE_NAME, "")
            .when().post(ENDPOINT)
            .then()
            .statusCode(204)
            .header("Cache-Control", "no-store")
            .extract().response();
        
        // Should issue a new cookie
        Cookie cookie = response.getDetailedCookie(COOKIE_NAME);
        assertNotNull(cookie, "New cookie should be issued for empty cookie");
        assertNotNull(cookie.getValue());
        assertFalse(cookie.getValue().isEmpty());
    }
    
    @Test
    void testCookieSecurityAttributes() {
        Response response = given()
            .when().post(ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie cookie = response.getDetailedCookie(COOKIE_NAME);
        
        // Verify all security attributes
        assertTrue(cookie.isHttpOnly(), "Cookie must be HttpOnly");
        assertTrue(cookie.isSecured(), "Cookie must be Secure");
        assertEquals("Lax", cookie.getSameSite(), "Cookie must have SameSite=Lax");
        assertEquals("/", cookie.getPath(), "Cookie path must be /");
        assertEquals(432000, cookie.getMaxAge(), "Cookie Max-Age must be 5 days (432000 seconds)");
    }
    
    @Test
    void testMultipleRequestsGenerateDifferentSessions() {
        // Create two sessions without cookies
        Response response1 = given()
            .when().post(ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Response response2 = given()
            .when().post(ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie cookie1 = response1.getDetailedCookie(COOKIE_NAME);
        Cookie cookie2 = response2.getDetailedCookie(COOKIE_NAME);
        
        assertNotNull(cookie1);
        assertNotNull(cookie2);
        assertNotEquals(cookie1.getValue(), cookie2.getValue(), 
            "Different requests should generate different session tokens");
    }
    
    @Test
    void testCacheControlHeader() {
        given()
            .when().post(ENDPOINT)
            .then()
            .statusCode(204)
            .header("Cache-Control", equalTo("no-store"));
    }
    
    @Test
    void testIdempotentRefresh_SameValidCookie() {
        // Get initial cookie
        Response response1 = given()
            .when().post(ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        Cookie cookie1 = response1.getDetailedCookie(COOKIE_NAME);
        assertNotNull(cookie1);
        
        // Use the same cookie immediately (not in refresh window)
        Response response2 = given()
            .cookie(COOKIE_NAME, cookie1.getValue())
            .when().post(ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        // Should not set a new cookie
        Cookie cookie2 = response2.getDetailedCookie(COOKIE_NAME);
        assertNull(cookie2, "Should not refresh cookie when not in refresh window");
        
        // Use the same cookie again (still not in refresh window)
        Response response3 = given()
            .cookie(COOKIE_NAME, cookie1.getValue())
            .when().post(ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();
        
        // Still should not set a new cookie
        Cookie cookie3 = response3.getDetailedCookie(COOKIE_NAME);
        assertNull(cookie3, "Should remain idempotent - no refresh when not in window");
    }
}
