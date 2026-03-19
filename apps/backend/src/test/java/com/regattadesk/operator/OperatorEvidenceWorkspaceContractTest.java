package com.regattadesk.operator;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;

@QuarkusTest
@Tag("contract")
class OperatorEvidenceWorkspaceContractTest {

    @Inject
    DataSource dataSource;

    @Inject
    OperatorTokenService operatorTokenService;

    @Test
    void contract_getWorkspaceReturnsStableErrorShapeForMissingSelectors() throws Exception {
        UUID regattaId = UUID.randomUUID();
        seedRegatta(regattaId);
        String token = operatorTokenService.issueToken(
            regattaId,
            null,
            "line-scan",
            Instant.now().minusSeconds(60),
            Instant.now().plusSeconds(3600)
        ).getToken();

        given()
            .header("X-Operator-Token", token)
        .when()
            .get("/api/v1/regattas/" + regattaId + "/operator/evidence_workspace")
        .then()
            .statusCode(400)
            .contentType("application/json")
            .body("$", hasKey("error"))
            .body("error.code", equalTo("BAD_REQUEST"))
            .body("error.message", equalTo("Either capture_session_id or event_id is required"));
    }

    private void seedRegatta(UUID regattaId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO regattas (id, name, description, time_zone, status, entry_fee, currency, draw_revision, results_revision, created_at, updated_at) VALUES (?, 'Contract Workspace', 'Test', 'Europe/Amsterdam', 'draft', 25.00, 'EUR', 0, 0, now(), now())"
             )) {
            statement.setObject(1, regattaId);
            statement.executeUpdate();
        }
    }
}
