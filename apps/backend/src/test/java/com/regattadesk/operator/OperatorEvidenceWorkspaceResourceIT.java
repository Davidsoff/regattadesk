package com.regattadesk.operator;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import com.regattadesk.entry.EntryService;

@QuarkusTest
class OperatorEvidenceWorkspaceResourceIT {

    @Inject
    DataSource dataSource;

    @Inject
    OperatorTokenService operatorTokenService;

    @Inject
    EntryService entryService;

    @Test
    void workspaceEndpointReturnsEvidenceTilesMarkersAndCaptureSessionStatus() throws Exception {
        TestData data = createTestData();

        given()
            .header("X-Operator-Token", data.token())
        .when()
            .get("/api/v1/regattas/" + data.regattaId() + "/operator/evidence_workspace?capture_session_id=" + data.captureSessionId() + "&event_id=" + data.eventId())
        .then()
            .statusCode(200)
            .body("regatta_id", equalTo(data.regattaId().toString()))
            .body("capture_session_id", equalTo(data.captureSessionId().toString()))
            .body("event_id", equalTo(data.eventId().toString()))
            .body("capture_session.capabilities.persisted_evidence_workspace_supported", equalTo(true))
            .body("capture_session.capabilities.device_control_mode", equalTo("read_only"))
            .body("capture_session.live_status.preview_state", equalTo("inactive"))
            .body("evidence.manifest_id", equalTo(data.manifestId().toString()))
            .body("evidence.availability_state", equalTo("degraded"))
            .body("evidence.availability_reason", equalTo("tile_upload_pending"))
            .body("evidence.upload_state", equalTo("syncing"))
            .body("evidence.tiles", hasSize(2))
            .body("evidence.tiles[0].tile_id", equalTo("tile-ready"))
            .body("evidence.tiles[1].upload_state", equalTo("pending"))
            .body("evidence.span.start_timestamp_ms", equalTo(1_000))
            .body("markers", hasSize(2))
            .body("markers[0].id", equalTo(data.unlinkedMarkerId().toString()))
            .body("markers[1].entry_summary.entry_id", equalTo(data.entryId().toString()))
            .body("markers[1].entry_summary.completion_status", equalTo("incomplete"));
    }

    @Test
    void workspaceEndpointSurfacesPartialFailureLifecycle() throws Exception {
        TestData data = createTestDataWithFailedTile();

        given()
            .header("X-Operator-Token", data.token())
        .when()
            .get("/api/v1/regattas/" + data.regattaId() + "/operator/evidence_workspace?capture_session_id=" + data.captureSessionId())
        .then()
            .statusCode(200)
            .body("evidence.upload_state", equalTo("partial_failure"))
            .body("evidence.availability_state", equalTo("degraded"))
            .body("evidence.availability_reason", equalTo("tile_upload_failed"))
            .body("evidence.tiles[1].upload_state", equalTo("failed"))
            .body("evidence.tiles[1].last_upload_error", equalTo("minio timeout"));
    }

    @Test
    void workspaceEndpointRejectsMissingQueryKeys() throws Exception {
        TestData data = createTestData();

        given()
            .header("X-Operator-Token", data.token())
        .when()
            .get("/api/v1/regattas/" + data.regattaId() + "/operator/evidence_workspace")
        .then()
            .statusCode(400)
            .body("error.code", equalTo("BAD_REQUEST"))
            .body("error.message", equalTo("Either capture_session_id or event_id is required"));
    }

