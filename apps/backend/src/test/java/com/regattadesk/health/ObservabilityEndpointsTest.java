package com.regattadesk.health;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

/**
 * Tests for observability endpoints (metrics).
 * 
 * Note: In production, /q/metrics is NOT exposed publicly through Traefik.
 * These tests verify that the endpoint itself works correctly when accessed
 * directly (e.g., from Prometheus on the internal network).
 * 
 * Public access restriction is enforced at the Traefik routing level,
 * not at the application level, so the endpoint remains accessible for
 * internal monitoring tools.
 */
@QuarkusTest
public class ObservabilityEndpointsTest {

    @Test
    public void testMetricsEndpoint() {
        RestAssured.given()
            .when().get("/q/metrics")
            .then()
            .statusCode(200)
            .contentType(containsString("openmetrics-text"))
            .body(containsString("# HELP"))
            .body(containsString("# TYPE"));
    }

    @Test
    public void testMetricsContainJvmMetrics() {
        RestAssured.given()
            .when().get("/q/metrics")
            .then()
            .statusCode(200)
            .body(containsString("jvm_memory"))
            .body(containsString("system_cpu"));
    }

    @Test
    public void testMetricsContainHttpMetrics() {
        // First make a request to generate some metrics
        RestAssured.given()
            .when().get("/api/health")
            .then()
            .statusCode(200);

        // Then check if metrics are available
        RestAssured.given()
            .when().get("/q/metrics")
            .then()
            .statusCode(200)
            .body(containsString("http_server_requests"));
    }
}
