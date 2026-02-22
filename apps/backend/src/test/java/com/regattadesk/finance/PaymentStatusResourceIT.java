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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class PaymentStatusResourceIT {

    @Inject
    DataSource dataSource;

    @Test
    void entryPaymentStatus_canBeUpdatedAndIsAuditable() throws Exception {
        TestData data = seedFinanceData();

        given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .when()
            .get("/api/v1/regattas/" + data.regattaId + "/entries/" + data.entryOneId + "/payment_status")
            .then()
            .statusCode(200)
            .body("payment_status", equalTo("unpaid"));

        given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .contentType("application/json")
            .body("""
                {
                  "payment_status": "paid",
                  "payment_reference": "BANK-REF-123"
                }
                """)
            .when()
            .put("/api/v1/regattas/" + data.regattaId + "/entries/" + data.entryOneId + "/payment_status")
            .then()
            .statusCode(200)
            .body("payment_status", equalTo("paid"))
            .body("payment_reference", equalTo("BANK-REF-123"))
            .body("paid_by", equalTo("fin-user"));

        assertEquals(1, countAuditEvents("EntryPaymentStatusUpdated", data.entryOneId));
    }

    @Test
    void clubPaymentStatus_updateSetsAllBillableEntries() throws Exception {
        TestData data = seedFinanceData();

        given()
            .header("Remote-User", "fin-user")
            .header("Remote-Groups", "financial_manager")
            .contentType("application/json")
            .body("""
                {
                  "payment_status": "paid",
                  "payment_reference": "CLUB-BULK-001"
                }
                """)
            .when()
            .put("/api/v1/regattas/" + data.regattaId + "/clubs/" + data.clubId + "/payment_status")
            .then()
            .statusCode(200)
            .body("club_id", equalTo(data.clubId.toString()))
            .body("payment_status", equalTo("paid"))
            .body("billable_entry_count", equalTo(2))
            .body("paid_entry_count", equalTo(2));

        assertEquals(1, countAuditEvents("ClubPaymentStatusUpdateRequested", data.clubId));
        assertEquals(2, countAuditEvents("EntryPaymentStatusUpdated", data.entryOneId) +
            countAuditEvents("EntryPaymentStatusUpdated", data.entryTwoId));
    }

    @Test
    void unauthorizedRoles_cannotMutatePaymentStatus() throws Exception {
        TestData data = seedFinanceData();

        given()
            .header("Remote-User", "desk-user")
            .header("Remote-Groups", "info_desk")
            .contentType("application/json")
            .body("""
                {
                  "payment_status": "paid"
                }
                """)
            .when()
            .put("/api/v1/regattas/" + data.regattaId + "/entries/" + data.entryOneId + "/payment_status")
            .then()
            .statusCode(403);

        given()
            .header("Remote-User", "desk-user")
            .header("Remote-Groups", "info_desk")
            .contentType("application/json")
            .body("""
                {
                  "payment_status": "paid"
                }
                """)
            .when()
            .put("/api/v1/regattas/" + data.regattaId + "/clubs/" + data.clubId + "/payment_status")
            .then()
            .statusCode(403);
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

    private TestData seedFinanceData() throws Exception {
        UUID regattaId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID boatTypeId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID crewOneId = UUID.randomUUID();
        UUID crewTwoId = UUID.randomUUID();
        UUID entryOneId = UUID.randomUUID();
        UUID entryTwoId = UUID.randomUUID();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO regattas (id, name, time_zone, status, entry_fee, currency, draw_revision, results_revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?, ?)
                """)) {
                Timestamp now = Timestamp.from(Instant.now());
                stmt.setObject(1, regattaId);
                stmt.setString(2, "Finance IT Regatta");
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
                Timestamp now = Timestamp.from(Instant.now());
                stmt.setObject(1, clubId);
                stmt.setString(2, "Finance Club");
                stmt.setString(3, "FC");
                stmt.setTimestamp(4, now);
                stmt.setTimestamp(5, now);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO categories (id, name, gender, is_global, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
                Timestamp now = Timestamp.from(Instant.now());
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
                Timestamp now = Timestamp.from(Instant.now());
                stmt.setObject(1, boatTypeId);
                stmt.setString(2, "IT" + UUID.randomUUID().toString().substring(0, 6));
                stmt.setString(3, "IT Boat");
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
                Timestamp now = Timestamp.from(Instant.now());
                stmt.setObject(1, blockId);
                stmt.setObject(2, regattaId);
                stmt.setString(3, "Block 1");
                stmt.setTimestamp(4, now);
                stmt.setTimestamp(5, now);
                stmt.setTimestamp(6, now);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO events (id, regatta_id, category_id, boat_type_id, name, display_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 0, ?, ?)
                """)) {
                Timestamp now = Timestamp.from(Instant.now());
                stmt.setObject(1, eventId);
                stmt.setObject(2, regattaId);
                stmt.setObject(3, categoryId);
                stmt.setObject(4, boatTypeId);
                stmt.setString(5, "Event 1");
                stmt.setTimestamp(6, now);
                stmt.setTimestamp(7, now);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO crews (id, display_name, is_composite, club_id, created_at, updated_at)
                VALUES (?, ?, FALSE, ?, ?, ?)
                """)) {
                Timestamp now = Timestamp.from(Instant.now());
                stmt.setObject(1, crewOneId);
                stmt.setString(2, "Crew One");
                stmt.setObject(3, clubId);
                stmt.setTimestamp(4, now);
                stmt.setTimestamp(5, now);
                stmt.executeUpdate();

                stmt.setObject(1, crewTwoId);
                stmt.setString(2, "Crew Two");
                stmt.setObject(3, clubId);
                stmt.setTimestamp(4, now);
                stmt.setTimestamp(5, now);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO entries (id, regatta_id, event_id, block_id, crew_id, billing_club_id, status, payment_status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'entered', 'unpaid', ?, ?)
                """)) {
                Timestamp now = Timestamp.from(Instant.now());
                stmt.setObject(1, entryOneId);
                stmt.setObject(2, regattaId);
                stmt.setObject(3, eventId);
                stmt.setObject(4, blockId);
                stmt.setObject(5, crewOneId);
                stmt.setObject(6, clubId);
                stmt.setTimestamp(7, now);
                stmt.setTimestamp(8, now);
                stmt.executeUpdate();

                stmt.setObject(1, entryTwoId);
                stmt.setObject(2, regattaId);
                stmt.setObject(3, eventId);
                stmt.setObject(4, blockId);
                stmt.setObject(5, crewTwoId);
                stmt.setObject(6, clubId);
                stmt.setTimestamp(7, now);
                stmt.setTimestamp(8, now);
                stmt.executeUpdate();
            }

            conn.commit();
        }

        return new TestData(regattaId, clubId, entryOneId, entryTwoId);
    }

    private record TestData(
        UUID regattaId,
        UUID clubId,
        UUID entryOneId,
        UUID entryTwoId
    ) {
    }
}
