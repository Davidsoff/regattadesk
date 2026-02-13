package com.regattadesk.health;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

@QuarkusTest
public class HealthEndpointsTest {

    @Test
    public void testHealthEndpoint() {
        RestAssured.given()
            .when().get("/api/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("version", notNullValue());
    }

    @Test
    public void testReadinessEndpoint() {
        RestAssured.given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("checks", notNullValue())
            .body("checks.find { it.name == 'regattadesk-backend-ready' }.status", equalTo("UP"));
    }

    @Test
    public void testLivenessEndpoint() {
        RestAssured.given()
            .when().get("/q/health/live")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("checks.find { it.name == 'regattadesk-backend-live' }.status", equalTo("UP"));
    }

    @Test
    public void testStartupEndpoint() {
        RestAssured.given()
            .when().get("/q/health/started")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("checks.find { it.name == 'regattadesk-backend-startup' }.status", equalTo("UP"));
    }
}
