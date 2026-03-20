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

/**
 * Integration tests for the capture session lifecycle API.
 *
 * <p>Tests cover: start, list, get, sync_state update, and close.
 */
@QuarkusTest
class CaptureSessionIntegrationTest {

    @Inject
    DataSource dataSource;

    @Inject
    OperatorTokenService tokenService;

    private UUID regattaId;
    private UUID blockId;
    private String operatorToken;
    private String otherStationOperatorToken;
    private String station;

    @BeforeEach
    void setUp() throws Exception {
        regattaId = UUID.randomUUID();
        blockId = UUID.randomUUID();
        station = "line-scan";

        // Seed minimal foreign-key rows required by the schema.
        seedRegatta(regattaId);
        seedBlock(regattaId, blockId);

        // Issue an operator token for the station under test.
        Instant now = Instant.now();
        OperatorToken token = tokenService.issueToken(
                regattaId,
                null,
                station,
                now,
                now.plus(8, ChronoUnit.HOURS)
        );
        operatorToken = token.getToken();
        otherStationOperatorToken = issueOperatorToken("start-line");
    }

    // ---- POST (start) -------------------------------------------------------

    @Test
    void startSession_shouldReturn201WithSessionBody() {
        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body(buildCreateRequest(blockId, station, "device-001", "finish", 25))
        .when()
                .post("/api/v1/regattas/" + regattaId + "/operator/capture_sessions")
        .then()
                .statusCode(201)
                .body("capture_session_id", notNullValue())
                .body("regatta_id", equalTo(regattaId.toString()))
                .body("block_id", equalTo(blockId.toString()))
                .body("station", equalTo(station))
                .body("device_id", equalTo("device-001"))
                .body("session_type", equalTo("finish"))
                .body("state", equalTo("open"))
                .body("fps", equalTo(25))
                .body("is_synced", equalTo(true))
                .body("drift_exceeded_threshold", equalTo(false));
    }

    @Test
    void startSession_shouldReturn401WithoutToken() {
        given()
                .contentType(ContentType.JSON)
                .body(buildCreateRequest(blockId, station, "device-002", "finish", 25))
        .when()
                .post("/api/v1/regattas/" + regattaId + "/operator/capture_sessions")
        .then()
                .statusCode(401);
    }

    @Test
    void startSession_shouldReturn400WithInvalidFps() {
        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body("""
                        {
                            "block_id": "%s",
                            "station": "%s",
                            "device_id": "dev-x",
                            "session_type": "finish",
                            "fps": 0
                        }
                        """.formatted(blockId, station))
        .when()
                .post("/api/v1/regattas/" + regattaId + "/operator/capture_sessions")
        .then()
                .statusCode(400);
    }

    // ---- GET list -----------------------------------------------------------

    @Test
    void listSessions_shouldReturnCreatedSession() {
        // Create a session first.
        String sessionId = given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body(buildCreateRequest(blockId, station, "device-list-01", "finish", 25))
        .when()
                .post("/api/v1/regattas/" + regattaId + "/operator/capture_sessions")
        .then()
                .statusCode(201)
                .extract().path("capture_session_id");

        given()
                .header("X-Operator-Token", operatorToken)
        .when()
                .get("/api/v1/regattas/" + regattaId + "/operator/capture_sessions")
        .then()
                .statusCode(200)
                .body("capture_sessions", hasSize(greaterThanOrEqualTo(1)))
                .body("capture_sessions.capture_session_id", hasItem(sessionId));
    }

    @Test
    void listSessions_withStaffAuth_shouldReturn200() {
        given()
                .header("X-Forwarded-User", "staff@example.com")
        .when()
                .get("/api/v1/regattas/" + regattaId + "/operator/capture_sessions")
        .then()
                .statusCode(200)
                .body("capture_sessions", notNullValue());
    }

    @Test
    void listSessions_shouldReturn401WithoutAuth() {
        given()
        .when()
                .get("/api/v1/regattas/" + regattaId + "/operator/capture_sessions")
        .then()
                .statusCode(401);
    }

    @Test
    void listSessions_stateOpenAndBlockId_shouldApplyBothFilters() throws Exception {
        UUID secondBlockId = UUID.randomUUID();
        seedBlock(regattaId, secondBlockId);

        String sessionInPrimaryBlock = createSessionAndGetId("device-open-target");

        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body(buildCreateRequest(secondBlockId, station, "device-open-other-block", "finish", 25))
        .when()
                .post("/api/v1/regattas/" + regattaId + "/operator/capture_sessions")
        .then()
                .statusCode(201);

        String closedSessionInPrimaryBlock = createSessionAndGetId("device-closed-target-block");
        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body("{}")
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + closedSessionInPrimaryBlock + "/close")
        .then()
                .statusCode(200);

