package com.regattadesk.export;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the printable export job endpoints.
 *
 * <p>Tests the full lifecycle:
 * <ol>
 *   <li>POST /api/v1/regattas/{id}/export/printables → 202 with job_id</li>
 *   <li>GET  /api/v1/jobs/{job_id}                   → status polling</li>
 *   <li>GET  /api/v1/jobs/{job_id}/download           → PDF artifact download</li>
 * </ol>
 * </p>
 */
@QuarkusTest
@Tag("contract")
class PrintableExportResourceIntegrationTest {

    @Inject
    DataSource dataSource;

    @Inject
    ExportJobService exportJobService;

    private UUID regattaId;

    @BeforeEach
    void setUp() throws Exception {
        regattaId = UUID.randomUUID();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO regattas (id, name, description, time_zone, status, " +
                     "entry_fee, currency, draw_revision, results_revision, created_at, updated_at) " +
                     "VALUES (?, 'Export Test Regatta', 'Test', 'UTC', 'published', " +
                     "25.00, 'EUR', 3, 5, now(), now())")) {
            stmt.setObject(1, regattaId);
            stmt.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // POST /export/printables
    // -------------------------------------------------------------------------

    @Test
    void requestExport_returns202WithJobId() {
        given()
                .header("Remote-User", "test-admin")
                .header("Remote-Groups", "regatta_admin")
        .when()
                .post("/api/v1/regattas/" + regattaId + "/export/printables")
        .then()
                .statusCode(202)
                .contentType("application/json")
                .body("job_id", notNullValue());
    }

    @Test
    void requestExport_acceptsEmptyBodyWithoutContentType() {
        given()
                .header("Remote-User", "test-admin")
                .header("Remote-Groups", "regatta_admin")
        .when()
                .post("/api/v1/regattas/" + regattaId + "/export/printables")
        .then()
                .statusCode(202)
                .contentType("application/json")
                .body("job_id", notNullValue());
    }

    @Test
    void requestExport_returns404ForUnknownRegatta() {
        UUID unknownId = UUID.randomUUID();
        given()
                .header("Remote-User", "test-admin")
                .header("Remote-Groups", "regatta_admin")
        .when()
                .post("/api/v1/regattas/" + unknownId + "/export/printables")
        .then()
                .statusCode(404)
                .contentType("application/json")
                .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    void requestExport_returns403WithoutAuthorizedRole() {
        given()
                .header("Remote-User", "operator-user")
                .header("Remote-Groups", "operator")
        .when()
                .post("/api/v1/regattas/" + regattaId + "/export/printables")
        .then()
                .statusCode(403);
    }

    @Test
    void requestExport_returns403WithoutAnyRole() {
        given()
                .header("Remote-User", "anonymous")
        .when()
                .post("/api/v1/regattas/" + regattaId + "/export/printables")
        .then()
                .statusCode(403);
    }

    @Test
    void requestExport_allowedForSuperAdminRole() {
        given()
                .header("Remote-User", "super-admin")
                .header("Remote-Groups", "super_admin")
        .when()
                .post("/api/v1/regattas/" + regattaId + "/export/printables")
        .then()
                .statusCode(202)
                .contentType("application/json")
                .body("job_id", notNullValue());
    }

    // -------------------------------------------------------------------------
    // GET /jobs/{job_id} – status polling
    // -------------------------------------------------------------------------

    @Test
    void getJobStatus_returns404ForUnknownJob() {
        UUID unknownJobId = UUID.randomUUID();
        given()
                .header("Remote-User", "test-admin")
                .header("Remote-Groups", "regatta_admin")
        .when()
                .get("/api/v1/jobs/" + unknownJobId)
        .then()
                .statusCode(404)
                .contentType("application/json")
                .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    void getJobStatus_returnsPendingStateBeforeProcessing() throws Exception {
        // Create a job directly (bypass async processing to observe PENDING state)
        UUID jobId = exportJobService.createJob(regattaId, "test-user");

        given()
                .header("Remote-User", "test-admin")
                .header("Remote-Groups", "regatta_admin")
        .when()
                .get("/api/v1/jobs/" + jobId)
        .then()
                .statusCode(200)
                .contentType("application/json")
                .body("status", equalTo("pending"))
                .body("download_url", nullValue())
                .body("error", nullValue());
    }

    @Test
    void getJobStatus_eventuallyReachesCompletedState() {
        // Request export via API – triggers background processing
        String jobIdStr = given()
                .header("Remote-User", "test-admin")
                .header("Remote-Groups", "regatta_admin")
        .when()
                .post("/api/v1/regattas/" + regattaId + "/export/printables")
        .then()
                .statusCode(202)
                .extract().path("job_id");

        UUID jobId = UUID.fromString(jobIdStr);

        // Poll until completed (or timeout)
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        given()
                                .header("Remote-User", "test-admin")
                                .header("Remote-Groups", "regatta_admin")
                        .when()
                                .get("/api/v1/jobs/" + jobId)
                        .then()
                                .statusCode(200)
                                .body("status", equalTo("completed"))
                                .body("download_url", notNullValue())
                                .body("error", nullValue())
                );
    }

    @Test
    void getJobStatus_completedJobHasDownloadUrl() {
        String jobIdStr = given()
                .header("Remote-User", "test-admin")
                .header("Remote-Groups", "regatta_admin")
        .when()
                .post("/api/v1/regattas/" + regattaId + "/export/printables")
        .then()
                .statusCode(202)
                .extract().path("job_id");

        UUID jobId = UUID.fromString(jobIdStr);

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> {
                    String status = given()
                            .header("Remote-User", "test-admin")
                            .header("Remote-Groups", "regatta_admin")
                    .when()
                            .get("/api/v1/jobs/" + jobId)
                    .then()
                            .statusCode(200)
                            .extract().path("status");
                    return "completed".equals(status);
                });

        given()
                .header("Remote-User", "test-admin")
                .header("Remote-Groups", "regatta_admin")
        .when()
                .get("/api/v1/jobs/" + jobId)
        .then()
                .statusCode(200)
                .body("download_url", containsString("api/v1/jobs/"))
                .body("download_url", containsString("/download"));
    }

    @Test
    void getJobStatus_allowedForInfoDeskRole() {
        UUID jobId = exportJobService.createJob(regattaId, "info-desk-user");

        given()
                .header("Remote-User", "info-desk-user")
                .header("Remote-Groups", "info_desk")
        .when()
                .get("/api/v1/jobs/" + jobId)
        .then()
                .statusCode(200);
    }

    @Test
    void getJobStatus_allowedForHeadOfJuryRole() {
        UUID jobId = exportJobService.createJob(regattaId, "jury-user");

        given()
                .header("Remote-User", "jury-user")
                .header("Remote-Groups", "head_of_jury")
        .when()
                .get("/api/v1/jobs/" + jobId)
        .then()
                .statusCode(200);
    }

    @Test
    void getJobStatus_allowedForSuperAdminRole() {
        UUID jobId = exportJobService.createJob(regattaId, "super-admin-user");

        given()
                .header("Remote-User", "super-admin-user")
                .header("Remote-Groups", "super_admin")
        .when()
                .get("/api/v1/jobs/" + jobId)
        .then()
                .statusCode(200);
    }

    // -------------------------------------------------------------------------
    // GET /jobs/{job_id}/download – PDF artifact
    // -------------------------------------------------------------------------

    @Test
    void downloadArtifact_returns404ForUnknownJob() {
        UUID unknownJobId = UUID.randomUUID();
        given()
                .header("Remote-User", "test-admin")
                .header("Remote-Groups", "regatta_admin")
        .when()
                .get("/api/v1/jobs/" + unknownJobId + "/download")
        .then()
                .statusCode(404);
    }

    @Test
    void downloadArtifact_returns404ForPendingJob() throws Exception {
        UUID jobId = exportJobService.createJob(regattaId, "test-user");

        given()
                .header("Remote-User", "test-admin")
                .header("Remote-Groups", "regatta_admin")
        .when()
                .get("/api/v1/jobs/" + jobId + "/download")
        .then()
                .statusCode(404);
    }

    @Test
    void downloadArtifact_returnsPdfWhenCompleted() {
        String jobIdStr = given()
                .header("Remote-User", "test-admin")
                .header("Remote-Groups", "regatta_admin")
        .when()
                .post("/api/v1/regattas/" + regattaId + "/export/printables")
        .then()
                .statusCode(202)
                .extract().path("job_id");

        UUID jobId = UUID.fromString(jobIdStr);

        // Wait until completed
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> {
                    String status = given()
                            .header("Remote-User", "test-admin")
                            .header("Remote-Groups", "regatta_admin")
                    .when()
                            .get("/api/v1/jobs/" + jobId)
                    .then()
                            .statusCode(200)
                            .extract().path("status");
                    return "completed".equals(status);
                });

        // Download should return PDF
        byte[] pdf = given()
                .header("Remote-User", "test-admin")
                .header("Remote-Groups", "regatta_admin")
        .when()
                .get("/api/v1/jobs/" + jobId + "/download")
        .then()
                .statusCode(200)
                .contentType("application/pdf")
                .header("Content-Disposition", containsString("attachment"))
                .extract().asByteArray();

        // Verify PDF magic bytes
        assertNotNull(pdf);
        assertTrue(pdf.length > 4);
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
        assertEquals('D', (char) pdf[2]);
        assertEquals('F', (char) pdf[3]);
    }

