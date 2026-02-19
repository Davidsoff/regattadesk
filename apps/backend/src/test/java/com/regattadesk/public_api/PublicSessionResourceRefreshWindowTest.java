package com.regattadesk.public_api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.Cookie;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(ShortJwtSessionTestProfile.class)
class PublicSessionResourceRefreshWindowTest {

    private static final String COOKIE_NAME = "regattadesk_public_session";
    private static final String ENDPOINT = "/public/session";

    @Test
    void testCookieRefreshWithinRefreshWindow() throws InterruptedException {
        Response createResponse = given()
            .when().post(ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();

        Cookie initialCookie = createResponse.getDetailedCookie(COOKIE_NAME);
        assertNotNull(initialCookie);

        Thread.sleep(1200);

        Response refreshResponse = given()
            .cookie(COOKIE_NAME, initialCookie.getValue())
            .when().post(ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();

        Cookie refreshedCookie = refreshResponse.getDetailedCookie(COOKIE_NAME);
        assertNotNull(refreshedCookie);
        assertNotEquals(initialCookie.getValue(), refreshedCookie.getValue());
    }
}