    private TestData createTestData() throws Exception {
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID captureSessionId = UUID.randomUUID();
        UUID manifestId = UUID.randomUUID();
        UUID linkedMarkerId = UUID.randomUUID();
        UUID unlinkedMarkerId = UUID.randomUUID();

        try (Connection conn = dataSource.getConnection()) {
            insertRegatta(conn, regattaId);
            UUID categoryId = insertCategory(conn);
            UUID boatTypeId = insertBoatType(conn);
            insertEvent(conn, eventId, regattaId, categoryId, boatTypeId);
            insertBlock(conn, blockId, regattaId);
            UUID crewId = insertCrew(conn);
            insertCaptureSession(conn, captureSessionId, regattaId, blockId, Instant.ofEpochMilli(1_000));
            UUID entryId = entryService.createEntry(regattaId, eventId, blockId, crewId, null).id();
            insert(conn, """
                INSERT INTO line_scan_manifests (
                    id, regatta_id, capture_session_id, tile_size_px, primary_format, fallback_format,
                    x_origin_timestamp_ms, ms_per_pixel, retention_days, prune_window_seconds,
                    retention_state, created_at, updated_at
                ) VALUES (?, ?, ?, 512, 'webp_lossless', 'png', 1000, 0.5, 14, 2, 'full_retained', now(), now())
                """, manifestId, regattaId, captureSessionId);
            insert(conn, """
                INSERT INTO line_scan_tiles (
                    id, manifest_id, tile_id, tile_x, tile_y, content_type, byte_size, upload_state,
                    upload_attempts, minio_bucket, minio_object_key, created_at, updated_at
                ) VALUES (?, ?, 'tile-ready', 0, 0, 'image/webp', 256, 'ready', 1, 'bucket', 'ready-key', now(), now())
                """, UUID.randomUUID(), manifestId);
            insert(conn, """
                INSERT INTO line_scan_tiles (
                    id, manifest_id, tile_id, tile_x, tile_y, content_type, upload_state,
                    upload_attempts, minio_bucket, minio_object_key, created_at, updated_at
                ) VALUES (?, ?, 'tile-pending', 1, 0, 'image/webp', 'pending', 0, 'bucket', 'pending-key', now(), now())
                """, UUID.randomUUID(), manifestId);
            insert(conn, """
                INSERT INTO timing_markers (
                    id, capture_session_id, frame_offset, timestamp_ms, is_linked, is_approved, tile_id, tile_x, tile_y, created_at, updated_at
                ) VALUES (?, ?, 10, 1100, false, false, 'tile-ready', 0, 0, now(), now())
                """, unlinkedMarkerId, captureSessionId);
            insert(conn, """
                INSERT INTO timing_markers (
                    id, capture_session_id, entry_id, frame_offset, timestamp_ms, is_linked, is_approved, tile_id, tile_x, tile_y, created_at, updated_at
                ) VALUES (?, ?, ?, 20, 1200, true, false, 'tile-pending', 1, 0, now(), now())
                """, linkedMarkerId, captureSessionId, entryId);

            return new TestData(regattaId, eventId, entryId, captureSessionId, manifestId, linkedMarkerId, unlinkedMarkerId, tokenFor(regattaId));
        }
    }

    private TestData createTestDataWithFailedTile() throws Exception {
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID captureSessionId = UUID.randomUUID();
        UUID manifestId = UUID.randomUUID();

        try (Connection conn = dataSource.getConnection()) {
            insertRegatta(conn, regattaId);
            UUID categoryId = insertCategory(conn);
            UUID boatTypeId = insertBoatType(conn);
            insertEvent(conn, eventId, regattaId, categoryId, boatTypeId);
            insertBlock(conn, blockId, regattaId);
            UUID crewId = insertCrew(conn);
            insertCaptureSession(conn, captureSessionId, regattaId, blockId, Instant.ofEpochMilli(1_000));
            entryService.createEntry(regattaId, eventId, blockId, crewId, null);
            insert(conn, """
                INSERT INTO line_scan_manifests (
                    id, regatta_id, capture_session_id, tile_size_px, primary_format, fallback_format,
                    x_origin_timestamp_ms, ms_per_pixel, retention_days, prune_window_seconds,
                    retention_state, created_at, updated_at
                ) VALUES (?, ?, ?, 512, 'webp_lossless', 'png', 1000, 0.5, 14, 2, 'full_retained', now(), now())
                """, manifestId, regattaId, captureSessionId);
            insert(conn, """
                INSERT INTO line_scan_tiles (
                    id, manifest_id, tile_id, tile_x, tile_y, content_type, byte_size, upload_state,
                    upload_attempts, last_upload_error, minio_bucket, minio_object_key, created_at, updated_at
                ) VALUES (?, ?, 'tile-ready', 0, 0, 'image/webp', 256, 'ready', 1, null, 'bucket', 'ready-key', now(), now())
                """, UUID.randomUUID(), manifestId);
            insert(conn, """
                INSERT INTO line_scan_tiles (
                    id, manifest_id, tile_id, tile_x, tile_y, content_type, upload_state,
                    upload_attempts, last_upload_error, minio_bucket, minio_object_key, created_at, updated_at
                ) VALUES (?, ?, 'tile-failed', 1, 0, 'image/webp', 'failed', 2, 'minio timeout', 'bucket', 'failed-key', now(), now())
                """, UUID.randomUUID(), manifestId);

            return new TestData(regattaId, eventId, UUID.randomUUID(), captureSessionId, manifestId, UUID.randomUUID(), UUID.randomUUID(), tokenFor(regattaId));
        }
    }

