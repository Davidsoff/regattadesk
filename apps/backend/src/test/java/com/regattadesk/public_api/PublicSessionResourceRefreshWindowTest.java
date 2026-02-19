package com.regattadesk.public_api;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.regattadesk.jwt.JwtConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Cookie;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

@QuarkusTest
class PublicSessionResourceRefreshWindowTest {

    private static final String COOKIE_NAME = "regattadesk_public_session";
    private static final String ENDPOINT = "/public/session";

    @Inject
    JwtConfig jwtConfig;

    private String signToken(Instant issuedAt, Instant expiresAt) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .claim("sid", UUID.randomUUID().toString())
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
            .keyID(jwtConfig.kid())
            .build();

        SignedJWT token = new SignedJWT(header, claims);
        token.sign(new MACSigner(jwtConfig.secret().getBytes(StandardCharsets.UTF_8)));
        return token.serialize();
    }

    private void assertJwtCookieStructure(Cookie cookie) {
        assertNotNull(cookie, "Cookie should be present");
        assertNotNull(cookie.getValue(), "Cookie value should not be null");
        assertFalse(cookie.getValue().isEmpty(), "Cookie value should not be empty");

        try {
            SignedJWT jwt = SignedJWT.parse(cookie.getValue());
            assertEquals(JWSAlgorithm.HS256, jwt.getHeader().getAlgorithm(), "Unexpected JWT algorithm");
            assertEquals(jwtConfig.kid(), jwt.getHeader().getKeyID(), "Unexpected JWT kid header");

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            String sid = claims.getStringClaim("sid");
            assertNotNull(sid, "sid claim must be present");
            assertFalse(sid.isBlank(), "sid claim must not be blank");
            assertNotNull(claims.getIssueTime(), "iat claim must be present");
            assertNotNull(claims.getExpirationTime(), "exp claim must be present");
            assertTrue(claims.getExpirationTime().after(claims.getIssueTime()), "exp must be after iat");
        } catch (Exception e) {
            fail("Cookie value must be a parseable JWT with required claims: " + e.getMessage());
        }
    }

    @Test
    void testCookieRefreshWithinRefreshWindow() throws JOSEException {
        Instant now = Instant.now();
        long refreshWindowSeconds = (jwtConfig.ttlSeconds() * (long) jwtConfig.refreshWindowPercent()) / 100L;
        String nearExpiryToken = signToken(
            now.minusSeconds(60),
            now.plusSeconds(Math.max(1L, refreshWindowSeconds - 1L))
        );

        Response refreshResponse = given()
            .cookie(COOKIE_NAME, nearExpiryToken)
            .when().post(ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();

        Cookie refreshedCookie = refreshResponse.getDetailedCookie(COOKIE_NAME);
        assertJwtCookieStructure(refreshedCookie);
        assertNotEquals(nearExpiryToken, refreshedCookie.getValue());
    }

    @Test
    void testCookieNotRefreshedOutsideRefreshWindow() throws JOSEException {
        Instant now = Instant.now();
        long refreshWindowSeconds = (jwtConfig.ttlSeconds() * (long) jwtConfig.refreshWindowPercent()) / 100L;
        long expiryLead = refreshWindowSeconds + 300L;

        String notNearExpiryToken = signToken(
            now.minusSeconds(60),
            now.plusSeconds(expiryLead)
        );

        Response response = given()
            .cookie(COOKIE_NAME, notNearExpiryToken)
            .when().post(ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();

        Cookie refreshedCookie = response.getDetailedCookie(COOKIE_NAME);
        assertNull(refreshedCookie, "Fresh token outside refresh window should not be reissued");
    }
}