        given()
                .header("X-Operator-Token", operatorToken)
                .queryParam("state", "open")
                .queryParam("block_id", blockId)
        .when()
                .get("/api/v1/regattas/" + regattaId + "/operator/capture_sessions")
        .then()
                .statusCode(200)
                .body("capture_sessions.capture_session_id", hasItem(sessionInPrimaryBlock))
                .body("capture_sessions.capture_session_id", not(hasItem(closedSessionInPrimaryBlock)))
                .body("capture_sessions", hasSize(1));
    }

    // ---- GET single session -------------------------------------------------

    @Test
    void getSession_shouldReturnSession() {
        String sessionId = createSessionAndGetId("device-get-01");

        given()
                .header("X-Operator-Token", operatorToken)
        .when()
                .get("/api/v1/regattas/" + regattaId + "/operator/capture_sessions/" + sessionId)
        .then()
                .statusCode(200)
                .body("capture_session_id", equalTo(sessionId));
    }

    @Test
    void getSession_shouldReturn404ForUnknownId() {
        given()
                .header("X-Operator-Token", operatorToken)
        .when()
                .get("/api/v1/regattas/" + regattaId + "/operator/capture_sessions/" + UUID.randomUUID())
        .then()
                .statusCode(404);
    }

    // ---- POST sync_state ----------------------------------------------------

    @Test
    void updateSyncState_shouldReflectNewState() {
        String sessionId = createSessionAndGetId("device-sync-01");

        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body("""
                        {
                            "is_synced": false,
                            "drift_exceeded_threshold": true,
                            "unsynced_reason": "NTP server unreachable"
                        }
                        """)
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + sessionId + "/sync_state")
        .then()
                .statusCode(200)
                .body("is_synced", equalTo(false))
                .body("drift_exceeded_threshold", equalTo(true))
                .body("unsynced_reason", equalTo("NTP server unreachable"))
                .body("state", equalTo("open"));
    }

    @Test
    void updateSyncState_shouldReturn401ForTokenFromDifferentStation() {
        String sessionId = createSessionAndGetId("device-sync-station-scope");

        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", otherStationOperatorToken)
                .body("""
                        {"is_synced": true, "drift_exceeded_threshold": false}
                        """)
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + sessionId + "/sync_state")
        .then()
                .statusCode(401);
    }

    @Test
    void updateSyncState_shouldReturn404ForUnknownSessionBeforeTokenValidation() {
        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", otherStationOperatorToken)
                .body("""
                        {"is_synced": true, "drift_exceeded_threshold": false}
                        """)
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + UUID.randomUUID() + "/sync_state")
        .then()
                .statusCode(404);
    }

    @Test
    void updateSyncState_shouldReturn400WhenRequiredBooleanFieldsAreMissing() {
        String sessionId = createSessionAndGetId("device-sync-missing-fields");

        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body("""
                        {"unsynced_reason": "clock skew"}
                        """)
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + sessionId + "/sync_state")
        .then()
                .statusCode(400);
    }

    @Test
    void updateSyncState_shouldReturn409ForClosedSession() {
        String sessionId = createSessionAndGetId("device-sync-closed");

        // Close the session first.
        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body("{}")
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + sessionId + "/close")
        .then()
                .statusCode(200);

        // Now try updating sync state — should be 409.
        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body("""
                        {"is_synced": true, "drift_exceeded_threshold": false}
                        """)
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + sessionId + "/sync_state")
        .then()
                .statusCode(409);
    }

    // ---- POST close ---------------------------------------------------------

    @Test
    void closeSession_shouldTransitionToClosedState() {
        String sessionId = createSessionAndGetId("device-close-01");

        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body("""
                        {"close_reason": "End of race"}
                        """)
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + sessionId + "/close")
        .then()
                .statusCode(200)
                .body("state", equalTo("closed"))
                .body("close_reason", equalTo("End of race"))
                .body("closed_at", notNullValue());
    }

    @Test
    void closeSession_shouldAcceptEmptyBody() {
        String sessionId = createSessionAndGetId("device-close-empty");

        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body("{}")
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + sessionId + "/close")
        .then()
                .statusCode(200)
                .body("state", equalTo("closed"));
    }

    @Test
    void closeSession_shouldReturn409WhenAlreadyClosed() {
        String sessionId = createSessionAndGetId("device-close-twice");

        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body("{}")
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + sessionId + "/close");

        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body("{}")
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + sessionId + "/close")
        .then()
                .statusCode(409);
    }

    @Test
    void closeSession_shouldReturn401ForTokenFromDifferentStation() {
        String sessionId = createSessionAndGetId("device-close-station-scope");

        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", otherStationOperatorToken)
                .body("{}")
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + sessionId + "/close")
        .then()
                .statusCode(401);
    }

    @Test
    void closeSession_shouldReturn404ForUnknownSessionBeforeTokenValidation() {
        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", otherStationOperatorToken)
                .body("{}")
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + UUID.randomUUID() + "/close")
        .then()
                .statusCode(404);
    }

    // ---- POST device_controls -----------------------------------------------

    @Test
    void updateDeviceControls_shouldSucceedForLineScanStationWithOpenSession() {
        // Create session at a line-scan station (the only supported station)
        String sessionId = createSessionAndGetId("device-control-01");

        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body("""
                        {
                            "scan_line_position": 512,
                            "capture_rate": 60
                        }
                        """)
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + sessionId + "/device_controls")
        .then()
                .statusCode(200)
                .body("device_controls.scan_line_position", equalTo(512))
                .body("device_controls.capture_rate", equalTo(60))
                .body("device_controls.scan_line_position_writable", equalTo(true))
                .body("device_controls.capture_rate_writable", equalTo(true))
                .body("state", equalTo("open"));
    }

    @Test
    void updateDeviceControls_shouldAcceptPartialUpdates() {
        String sessionId = createSessionAndGetId("device-control-partial");

        // Update only scan_line_position
        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body("""
                        {
                            "scan_line_position": 256
                        }
                        """)
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + sessionId + "/device_controls")
        .then()
                .statusCode(200)
                .body("device_controls.scan_line_position", equalTo(256))
                .body("device_controls.capture_rate", nullValue());
    }

    @Test
    void updateDeviceControls_shouldValidateScanLinePositionNonNegative() {
        String sessionId = createSessionAndGetId("device-control-negative");

        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body("""
                        {
                            "scan_line_position": -1
                        }
                        """)
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + sessionId + "/device_controls")
        .then()
                .statusCode(400)
                .body("message", containsString("must be non-negative"));
    }

    @Test
    void updateDeviceControls_shouldValidateCaptureRatePositive() {
        String sessionId = createSessionAndGetId("device-control-zero-rate");

        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body("""
                        {
                            "capture_rate": 0
                        }
                        """)
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + sessionId + "/device_controls")
        .then()
                .statusCode(400)
                .body("message", containsString("must be positive"));
    }

    @Test
    void updateDeviceControls_shouldReturn409ForClosedSession() {
        String sessionId = createSessionAndGetId("device-control-closed");

        // Close the session first
        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body("{}")
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + sessionId + "/close");

        // Try to update device controls
        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body("""
                        {
                            "scan_line_position": 512
                        }
                        """)
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + sessionId + "/device_controls")
        .then()
                .statusCode(409)
                .body("message", containsString("Cannot update device controls of a closed session"));
    }

    @Test
    void updateDeviceControls_shouldReturn401ForTokenFromDifferentStation() {
        String sessionId = createSessionAndGetId("device-control-station-scope");

        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", otherStationOperatorToken)
                .body("""
                        {
                            "scan_line_position": 512
                        }
                        """)
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + sessionId + "/device_controls")
        .then()
                .statusCode(401);
    }

    @Test
    void updateDeviceControls_shouldReturn404ForUnknownSession() {
        given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body("""
                        {
                            "scan_line_position": 512
                        }
                        """)
        .when()
                .post("/api/v1/regattas/" + regattaId
                        + "/operator/capture_sessions/" + UUID.randomUUID() + "/device_controls")
        .then()
                .statusCode(404);
    }

    // ---- Helpers ------------------------------------------------------------

    private String createSessionAndGetId(String deviceId) {
        return given()
                .contentType(ContentType.JSON)
                .header("X-Operator-Token", operatorToken)
                .body(buildCreateRequest(blockId, station, deviceId, "finish", 25))
        .when()
                .post("/api/v1/regattas/" + regattaId + "/operator/capture_sessions")
        .then()
                .statusCode(201)
                .extract().path("capture_session_id");
    }

    private String buildCreateRequest(UUID blockId, String station,
                                      String deviceId, String sessionType, int fps) {
        return """
                {
                    "block_id": "%s",
                    "station": "%s",
                    "device_id": "%s",
                    "session_type": "%s",
                    "fps": %d
                }
                """.formatted(blockId, station, deviceId, sessionType, fps);
    }

    private String issueOperatorToken(String tokenStation) {
        Instant now = Instant.now();
        OperatorToken token = tokenService.issueToken(
                regattaId,
                null,
                tokenStation,
                now,
                now.plus(8, ChronoUnit.HOURS)
        );
        return token.getToken();
    }

    private void seedRegatta(UUID id) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO regattas (id, name) VALUES (?, 'Test Regatta')")) {
            stmt.setObject(1, id);
            stmt.executeUpdate();
        }
    }

    private void seedBlock(UUID regattaId, UUID blockId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO blocks (id, regatta_id, name, start_time) VALUES (?, ?, 'Block A', CURRENT_TIMESTAMP)")) {
            stmt.setObject(1, blockId);
            stmt.setObject(2, regattaId);
            stmt.executeUpdate();
        }
    }
}
