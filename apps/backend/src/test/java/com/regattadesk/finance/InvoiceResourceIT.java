package com.regattadesk.finance;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class InvoiceResourceIT {

    @Inject
    DataSource dataSource;

    @Test
    void invoiceLifecycle_endpointsSupportGenerateListDetailAndMarkPaid() throws Exception {
        TestData data = seedInvoiceData();

        String jobId = given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .contentType("application/json")
            .body("{\"club_ids\": [\"" + data.clubOneId + "\"]}")
            .when()
            .post("/api/v1/regattas/" + data.regattaId + "/invoices/generate")
            .then()
            .statusCode(202)
            .body("job_id", notNullValue())
            .extract()
            .path("job_id");

        String invoiceId = awaitCompletedJob(data.regattaId, UUID.fromString(jobId));

        given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .when()
            .get("/api/v1/regattas/" + data.regattaId + "/invoices")
            .then()
            .statusCode(200)
            .body("invoices", hasSize(1))
            .body("pagination.has_more", equalTo(false));

        given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .when()
            .get("/api/v1/regattas/" + data.regattaId + "/invoices/" + invoiceId)
            .then()
            .statusCode(200)
            .body("id", equalTo(invoiceId))
            .body("status", equalTo("draft"))
            .body("entries", hasSize(2))
            .body("total_amount", equalTo(25.00f))
            .body("currency", equalTo("EUR"));

        given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .contentType("application/json")
            .body("""
                {
                  "paid_by": "cashier-1",
                  "payment_reference": "BANK-149",
                  "paid_at": "2026-03-10T12:00:00Z"
                }
                """)
            .when()
            .post("/api/v1/regattas/" + data.regattaId + "/invoices/" + invoiceId + "/mark_paid")
            .then()
            .statusCode(200)
            .body("status", equalTo("paid"))
            .body("paid_by", equalTo("cashier-1"))
            .body("payment_reference", equalTo("BANK-149"));

        given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .when()
            .get("/api/v1/regattas/" + data.regattaId + "/entries/" + data.entryOneId + "/payment_status")
            .then()
            .statusCode(200)
            .body("payment_status", equalTo("paid"));

        assertEquals(1, countAuditEvents("InvoiceGenerated", UUID.fromString(invoiceId)));
        assertEquals(1, countAuditEvents("InvoiceMarkedPaid", UUID.fromString(invoiceId)));
    }

    @Test
    void invoiceList_supportsClubAndStatusFiltersWithCursorPagination() throws Exception {
        TestData data = seedInvoiceData();

        String firstJobId = given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .contentType("application/json")
            .body("{\"club_ids\": [\"" + data.clubOneId + "\"]}")
            .when()
            .post("/api/v1/regattas/" + data.regattaId + "/invoices/generate")
            .then()
            .statusCode(202)
            .extract()
            .path("job_id");
        String clubOneInvoiceId = awaitCompletedJob(data.regattaId, UUID.fromString(firstJobId));

        String secondJobId = given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .contentType("application/json")
            .body("{\"club_ids\": [\"" + data.clubTwoId + "\"]}")
            .when()
            .post("/api/v1/regattas/" + data.regattaId + "/invoices/generate")
            .then()
            .statusCode(202)
            .extract()
            .path("job_id");
        String clubTwoInvoiceId = awaitCompletedJob(data.regattaId, UUID.fromString(secondJobId));
        assertNotNull(clubTwoInvoiceId);

        given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .when()
            .get("/api/v1/regattas/" + data.regattaId + "/invoices?limit=1")
            .then()
            .statusCode(200)
            .body("invoices", hasSize(1))
            .body("pagination.has_more", equalTo(true))
            .body("pagination.next_cursor", equalTo("1"));

        given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .when()
            .get("/api/v1/regattas/" + data.regattaId + "/invoices?club_id=" + data.clubTwoId)
            .then()
            .statusCode(200)
            .body("invoices", hasSize(1))
            .body("invoices[0].club_id", equalTo(data.clubTwoId.toString()));

        given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .contentType("application/json")
            .body("""
                {
                  "paid_by": "cashier-2"
                }
                """)
            .when()
            .post("/api/v1/regattas/" + data.regattaId + "/invoices/" + clubOneInvoiceId + "/mark_paid")
            .then()
            .statusCode(200);

        given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .when()
            .get("/api/v1/regattas/" + data.regattaId + "/invoices?status=paid")
            .then()
            .statusCode(200)
            .body("invoices", hasSize(1))
            .body("invoices[0].id", equalTo(clubOneInvoiceId));
    }

    @Test
    void invoiceEndpoints_enforceMutationRulesAndConflictResponses() throws Exception {
        TestData data = seedInvoiceData();

        given()
            .header("Remote-User", "desk-user")
            .header("Remote-Groups", "info_desk")
            .contentType("application/json")
            .body("{}")
            .when()
            .post("/api/v1/regattas/" + data.regattaId + "/invoices/generate")
            .then()
            .statusCode(403);

        String jobId = given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .contentType("application/json")
            .body("{}")
            .when()
            .post("/api/v1/regattas/" + data.regattaId + "/invoices/generate")
            .then()
            .statusCode(202)
            .extract()
            .path("job_id");
        String invoiceId = awaitCompletedJob(data.regattaId, UUID.fromString(jobId));

        given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .contentType("application/json")
            .body("{}")
            .when()
            .post("/api/v1/regattas/" + data.regattaId + "/invoices/" + invoiceId + "/mark_paid")
            .then()
            .statusCode(400);

        given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .contentType("application/json")
            .body("""
                {
                  "paid_by": "cashier-3"
                }
                """)
            .when()
            .post("/api/v1/regattas/" + data.regattaId + "/invoices/" + invoiceId + "/mark_paid")
            .then()
            .statusCode(200);

        given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .contentType("application/json")
            .body("""
                {
                  "paid_by": "cashier-3"
                }
                """)
            .when()
            .post("/api/v1/regattas/" + data.regattaId + "/invoices/" + invoiceId + "/mark_paid")
            .then()
            .statusCode(409);
    }

    @Test
    void markPaid_rejectsPaymentReferenceLongerThan255Characters() throws Exception {
        TestData data = seedInvoiceData();

        String jobId = given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .contentType("application/json")
            .body("{}")
            .when()
            .post("/api/v1/regattas/" + data.regattaId + "/invoices/generate")
            .then()
            .statusCode(202)
            .extract()
            .path("job_id");
        String invoiceId = awaitCompletedJob(data.regattaId, UUID.fromString(jobId));

        String paymentReference = IntStream.range(0, 256)
            .mapToObj(ignored -> "R")
            .collect(Collectors.joining());

        given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .contentType("application/json")
            .body("""
                {
                  "paid_by": "cashier-3",
                  "payment_reference": "%s"
                }
                """.formatted(paymentReference))
            .when()
            .post("/api/v1/regattas/" + data.regattaId + "/invoices/" + invoiceId + "/mark_paid")
            .then()
            .statusCode(400);
    }

    private String awaitCompletedJob(UUID regattaId, UUID jobId) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            var response = given()
                .header("Remote-User", "fin-user")
                .header("Remote-Groups", "financial_manager")
                .when()
                .get("/api/v1/regattas/" + regattaId + "/invoices/jobs/" + jobId)
                .then()
                .statusCode(200)
                .extract()
                .response();

            String status = response.path("status");
            if ("completed".equals(status)) {
                return response.path("invoice_ids[0]");
            }
            if ("failed".equals(status)) {
                throw new AssertionError("Invoice generation job failed: " + response.path("error_message"));
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("Invoice generation job did not complete in time");
    }

    private int countAuditEvents(String eventType, UUID aggregateId) throws Exception {
        String sql = """
            SELECT COUNT(*) AS c
            FROM event_store
            WHERE event_type = ? AND aggregate_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, eventType);
            stmt.setObject(2, aggregateId);
            try (var rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt("c");
            }
        }
    }

    private TestData seedInvoiceData() throws Exception {
        UUID regattaId = UUID.randomUUID();
        UUID clubOneId = UUID.randomUUID();
        UUID clubTwoId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID boatTypeId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID crewOneId = UUID.randomUUID();
        UUID crewTwoId = UUID.randomUUID();
        UUID crewThreeId = UUID.randomUUID();
        UUID entryOneId = UUID.randomUUID();
        UUID entryTwoId = UUID.randomUUID();
        UUID entryThreeId = UUID.randomUUID();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            Timestamp now = Timestamp.from(Instant.now());

            try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO regattas (id, name, time_zone, status, entry_fee, currency, draw_revision, results_revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?, ?)
                """)) {
                stmt.setObject(1, regattaId);
                stmt.setString(2, "Invoice IT Regatta");
                stmt.setString(3, "Europe/Amsterdam");
                stmt.setString(4, "draft");
                stmt.setBigDecimal(5, BigDecimal.valueOf(12.50));
                stmt.setString(6, "EUR");
                stmt.setTimestamp(7, now);
                stmt.setTimestamp(8, now);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO clubs (id, name, short_name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
                stmt.setObject(1, clubOneId);
                stmt.setString(2, "Invoice Club One");
                stmt.setString(3, "IC1");
                stmt.setTimestamp(4, now);
                stmt.setTimestamp(5, now);
                stmt.executeUpdate();

                stmt.setObject(1, clubTwoId);
                stmt.setString(2, "Invoice Club Two");
                stmt.setString(3, "IC2");
                stmt.setTimestamp(4, now);
                stmt.setTimestamp(5, now);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO categories (id, name, gender, is_global, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
                stmt.setObject(1, categoryId);
                stmt.setString(2, "Senior");
                stmt.setString(3, "ANY");
                stmt.setBoolean(4, true);
                stmt.setTimestamp(5, now);
                stmt.setTimestamp(6, now);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO boat_types (id, code, name, rowers, coxswain, sculling, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
                stmt.setObject(1, boatTypeId);
                stmt.setString(2, "IV" + UUID.randomUUID().toString().substring(0, 6));
                stmt.setString(3, "Invoice Boat");
                stmt.setInt(4, 1);
                stmt.setBoolean(5, false);
                stmt.setBoolean(6, true);
                stmt.setTimestamp(7, now);
                stmt.setTimestamp(8, now);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO blocks (id, regatta_id, name, start_time, event_interval_seconds, crew_interval_seconds, display_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, 300, 60, 0, ?, ?)
                """)) {
                stmt.setObject(1, blockId);
                stmt.setObject(2, regattaId);
                stmt.setString(3, "Invoice Block");
                stmt.setTimestamp(4, now);
                stmt.setTimestamp(5, now);
                stmt.setTimestamp(6, now);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO events (id, regatta_id, category_id, boat_type_id, name, display_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 0, ?, ?)
                """)) {
                stmt.setObject(1, eventId);
                stmt.setObject(2, regattaId);
                stmt.setObject(3, categoryId);
                stmt.setObject(4, boatTypeId);
                stmt.setString(5, "Invoice Event");
                stmt.setTimestamp(6, now);
                stmt.setTimestamp(7, now);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO crews (id, display_name, is_composite, club_id, created_at, updated_at)
                VALUES (?, ?, FALSE, ?, ?, ?)
                """)) {
                stmt.setObject(1, crewOneId);
                stmt.setString(2, "Crew One");
                stmt.setObject(3, clubOneId);
                stmt.setTimestamp(4, now);
                stmt.setTimestamp(5, now);
                stmt.executeUpdate();

                stmt.setObject(1, crewTwoId);
                stmt.setString(2, "Crew Two");
                stmt.setObject(3, clubOneId);
                stmt.setTimestamp(4, now);
                stmt.setTimestamp(5, now);
                stmt.executeUpdate();

                stmt.setObject(1, crewThreeId);
                stmt.setString(2, "Crew Three");
                stmt.setObject(3, clubTwoId);
                stmt.setTimestamp(4, now);
                stmt.setTimestamp(5, now);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO entries (id, regatta_id, event_id, block_id, crew_id, billing_club_id, status, payment_status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'entered', 'unpaid', ?, ?)
                """)) {
                stmt.setObject(1, entryOneId);
                stmt.setObject(2, regattaId);
                stmt.setObject(3, eventId);
                stmt.setObject(4, blockId);
                stmt.setObject(5, crewOneId);
                stmt.setObject(6, clubOneId);
                stmt.setTimestamp(7, now);
                stmt.setTimestamp(8, now);
                stmt.executeUpdate();

                stmt.setObject(1, entryTwoId);
                stmt.setObject(2, regattaId);
                stmt.setObject(3, eventId);
                stmt.setObject(4, blockId);
                stmt.setObject(5, crewTwoId);
                stmt.setObject(6, clubOneId);
                stmt.setTimestamp(7, now);
                stmt.setTimestamp(8, now);
                stmt.executeUpdate();

                stmt.setObject(1, entryThreeId);
                stmt.setObject(2, regattaId);
                stmt.setObject(3, eventId);
                stmt.setObject(4, blockId);
                stmt.setObject(5, crewThreeId);
                stmt.setObject(6, clubTwoId);
                stmt.setTimestamp(7, now);
                stmt.setTimestamp(8, now);
                stmt.executeUpdate();
            }

            conn.commit();
            conn.setAutoCommit(true);
        }

        return new TestData(regattaId, clubOneId, clubTwoId, entryOneId);
    }

    private record TestData(
        UUID regattaId,
        UUID clubOneId,
        UUID clubTwoId,
        UUID entryOneId
    ) {
    }
}