    @Test
    void downloadArtifact_returns403WithoutRole() {
        UUID jobId = exportJobService.createJob(regattaId, "user");

        given()
                .header("Remote-User", "operator")
                .header("Remote-Groups", "operator")
        .when()
                .get("/api/v1/jobs/" + jobId + "/download")
        .then()
                .statusCode(403);
    }

    @Test
    void downloadArtifact_allowedForSuperAdminRole() {
        String jobIdStr = given()
                .header("Remote-User", "super-admin")
                .header("Remote-Groups", "super_admin")
        .when()
                .post("/api/v1/regattas/" + regattaId + "/export/printables")
        .then()
                .statusCode(202)
                .extract().path("job_id");

        UUID jobId = UUID.fromString(jobIdStr);
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> {
                    String status = given()
                            .header("Remote-User", "super-admin")
                            .header("Remote-Groups", "super_admin")
                    .when()
                            .get("/api/v1/jobs/" + jobId)
                    .then()
                            .statusCode(200)
                            .extract().path("status");
                    return "completed".equals(status);
                });

        given()
                .header("Remote-User", "super-admin")
                .header("Remote-Groups", "super_admin")
        .when()
                .get("/api/v1/jobs/" + jobId + "/download")
        .then()
                .statusCode(200)
                .contentType("application/pdf");
    }
}
