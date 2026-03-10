package com.regattadesk.adjudication;

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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class AdjudicationResourceIT {

    @Inject
    DataSource dataSource;

    @Test
    void investigationDetail_returnsEntryStateHistoryAndRevisionImpact() throws Exception {
        TestData data = seedAdjudicationData();

        given()
            .header("Remote-User", "jury-user")
            .header("Remote-Groups", "head_of_jury")
        .when()
            .get("/api/v1/regattas/" + data.regattaId + "/adjudication/entries/" + data.entryId)
        .then()
            .statusCode(200)
            .body("entry.entry_id", equalTo(data.entryId.toString()))
            .body("entry.status", equalTo("entered"))
            .body("entry.result_label", equalTo("provisional"))
            .body("investigations", hasSize(0))
            .body("revision_impact.current_results_revision", equalTo(0))
            .body("revision_impact.next_results_revision", equalTo(1))
            .body("revision_impact.message", equalTo("Next adjudication change will advance results revision to 1."));
    }

    @Test
    void dsqAndRevert_actionsMutateEntryStateAndResultsRevision() throws Exception {
        TestData data = seedAdjudicationData();

        given()
            .header("Remote-User", "jury-user")
            .header("Remote-Groups", "head_of_jury")
            .contentType("application/json")
            .body("""
                {
                  "reason": "Lane violation confirmed on review",
                  "note": "DSQ after jury review"
                }
                """)
        .when()
            .post("/api/v1/regattas/" + data.regattaId + "/adjudication/entries/" + data.entryId + "/dsq")
        .then()
            .statusCode(200)
            .body("entry.entry_id", equalTo(data.entryId.toString()))
            .body("entry.status", equalTo("dsq"))
            .body("entry.result_label", equalTo("edited"))
            .body("revision_impact.current_results_revision", equalTo(1))
            .body("revision_impact.message", equalTo("Results revision advanced to 1 after DSQ."))
            .body("history", hasSize(1));

        given()
            .header("Remote-User", "jury-user")
            .header("Remote-Groups", "head_of_jury")
        .when()
            .get("/api/v1/regattas/" + data.regattaId + "/adjudication/entries/" + data.entryId)
        .then()
            .statusCode(200)
            .body("revision_impact.current_results_revision", equalTo(1))
            .body("revision_impact.next_results_revision", equalTo(2))
            .body("revision_impact.message", equalTo("Results revision is 1 after DSQ. Next adjudication change will advance it to 2."));

        given()
            .header("Remote-User", "jury-user")
            .header("Remote-Groups", "head_of_jury")
            .contentType("application/json")
            .body("""
                {
                  "reason": "Video review overturned the DSQ",
                  "note": "Restore original state"
                }
                """)
        .when()
            .post("/api/v1/regattas/" + data.regattaId + "/adjudication/entries/" + data.entryId + "/revert_dsq")
        .then()
            .statusCode(200)
            .body("entry.status", equalTo("entered"))
            .body("entry.result_label", equalTo("provisional"))
            .body("revision_impact.current_results_revision", equalTo(2))
            .body("revision_impact.next_results_revision", equalTo(3))
            .body("history", hasSize(2))
            .body("history[1].action", equalTo("dsq_reverted"));
    }

    @Test
    void penaltyAction_requiresJuryRole() throws Exception {
        TestData data = seedAdjudicationData();

        given()
            .header("Remote-User", "desk-user")
            .header("Remote-Groups", "info_desk")
            .contentType("application/json")
            .body("""
                {
                  "reason": "Late buoy turn",
                  "note": "Apply standard penalty",
                  "penalty_seconds": 15
                }
                """)
        .when()
            .post("/api/v1/regattas/" + data.regattaId + "/adjudication/entries/" + data.entryId + "/penalty")
        .then()
            .statusCode(403);
    }

    private TestData seedAdjudicationData() throws Exception {
        UUID regattaId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID boatTypeId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID crewId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            Timestamp now = Timestamp.from(Instant.now());

            try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO regattas (id, name, time_zone, status, entry_fee, currency, draw_revision, results_revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?, ?)
                """)) {
                stmt.setObject(1, regattaId);
                stmt.setString(2, "Adjudication IT Regatta");
                stmt.setString(3, "Europe/Amsterdam");
                stmt.setString(4, "published");
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
                stmt.setObject(1, clubId);
                stmt.setString(2, "Adjudication Club");
                stmt.setString(3, "AC");
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
                stmt.setString(2, "ADJ" + UUID.randomUUID().toString().substring(0, 5));
                stmt.setString(3, "Adjudication Boat");
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
                stmt.setObject(1, eventId);
                stmt.setObject(2, regattaId);
                stmt.setObject(3, categoryId);
                stmt.setObject(4, boatTypeId);
                stmt.setString(5, "Open 1x");
                stmt.setTimestamp(6, now);
                stmt.setTimestamp(7, now);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO crews (id, display_name, is_composite, club_id, created_at, updated_at)
                VALUES (?, ?, FALSE, ?, ?, ?)
                """)) {
                stmt.setObject(1, crewId);
                stmt.setString(2, "Crew One");
                stmt.setObject(3, clubId);
                stmt.setTimestamp(4, now);
                stmt.setTimestamp(5, now);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO entries (id, regatta_id, event_id, block_id, crew_id, billing_club_id, status, payment_status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'entered', 'unpaid', ?, ?)
                """)) {
                stmt.setObject(1, entryId);
                stmt.setObject(2, regattaId);
                stmt.setObject(3, eventId);
                stmt.setObject(4, blockId);
                stmt.setObject(5, crewId);
                stmt.setObject(6, clubId);
                stmt.setTimestamp(7, now);
                stmt.setTimestamp(8, now);
                stmt.executeUpdate();
            }

            conn.commit();
        }

        return new TestData(regattaId, entryId);
    }

    private record TestData(UUID regattaId, UUID entryId) {
    }
}
