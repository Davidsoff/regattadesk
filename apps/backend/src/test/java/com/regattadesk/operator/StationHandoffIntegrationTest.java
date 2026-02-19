package com.regattadesk.operator;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class StationHandoffIntegrationTest {

    @Inject
    DataSource dataSource;

    @Inject
    OperatorTokenService tokenService;

    private UUID regattaId;
    private UUID tokenId;
    private String station;

    @BeforeEach
    void setUp() throws Exception {
        regattaId = UUID.randomUUID();
        station = "finish-line";
        
        // Create a valid token for testing
        Instant now = Instant.now();
        OperatorToken token = tokenService.issueToken(
            regattaId,
            null,
            station,
            now,
            now.plus(8, ChronoUnit.HOURS)
        );
        tokenId = token.getId();
    }

    @Test
    void requestHandoff_shouldCreatePendingHandoff() {
        String requestBody = """
            {
                "requestingDeviceId": "device-123"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .queryParam("token_id", tokenId.toString())
            .queryParam("station", station)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/station_handoffs")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("regattaId", equalTo(regattaId.toString()))
            .body("tokenId", equalTo(tokenId.toString()))
            .body("station", equalTo(station))
            .body("requestingDeviceId", equalTo("device-123"))
            .body("status", equalTo("PENDING"))
            .body("createdAt", notNullValue())
            .body("expiresAt", notNullValue())
            .body("pin", nullValue()); // PIN should not be in response
    }

    @Test
    void requestHandoff_shouldFailWithInvalidToken() {
        String requestBody = """
            {
                "requestingDeviceId": "device-123"
            }
            """;

        UUID invalidTokenId = UUID.randomUUID();

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .queryParam("token_id", invalidTokenId.toString())
            .queryParam("station", station)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/station_handoffs")
        .then()
            .statusCode(409);
    }

    @Test
    void getHandoff_shouldReturnHandoffStatus() {
        // First create a handoff
        String requestBody = """
            {
                "requestingDeviceId": "device-456"
            }
            """;

        String handoffId = given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .queryParam("token_id", tokenId.toString())
            .queryParam("station", station)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/station_handoffs")
        .then()
            .statusCode(201)
            .extract().path("id");

        // Then get the status
        given()
        .when()
            .get("/api/v1/regattas/" + regattaId + "/operator/station_handoffs/" + handoffId)
        .then()
            .statusCode(200)
            .body("id", equalTo(handoffId))
            .body("status", equalTo("PENDING"));
    }

    @Test
    void revealPin_shouldReturnPinForPendingHandoff() {
        // Create a handoff
        String requestBody = """
            {
                "requestingDeviceId": "device-789"
            }
            """;

        String handoffId = given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .queryParam("token_id", tokenId.toString())
            .queryParam("station", station)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/station_handoffs")
        .then()
            .statusCode(201)
            .extract().path("id");

        // Reveal PIN
        given()
        .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/station_handoffs/" + handoffId + "/reveal_pin")
        .then()
            .statusCode(200)
            .body("id", equalTo(handoffId))
            .body("pin", notNullValue())
            .body("pin", matchesPattern("\\d{6}"));
    }

    @Test
    void completeHandoff_shouldSucceedWithCorrectPin() {
        // Create a handoff
        String createRequest = """
            {
                "requestingDeviceId": "device-complete"
            }
            """;

        String handoffId = given()
            .contentType(ContentType.JSON)
            .body(createRequest)
            .queryParam("token_id", tokenId.toString())
            .queryParam("station", station)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/station_handoffs")
        .then()
            .statusCode(201)
            .extract().path("id");

        // Reveal PIN
        String pin = given()
        .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/station_handoffs/" + handoffId + "/reveal_pin")
        .then()
            .statusCode(200)
            .extract().path("pin");

        // Complete with correct PIN
        String completeRequest = String.format("""
            {
                "pin": "%s"
            }
            """, pin);

        given()
            .contentType(ContentType.JSON)
            .body(completeRequest)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/station_handoffs/" + handoffId + "/complete")
        .then()
            .statusCode(200)
            .body("status", equalTo("COMPLETED"))
            .body("completedAt", notNullValue());
    }

    @Test
    void completeHandoff_shouldFailWithIncorrectPin() {
        // Create a handoff
        String createRequest = """
            {
                "requestingDeviceId": "device-fail"
            }
            """;

        String handoffId = given()
            .contentType(ContentType.JSON)
            .body(createRequest)
            .queryParam("token_id", tokenId.toString())
            .queryParam("station", station)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/station_handoffs")
        .then()
            .statusCode(201)
            .extract().path("id");

        // Try to complete with wrong PIN
        String completeRequest = """
            {
                "pin": "000000"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(completeRequest)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/station_handoffs/" + handoffId + "/complete")
        .then()
            .statusCode(400)
            .body("error.code", equalTo("INVALID_PIN"))
            .body("error.message", equalTo("Invalid PIN"));
    }

    @Test
    void revealPin_shouldFailForExpiredHandoff() {
        String createRequest = """
            {
                "requestingDeviceId": "device-expired-reveal"
            }
            """;

        String handoffId = given()
            .contentType(ContentType.JSON)
            .body(createRequest)
            .queryParam("token_id", tokenId.toString())
            .queryParam("station", station)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/station_handoffs")
        .then()
            .statusCode(201)
            .extract().path("id");

        forceExpireHandoffForTest(handoffId);

        given()
        .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/station_handoffs/" + handoffId + "/reveal_pin")
        .then()
            .statusCode(410)
            .body("error.message", equalTo("HANDOFF_EXPIRED"));
    }

    @Test
    void completeHandoff_shouldFailWhenExpired() {
        String createRequest = """
            {
                "requestingDeviceId": "device-expired-complete"
            }
            """;

        String handoffId = given()
            .contentType(ContentType.JSON)
            .body(createRequest)
            .queryParam("token_id", tokenId.toString())
            .queryParam("station", station)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/station_handoffs")
        .then()
            .statusCode(201)
            .extract().path("id");

        forceExpireHandoffForTest(handoffId);

        String completeRequest = """
            {
                "pin": "123456"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(completeRequest)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/station_handoffs/" + handoffId + "/complete")
        .then()
            .statusCode(410)
            .body("error.message", equalTo("HANDOFF_EXPIRED"));
    }

    @Test
    void cancelHandoff_shouldCancelPendingHandoff() {
        // Create a handoff
        String requestBody = """
            {
                "requestingDeviceId": "device-cancel"
            }
            """;

        String handoffId = given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .queryParam("token_id", tokenId.toString())
            .queryParam("station", station)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/station_handoffs")
        .then()
            .statusCode(201)
            .extract().path("id");

        // Cancel it
        given()
        .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/station_handoffs/" + handoffId + "/cancel")
        .then()
            .statusCode(200)
            .body("message", containsString("cancelled"));

        // Verify status is cancelled
        given()
        .when()
            .get("/api/v1/regattas/" + regattaId + "/operator/station_handoffs/" + handoffId)
        .then()
            .statusCode(200)
            .body("status", equalTo("CANCELLED"));
    }

    private void forceExpireHandoffForTest(String handoffId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE station_handoffs SET created_at = ?, expires_at = ? WHERE id = ?"
             )) {
            Instant expiredInstant = Instant.now().minus(10, ChronoUnit.MINUTES);
            Instant createdInstant = expiredInstant.minus(10, ChronoUnit.MINUTES);
            statement.setObject(1, createdInstant);
            statement.setObject(2, expiredInstant);
            statement.setObject(3, UUID.fromString(handoffId));
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to force handoff expiration for test", e);
        }
    }
}
