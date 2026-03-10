package com.regattadesk.draw;

import com.regattadesk.eventstore.EventMetadata;
import com.regattadesk.eventstore.EventStore;
import com.regattadesk.regatta.RegattaCreatedEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class DrawWorkflowResourceIT {

    @Inject
    EventStore eventStore;

    @Test
    void blocksEndpoints_shouldCreateListAndReorderBlocks() {
        UUID regattaId = seedRegatta();

        String morningBlockId = given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "regatta_admin")
            .contentType("application/json")
            .body("""
                {
                  "name": "Morning Session",
                  "start_time": "2026-08-15T09:00:00Z",
                  "event_interval_seconds": 300,
                  "crew_interval_seconds": 60
                }
                """)
            .when()
            .post("/api/v1/regattas/" + regattaId + "/blocks")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("name", equalTo("Morning Session"))
            .body("display_order", equalTo(1))
            .extract()
            .path("id");

        String afternoonBlockId = given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "regatta_admin")
            .contentType("application/json")
            .body("""
                {
                  "name": "Afternoon Session",
                  "start_time": "2026-08-15T13:30:00Z",
                  "event_interval_seconds": 420,
                  "crew_interval_seconds": 90
                }
                """)
            .when()
            .post("/api/v1/regattas/" + regattaId + "/blocks")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("display_order", equalTo(2))
            .extract()
            .path("id");

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "regatta_admin")
            .when()
            .get("/api/v1/regattas/" + regattaId + "/blocks")
            .then()
            .statusCode(200)
            .body("data", hasSize(2))
            .body("data[0].id", equalTo(morningBlockId))
            .body("data[1].id", equalTo(afternoonBlockId));

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "regatta_admin")
            .contentType("application/json")
            .body(String.format("""
                {
                  "items": [
                    { "block_id": "%s", "display_order": 1 },
                    { "block_id": "%s", "display_order": 2 }
                  ]
                }
                """, afternoonBlockId, morningBlockId))
            .when()
            .post("/api/v1/regattas/" + regattaId + "/blocks/reorder")
            .then()
            .statusCode(200)
            .body("data", hasSize(2))
            .body("data[0].id", equalTo(afternoonBlockId))
            .body("data[0].display_order", equalTo(1))
            .body("data[1].id", equalTo(morningBlockId))
            .body("data[1].display_order", equalTo(2));
    }

    @Test
    void bibPoolEndpoints_shouldRejectOverlappingPoolsWithMachineReadableError() {
        UUID regattaId = seedRegatta();

        String blockId = given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "regatta_admin")
            .contentType("application/json")
            .body("""
                {
                  "name": "Morning Session",
                  "start_time": "2026-08-15T09:00:00Z",
                  "event_interval_seconds": 300,
                  "crew_interval_seconds": 60
                }
                """)
            .when()
            .post("/api/v1/regattas/" + regattaId + "/blocks")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "regatta_admin")
            .contentType("application/json")
            .body(String.format("""
                {
                  "name": "Primary Pool",
                  "block_id": "%s",
                  "allocation_mode": "range",
                  "start_bib": 1,
                  "end_bib": 50,
                  "priority": 1,
                  "is_overflow": false
                }
                """, blockId))
            .when()
            .post("/api/v1/regattas/" + regattaId + "/bib_pools")
            .then()
            .statusCode(201);

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "regatta_admin")
            .contentType("application/json")
            .body(String.format("""
                {
                  "name": "Overlap Pool",
                  "block_id": "%s",
                  "allocation_mode": "range",
                  "start_bib": 40,
                  "end_bib": 75,
                  "priority": 2,
                  "is_overflow": false
                }
                """, blockId))
            .when()
            .post("/api/v1/regattas/" + regattaId + "/bib_pools")
            .then()
            .statusCode(400)
            .body("error.code", equalTo("BIB_POOL_VALIDATION_ERROR"))
            .body("error.message", equalTo("Bib pool overlap detected"))
            .body("error.details.overlapping_bibs", hasSize(11));
    }

    @Test
    void drawLifecycleEndpoints_shouldGeneratePublishAndUnpublishWithRevisionState() {
        UUID regattaId = seedRegatta();

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "regatta_admin")
            .contentType("application/json")
            .body("""
                {
                  "seed": 246813579
                }
                """)
            .when()
            .post("/api/v1/regattas/" + regattaId + "/draw/generate")
            .then()
            .statusCode(200)
            .body("seed", equalTo(246813579))
            .body("generated", equalTo(true))
            .body("published", equalTo(false));

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "regatta_admin")
            .when()
            .post("/api/v1/regattas/" + regattaId + "/draw/publish")
            .then()
            .statusCode(200)
            .body("draw_revision", equalTo(1))
            .body("results_revision", equalTo(0))
            .body("published", equalTo(true));

        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "regatta_admin")
            .when()
            .post("/api/v1/regattas/" + regattaId + "/draw/unpublish")
            .then()
            .statusCode(200)
            .body("draw_revision", equalTo(1))
            .body("results_revision", equalTo(0))
            .body("published", equalTo(false))
            .body("generated", equalTo(false));
    }

    private UUID seedRegatta() {
        UUID regattaId = UUID.randomUUID();
        RegattaCreatedEvent event = new RegattaCreatedEvent(
            regattaId,
            "Issue 135 Regatta",
            "BC04 workflow fixture",
            "Europe/Amsterdam",
            new BigDecimal("50.00"),
            "EUR",
            60,
            false
        );

        eventStore.append(
            regattaId,
            "Regatta",
            -1,
            List.of(event),
            EventMetadata.builder().build()
        );

        return regattaId;
    }
}
