package com.regattadesk.linescan;

import com.regattadesk.entry.EntryService;
import com.regattadesk.operator.OperatorTokenService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class MarkerResourceIT {

    @Inject
    DataSource dataSource;

    @Inject
    EntryService entryService;

    @Inject
    OperatorTokenService operatorTokenService;

    @Test
    void approvalGate_preventsFinalizationUntilTwoApprovedMarkers() throws Exception {
        TestData data = createTestData();

        String markerOneId = createMarker(data.regattaId(), data.token(), data.captureSessionId(), 100L, 1_000L);
        String markerTwoId = createMarker(data.regattaId(), data.token(), data.captureSessionId(), 200L, 2_000L);

        linkMarker(data.regattaId(), data.token(), markerOneId, data.entryId());
        linkMarker(data.regattaId(), data.token(), markerTwoId, data.entryId());

        given()
            .header("X-Operator-Token", data.token())
            .contentType("application/json")
            .body("""
                {
                  "is_approved": true
                }
                """)
        .when()
            .patch("/api/v1/regattas/" + data.regattaId() + "/operator/markers/" + markerOneId)
        .then()
            .statusCode(200)
            .body("is_approved", equalTo(true));

        assertEntryCompletion(data.entryId(), "pending_approval", null, null);

        given()
            .header("X-Operator-Token", data.token())
            .contentType("application/json")
            .body("""
                {
                  "is_approved": true
                }
                """)
        .when()
            .patch("/api/v1/regattas/" + data.regattaId() + "/operator/markers/" + markerTwoId)
        .then()
            .statusCode(200)
            .body("is_approved", equalTo(true));

        assertEntryCompletion(data.entryId(), "completed", 1_000L, 2_000L);

        given()
            .header("X-Operator-Token", data.token())
            .contentType("application/json")
            .body(Map.of("entry_id", data.entryId().toString()))
        .when()
            .post("/api/v1/regattas/" + data.regattaId() + "/operator/markers/" + markerOneId + "/link")
        .then()
            .statusCode(200)
            .body("entry_id", equalTo(data.entryId().toString()))
            .body("is_approved", equalTo(true));

        UUID secondEntryId = entryService.createEntry(
            data.regattaId(),
            data.eventId(),
            data.blockId(),
            data.crewId(),
            null
        ).id();

        given()
            .header("X-Operator-Token", data.token())
            .contentType("application/json")
            .body(Map.of("entry_id", secondEntryId.toString()))
        .when()
            .post("/api/v1/regattas/" + data.regattaId() + "/operator/markers/" + markerOneId + "/link")
        .then()
            .statusCode(409)
            .body("error.code", equalTo("CONFLICT"));

        given()
            .header("X-Operator-Token", data.token())
            .contentType("application/json")
            .body("""
                {
                  "timestamp_ms": 900
                }
                """)
        .when()
            .patch("/api/v1/regattas/" + data.regattaId() + "/operator/markers/" + markerOneId)
        .then()
            .statusCode(409)
            .body("error.code", equalTo("CONFLICT"));

        assertEntryCompletion(data.entryId(), "completed", 1_000L, 2_000L);
    }

    @Test
    void markerCrudAndListing_workForScopedRegatta() throws Exception {
        TestData data = createTestData();

        String markerId = createMarker(data.regattaId(), data.token(), data.captureSessionId(), 150L, 1_500L);

        given()
            .header("X-Operator-Token", data.token())
        .when()
            .get("/api/v1/regattas/" + data.regattaId() + "/operator/markers?capture_session_id=" + data.captureSessionId())
        .then()
            .statusCode(200)
            .body("data", hasSize(1))
            .body("data[0].id", equalTo(markerId));

        given()
            .header("X-Operator-Token", data.token())
            .contentType("application/json")
            .body("""
                {
                  "frame_offset": 155,
                  "tile_id": "tile_1",
                  "tile_x": 1,
                  "tile_y": 0
                }
                """)
        .when()
            .patch("/api/v1/regattas/" + data.regattaId() + "/operator/markers/" + markerId)
        .then()
            .statusCode(200)
            .body("frame_offset", equalTo(155))
            .body("tile_id", equalTo("tile_1"))
            .body("tile_x", equalTo(1))
            .body("tile_y", equalTo(0));

        given()
            .header("X-Operator-Token", data.token())
        .when()
            .delete("/api/v1/regattas/" + data.regattaId() + "/operator/markers/" + markerId)
        .then()
            .statusCode(204);

        given()
            .header("X-Operator-Token", data.token())
        .when()
            .get("/api/v1/regattas/" + data.regattaId() + "/operator/markers")
        .then()
            .statusCode(200)
            .body("data", hasSize(0));
    }

    @Test
    void markerEndpoints_requireValidOperatorTokenAndRespectRegattaScope() throws Exception {
        TestData data = createTestData();
        TestData otherRegatta = createTestData();
        String markerId = createMarker(data.regattaId(), data.token(), data.captureSessionId(), 150L, 1_500L);

        given()
        .when()
            .get("/api/v1/regattas/" + data.regattaId() + "/operator/markers")
        .then()
            .statusCode(401)
            .body("error.code", equalTo("UNAUTHORIZED"));

        given()
            .header("X-Operator-Token", otherRegatta.token())
        .when()
            .get("/api/v1/regattas/" + data.regattaId() + "/operator/markers")
        .then()
            .statusCode(401)
            .body("error.code", equalTo("UNAUTHORIZED"));

        given()
            .header("X-Operator-Token", otherRegatta.token())
        .when()
            .post("/api/v1/regattas/" + data.regattaId() + "/operator/markers/" + markerId + "/unlink")
        .then()
            .statusCode(401)
            .body("error.code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void unlinkClearsMarkerLinkAndEntryCompletion() throws Exception {
        TestData data = createTestData();
        String markerOneId = createMarker(data.regattaId(), data.token(), data.captureSessionId(), 100L, 1_000L);
        String markerTwoId = createMarker(data.regattaId(), data.token(), data.captureSessionId(), 200L, 2_000L);

        linkMarker(data.regattaId(), data.token(), markerOneId, data.entryId());
        linkMarker(data.regattaId(), data.token(), markerTwoId, data.entryId());

        given()
            .header("X-Operator-Token", data.token())
            .contentType("application/json")
            .body(Map.of("is_approved", true))
        .when()
            .patch("/api/v1/regattas/" + data.regattaId() + "/operator/markers/" + markerOneId)
        .then()
            .statusCode(200)
            .body("is_approved", equalTo(true));

        given()
            .header("X-Operator-Token", data.token())
            .contentType("application/json")
            .body(Map.of("is_approved", true))
        .when()
            .patch("/api/v1/regattas/" + data.regattaId() + "/operator/markers/" + markerTwoId)
        .then()
            .statusCode(200)
            .body("is_approved", equalTo(true));

        assertEntryCompletion(data.entryId(), "completed", 1_000L, 2_000L);

        given()
            .header("X-Operator-Token", data.token())
        .when()
            .post("/api/v1/regattas/" + data.regattaId() + "/operator/markers/" + markerOneId + "/unlink")
        .then()
            .statusCode(409)
            .body("error.code", equalTo("CONFLICT"));

        String unapprovedMarkerId = createMarker(data.regattaId(), data.token(), data.captureSessionId(), 300L, 3_000L);
        linkMarker(data.regattaId(), data.token(), unapprovedMarkerId, data.entryId());

        given()
            .header("X-Operator-Token", data.token())
        .when()
            .post("/api/v1/regattas/" + data.regattaId() + "/operator/markers/" + unapprovedMarkerId + "/unlink")
        .then()
            .statusCode(200)
            .body("is_linked", equalTo(false))
            .body("entry_id", nullValue());
    }

    @Test
    void createRejectsNegativeOffsetsAndTimestamps() throws Exception {
        TestData data = createTestData();

        given()
            .header("X-Operator-Token", data.token())
            .contentType("application/json")
            .body(Map.of(
                "capture_session_id", data.captureSessionId().toString(),
                "frame_offset", -1,
                "timestamp_ms", -10
            ))
        .when()
            .post("/api/v1/regattas/" + data.regattaId() + "/operator/markers")
        .then()
            .statusCode(400);
    }

    private String createMarker(UUID regattaId, String token, UUID captureSessionId, long frameOffset, long timestampMs) {
        return given()
            .header("X-Operator-Token", token)
            .contentType("application/json")
            .body(Map.of(
                "capture_session_id", captureSessionId.toString(),
                "frame_offset", frameOffset,
                "timestamp_ms", timestampMs
            ))
        .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/markers")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("is_linked", equalTo(false))
            .body("is_approved", equalTo(false))
            .extract()
            .path("id");
    }

    private void linkMarker(UUID regattaId, String token, String markerId, UUID entryId) {
        given()
            .header("X-Operator-Token", token)
            .contentType("application/json")
            .body(Map.of("entry_id", entryId.toString()))
        .when()
            .post("/api/v1/regattas/" + regattaId + "/operator/markers/" + markerId + "/link")
        .then()
            .statusCode(200)
            .body("is_linked", equalTo(true))
            .body("entry_id", equalTo(entryId.toString()))
            .body("is_approved", equalTo(false));
    }

    private void assertEntryCompletion(UUID entryId, String expectedStatus, Long startMs, Long finishMs) {
        var response = given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "regatta_admin")
        .when()
            .get("/api/v1/entries/" + entryId)
        .then()
            .statusCode(200)
            .body("completion_status", equalTo(expectedStatus))
            .extract()
            .response();

        Object markerStart = response.path("marker_start_time_ms");
        if (startMs == null) {
            assertNull(markerStart);
        } else {
            assertThat(((Number) markerStart).longValue(), equalTo(startMs));
        }

        Object markerFinish = response.path("marker_finish_time_ms");
        if (finishMs == null) {
            assertNull(markerFinish);
        } else {
            assertThat(((Number) markerFinish).longValue(), equalTo(finishMs));
        }
    }

    private TestData createTestData() throws Exception {
        UUID regattaId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID captureSessionId = UUID.randomUUID();

        try (Connection conn = dataSource.getConnection()) {
            insertRegatta(conn, regattaId);
            UUID categoryId = insertCategory(conn);
            UUID boatTypeId = insertBoatType(conn);
            insertEvent(conn, eventId, regattaId, categoryId, boatTypeId);
            insertBlock(conn, blockId, regattaId);
            UUID crewId = insertCrew(conn);
            insertCaptureSession(conn, captureSessionId, regattaId, blockId);

            UUID entryId = entryService.createEntry(regattaId, eventId, blockId, crewId, null).id();
            String token = operatorTokenService.issueToken(
                regattaId,
                null,
                "line-scan",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600)
            ).getToken();

            return new TestData(regattaId, entryId, eventId, blockId, crewId, captureSessionId, token);
        }
    }

    private void insertRegatta(Connection conn, UUID regattaId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO regattas (id, name, time_zone, status) VALUES (?, ?, ?, ?)"
        )) {
            stmt.setObject(1, regattaId);
            stmt.setString(2, "BC06 Marker Test");
            stmt.setString(3, "Europe/Amsterdam");
            stmt.setString(4, "draft");
            stmt.executeUpdate();
        }
    }

    private UUID insertCategory(Connection conn) throws Exception {
        UUID categoryId = UUID.randomUUID();
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO categories (id, name) VALUES (?, ?)")) {
            stmt.setObject(1, categoryId);
            stmt.setString(2, "Test Category");
            stmt.executeUpdate();
        }
        return categoryId;
    }

    private UUID insertBoatType(Connection conn) throws Exception {
        UUID boatTypeId = UUID.randomUUID();
        String code = "M" + boatTypeId.toString().substring(0, 8).toUpperCase();
        try (PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO boat_types (id, code, name, rowers, coxswain, sculling) VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            stmt.setObject(1, boatTypeId);
            stmt.setString(2, code);
            stmt.setString(3, "Marker Test Boat");
            stmt.setInt(4, 1);
            stmt.setBoolean(5, false);
            stmt.setBoolean(6, true);
            stmt.executeUpdate();
        }
        return boatTypeId;
    }

    private void insertEvent(Connection conn, UUID eventId, UUID regattaId, UUID categoryId, UUID boatTypeId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO events (id, regatta_id, category_id, boat_type_id, name) VALUES (?, ?, ?, ?, ?)"
        )) {
            stmt.setObject(1, eventId);
            stmt.setObject(2, regattaId);
            stmt.setObject(3, categoryId);
            stmt.setObject(4, boatTypeId);
            stmt.setString(5, "Marker Event");
            stmt.executeUpdate();
        }
    }

    private void insertBlock(Connection conn, UUID blockId, UUID regattaId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO blocks (id, regatta_id, name, start_time) VALUES (?, ?, ?, NOW())"
        )) {
            stmt.setObject(1, blockId);
            stmt.setObject(2, regattaId);
            stmt.setString(3, "Marker Block");
            stmt.executeUpdate();
        }
    }

    private UUID insertCrew(Connection conn) throws Exception {
        UUID crewId = UUID.randomUUID();
        try (PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO crews (id, display_name, is_composite) VALUES (?, ?, ?)"
        )) {
            stmt.setObject(1, crewId);
            stmt.setString(2, "Marker Crew " + crewId.toString().substring(0, 8));
            stmt.setBoolean(3, false);
            stmt.executeUpdate();
        }
        return crewId;
    }

    private void insertCaptureSession(Connection conn, UUID captureSessionId, UUID regattaId, UUID blockId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
            """
            INSERT INTO capture_sessions (
                id, regatta_id, block_id, station, device_id, session_type, state,
                server_time_at_start, fps, is_synced, drift_exceeded_threshold
            ) VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), ?, ?, ?)
            """
        )) {
            stmt.setObject(1, captureSessionId);
            stmt.setObject(2, regattaId);
            stmt.setObject(3, blockId);
            stmt.setString(4, "line-scan");
            stmt.setString(5, "test-device");
            stmt.setString(6, "finish");
            stmt.setString(7, "open");
            stmt.setInt(8, 60);
            stmt.setBoolean(9, true);
            stmt.setBoolean(10, false);
            stmt.executeUpdate();
        }
    }

    private record TestData(
        UUID regattaId,
        UUID entryId,
        UUID eventId,
        UUID blockId,
        UUID crewId,
        UUID captureSessionId,
        String token
    ) {
    }
}
