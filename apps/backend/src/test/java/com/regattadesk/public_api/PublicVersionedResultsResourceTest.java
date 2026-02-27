package com.regattadesk.public_api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Cookie;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class PublicVersionedResultsResourceTest {

    private static final String COOKIE_NAME = "regattadesk_public_session";
    private static final String SESSION_ENDPOINT = "/public/session";

    @Inject
    DataSource dataSource;

    private UUID testRegattaId;
    private UUID entry1;
    private UUID entry2;
    private UUID entryWithdrawn;
    private UUID entryWithdrawnAfterDraw;
    private UUID eventId;
    private Cookie sessionCookie;

    @BeforeEach
    void setUp() throws Exception {
        testRegattaId = UUID.randomUUID();
        entry1 = UUID.randomUUID();
        entry2 = UUID.randomUUID();
        entryWithdrawn = UUID.randomUUID();
        entryWithdrawnAfterDraw = UUID.randomUUID();
        eventId = UUID.randomUUID();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement regattaStmt = conn.prepareStatement(
                 "INSERT INTO regattas (id, name, description, time_zone, status, entry_fee, currency, " +
                     "draw_revision, results_revision, created_at, updated_at) " +
                     "VALUES (?, 'Results Test Regatta', 'Test', 'Europe/Amsterdam', 'published', 25.00, 'EUR', 2, 3, now(), now())"
             );
             PreparedStatement resultsStmt = conn.prepareStatement(
                 "INSERT INTO public_regatta_results (" +
                     "regatta_id, draw_revision, results_revision, entry_id, event_id, bib, crew_name, club_name, " +
                     "elapsed_time_ms, penalties_ms, rank, status, is_provisional, is_edited, is_official, updated_at" +
                     ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())"
             )) {
            regattaStmt.setObject(1, testRegattaId);
            regattaStmt.executeUpdate();

            insertRow(resultsStmt, entry1, 11, "Crew One", "Club A", 321000, 0, 1, "entered", false, false, true);
            insertRow(resultsStmt, entry2, 12, "Crew Two", "Club B", 330000, 5000, 2, "entered", true, true, false);
            insertRow(resultsStmt, entryWithdrawn, 13, "Withdrawn Crew", "Club C", null, 0, null, "withdrawn_before_draw", true, false, false);
            insertRow(resultsStmt, entryWithdrawnAfterDraw, 14, "Late Withdrawn Crew", "Club D", null, 0, null, "withdrawn_after_draw", false, false, false);
        }

        Response sessionResponse = given()
            .when().post(SESSION_ENDPOINT)
            .then()
            .statusCode(204)
            .extract().response();

        sessionCookie = sessionResponse.getDetailedCookie(COOKIE_NAME);
        assertNotNull(sessionCookie);
    }

    @Test
    void getResults_returnsVersionedRowsAndExcludesOnlyWithdrawnBeforeDraw() {
        String path = String.format("/public/v2-3/regattas/%s/results", testRegattaId);

        given()
            .cookie(COOKIE_NAME, sessionCookie.getValue())
            .when()
            .get(path)
            .then()
            .statusCode(200)
            .body("draw_revision", equalTo(2))
            .body("results_revision", equalTo(3))
            .body("data", hasSize(3))
            .body("data[0].entry_id", equalTo(entry1.toString()))
            .body("data[0].event_id", equalTo(eventId.toString()))
            .body("data[0].rank", equalTo(1))
            .body("data[0].is_official", equalTo(true))
            .body("data[1].entry_id", equalTo(entry2.toString()))
            .body("data[1].penalties_ms", equalTo(5000))
            .body("data[1].is_provisional", equalTo(true))
            .body("data[2].entry_id", equalTo(entryWithdrawnAfterDraw.toString()))
            .body("data[2].status", equalTo("withdrawn_after_draw"));
    }

    private void insertRow(
        PreparedStatement stmt,
        UUID entryId,
        Integer bib,
        String crewName,
        String clubName,
        Integer elapsedTimeMs,
        int penaltiesMs,
        Integer rank,
        String status,
        boolean provisional,
        boolean edited,
        boolean official
    ) throws Exception {
        stmt.setObject(1, testRegattaId);
        stmt.setInt(2, 2);
        stmt.setInt(3, 3);
        stmt.setObject(4, entryId);
        stmt.setObject(5, eventId);
        stmt.setObject(6, bib);
        stmt.setString(7, crewName);
        stmt.setString(8, clubName);
        stmt.setObject(9, elapsedTimeMs);
        stmt.setInt(10, penaltiesMs);
        stmt.setObject(11, rank);
        stmt.setString(12, status);
        stmt.setBoolean(13, provisional);
        stmt.setBoolean(14, edited);
        stmt.setBoolean(15, official);
        stmt.executeUpdate();
    }
}
