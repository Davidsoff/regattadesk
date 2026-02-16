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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    @Test
    void testCookieRefreshWithinRefreshWindow() throws JOSEException {
        Instant now = Instant.now();
        String nearExpiryToken = signToken(
            now.minusSeconds(100),
            now.plusSeconds(300)
        );

        Response refreshResponse = given()
            .cookie(COOKIE_NAME, nearExpiryToken)
            .when().post(ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();

        Cookie refreshedCookie = refreshResponse.getDetailedCookie(COOKIE_NAME);
        assertNotNull(refreshedCookie);
        assertNotEquals(nearExpiryToken, refreshedCookie.getValue());
    }
}