    private void insert(Connection connection, String sql, Object... params) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.executeUpdate();
        }
    }

    private String tokenFor(UUID regattaId) {
        return operatorTokenService.issueToken(
            regattaId,
            null,
            "line-scan",
            Instant.now().minusSeconds(60),
            Instant.now().plusSeconds(3600)
        ).getToken();
    }

    private void insertRegatta(Connection conn, UUID regattaId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO regattas (id, name, time_zone, status) VALUES (?, ?, ?, ?)"
        )) {
            stmt.setObject(1, regattaId);
            stmt.setString(2, "Workspace Regatta");
            stmt.setString(3, "Europe/Amsterdam");
            stmt.setString(4, "draft");
            stmt.executeUpdate();
        }
    }

    private UUID insertCategory(Connection conn) throws Exception {
        UUID categoryId = UUID.randomUUID();
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO categories (id, name) VALUES (?, ?)")) {
            stmt.setObject(1, categoryId);
            stmt.setString(2, "Workspace Category");
            stmt.executeUpdate();
        }
        return categoryId;
    }

    private UUID insertBoatType(Connection conn) throws Exception {
        UUID boatTypeId = UUID.randomUUID();
        try (PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO boat_types (id, code, name, rowers, coxswain, sculling) VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            stmt.setObject(1, boatTypeId);
            stmt.setString(2, "B" + boatTypeId.toString().substring(0, 8).toUpperCase());
            stmt.setString(3, "Workspace Boat");
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
            stmt.setString(5, "Workspace Event");
            stmt.executeUpdate();
        }
    }

    private void insertBlock(Connection conn, UUID blockId, UUID regattaId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO blocks (id, regatta_id, name, start_time) VALUES (?, ?, ?, NOW())"
        )) {
            stmt.setObject(1, blockId);
            stmt.setObject(2, regattaId);
            stmt.setString(3, "Workspace Block");
            stmt.executeUpdate();
        }
    }

    private UUID insertCrew(Connection conn) throws Exception {
        UUID crewId = UUID.randomUUID();
        try (PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO crews (id, display_name, is_composite) VALUES (?, ?, ?)"
        )) {
            stmt.setObject(1, crewId);
            stmt.setString(2, "Workspace Crew");
            stmt.setBoolean(3, false);
            stmt.executeUpdate();
        }
        return crewId;
    }

    private void insertCaptureSession(Connection conn, UUID captureSessionId, UUID regattaId, UUID blockId, Instant serverTimeAtStart) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
            """
            INSERT INTO capture_sessions (
                id, regatta_id, block_id, station, device_id, session_type, state,
                server_time_at_start, fps, is_synced, drift_exceeded_threshold
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
        )) {
            stmt.setObject(1, captureSessionId);
            stmt.setObject(2, regattaId);
            stmt.setObject(3, blockId);
            stmt.setString(4, "line-scan");
            stmt.setString(5, "workspace-device");
            stmt.setString(6, "finish");
            stmt.setString(7, "open");
            stmt.setObject(8, serverTimeAtStart);
            stmt.setInt(9, 25);
            stmt.setBoolean(10, true);
            stmt.setBoolean(11, false);
            stmt.executeUpdate();
        }
    }

    private record TestData(
        UUID regattaId,
        UUID eventId,
        UUID entryId,
        UUID captureSessionId,
        UUID manifestId,
        UUID linkedMarkerId,
        UUID unlinkedMarkerId,
        String token
    ) {}
}
