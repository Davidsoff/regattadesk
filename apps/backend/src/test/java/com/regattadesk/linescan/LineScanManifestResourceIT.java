package com.regattadesk.linescan;

import com.regattadesk.operator.OperatorTokenService;
import jakarta.inject.Inject;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for line-scan manifest API endpoints.
 */
@QuarkusTest
public class LineScanManifestResourceIT {

    @Inject
    OperatorTokenService operatorTokenService;

    private String validOperatorToken(UUID regattaId) {
        return operatorTokenService.issueToken(
                regattaId,
                null,
                "line-scan",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600))
            .getToken();
    }
    
    @Test
    public void testUpsertManifest_withOperatorToken_succeeds() {
        UUID regattaId = UUID.randomUUID();
        UUID captureSessionId = UUID.randomUUID();
        String token = validOperatorToken(regattaId);
        
        // Create manifest request
        Map<String, Object> request = Map.of(
            "capture_session_id", captureSessionId.toString(),
            "tile_size_px", 512,
            "primary_format", "webp_lossless",
            "x_origin_timestamp_ms", 1000000L,
            "ms_per_pixel", 0.5,
            "tiles", List.of(
                Map.of(
                    "tile_id", "tile_0_0",
                    "tile_x", 0,
                    "tile_y", 0,
                    "content_type", "image/webp"
                ),
                Map.of(
                    "tile_id", "tile_1_0",
                    "tile_x", 1,
                    "tile_y", 0,
                    "content_type", "image/webp"
                )
            )
        );
        
        given()
            .header("X-Operator-Token", token)
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/line_scan/manifests")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("regatta_id", equalTo(regattaId.toString()))
            .body("capture_session_id", equalTo(captureSessionId.toString()))
            .body("tile_size_px", equalTo(512))
            .body("primary_format", equalTo("webp_lossless"))
            .body("x_origin_timestamp_ms", equalTo(1000000))
            .body("ms_per_pixel", equalTo(0.5f))
            .body("tiles", hasSize(2))
            .body("retention_state", equalTo("full_retained"))
            .body("retention_days", equalTo(14))
            .body("prune_window_seconds", equalTo(2));
    }
    
    @Test
    public void testUpsertManifest_withoutOperatorToken_returnsUnauthorized() {
        UUID regattaId = UUID.randomUUID();
        
        Map<String, Object> request = Map.of(
            "capture_session_id", UUID.randomUUID().toString(),
            "tile_size_px", 512,
            "primary_format", "webp_lossless",
            "x_origin_timestamp_ms", 1000000L,
            "ms_per_pixel", 0.5,
            "tiles", List.of(
                Map.of(
                    "tile_id", "tile_0_0",
                    "tile_x", 0,
                    "tile_y", 0,
                    "content_type", "image/webp"
                )
            )
        );
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/line_scan/manifests")
        .then()
            .statusCode(401);
    }
    
    @Test
    public void testGetManifest_withOperatorToken_succeeds() {
        // First create a manifest
        UUID regattaId = UUID.randomUUID();
        UUID captureSessionId = UUID.randomUUID();
        String token = validOperatorToken(regattaId);
        
        Map<String, Object> createRequest = Map.of(
            "capture_session_id", captureSessionId.toString(),
            "tile_size_px", 1024,
            "primary_format", "png",
            "x_origin_timestamp_ms", 2000000L,
            "ms_per_pixel", 1.0,
            "tiles", List.of(
                Map.of(
                    "tile_id", "tile_0_0",
                    "tile_x", 0,
                    "tile_y", 0,
                    "content_type", "image/png"
                )
            )
        );
        
        String manifestId = given()
            .header("X-Operator-Token", token)
            .contentType(ContentType.JSON)
            .body(createRequest)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/line_scan/manifests")
        .then()
            .statusCode(201)
            .extract()
            .path("id");
        
        // Now retrieve it
        given()
            .header("X-Operator-Token", token)
        .when()
            .get("/api/v1/regattas/" + regattaId + "/line_scan/manifests/" + manifestId)
        .then()
            .statusCode(200)
            .body("id", equalTo(manifestId))
            .body("regatta_id", equalTo(regattaId.toString()))
            .body("capture_session_id", equalTo(captureSessionId.toString()))
            .body("tile_size_px", equalTo(1024))
            .body("primary_format", equalTo("png"))
            .body("tiles", hasSize(1));
    }
    
    @Test
    public void testGetManifest_withStaffAuth_succeeds() {
        // First create a manifest
        UUID regattaId = UUID.randomUUID();
        UUID captureSessionId = UUID.randomUUID();
        String token = validOperatorToken(regattaId);
        
        Map<String, Object> createRequest = Map.of(
            "capture_session_id", captureSessionId.toString(),
            "tile_size_px", 512,
            "primary_format", "webp_lossless",
            "x_origin_timestamp_ms", 3000000L,
            "ms_per_pixel", 0.25,
            "tiles", List.of(
                Map.of(
                    "tile_id", "tile_0_0",
                    "tile_x", 0,
                    "tile_y", 0,
                    "content_type", "image/webp"
                )
            )
        );
        
        String manifestId = given()
            .header("X-Operator-Token", token)
            .contentType(ContentType.JSON)
            .body(createRequest)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/line_scan/manifests")
        .then()
            .statusCode(201)
            .extract()
            .path("id");
        
        // Now retrieve it with staff auth
        given()
            .header("X-Forwarded-User", "admin@example.com")
        .when()
            .get("/api/v1/regattas/" + regattaId + "/line_scan/manifests/" + manifestId)
        .then()
            .statusCode(200)
            .body("id", equalTo(manifestId));
    }
    
    @Test
    public void testGetManifest_withoutAuth_returnsUnauthorized() {
        UUID regattaId = UUID.randomUUID();
        UUID manifestId = UUID.randomUUID();
        
        given()
        .when()
            .get("/api/v1/regattas/" + regattaId + "/line_scan/manifests/" + manifestId)
        .then()
            .statusCode(401);
    }
    
    @Test
    public void testGetManifest_notFound_returns404() {
        UUID regattaId = UUID.randomUUID();
        UUID manifestId = UUID.randomUUID();
        String token = validOperatorToken(regattaId);
        
        given()
            .header("X-Operator-Token", token)
        .when()
            .get("/api/v1/regattas/" + regattaId + "/line_scan/manifests/" + manifestId)
        .then()
            .statusCode(404);
    }

    @Test
    public void testUpsertManifest_replacesExistingTiles() {
        UUID regattaId = UUID.randomUUID();
        UUID captureSessionId = UUID.randomUUID();
        String token = validOperatorToken(regattaId);

        Map<String, Object> initialRequest = Map.of(
            "capture_session_id", captureSessionId.toString(),
            "tile_size_px", 512,
            "primary_format", "webp_lossless",
            "x_origin_timestamp_ms", 1000L,
            "ms_per_pixel", 0.5,
            "tiles", List.of(
                Map.of(
                    "tile_id", "tile_0_0",
                    "tile_x", 0,
                    "tile_y", 0,
                    "content_type", "image/webp"
                ),
                Map.of(
                    "tile_id", "tile_1_0",
                    "tile_x", 1,
                    "tile_y", 0,
                    "content_type", "image/webp"
                )
            )
        );

        String manifestId = given()
            .header("X-Operator-Token", token)
            .contentType(ContentType.JSON)
            .body(initialRequest)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/line_scan/manifests")
        .then()
            .statusCode(201)
            .body("tiles", hasSize(2))
            .extract()
            .path("id");

        Map<String, Object> replacementRequest = Map.of(
            "capture_session_id", captureSessionId.toString(),
            "tile_size_px", 512,
            "primary_format", "webp_lossless",
            "x_origin_timestamp_ms", 1000L,
            "ms_per_pixel", 0.5,
            "tiles", List.of(
                Map.of(
                    "tile_id", "tile_0_0",
                    "tile_x", 0,
                    "tile_y", 0,
                    "content_type", "image/webp"
                )
            )
        );

        given()
            .header("X-Operator-Token", token)
            .contentType(ContentType.JSON)
            .body(replacementRequest)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/line_scan/manifests")
        .then()
            .statusCode(201)
            .body("tiles", hasSize(1))
            .body("tiles[0].tile_id", equalTo("tile_0_0"));

        given()
            .header("X-Operator-Token", token)
        .when()
            .get("/api/v1/regattas/" + regattaId + "/line_scan/manifests/" + manifestId)
        .then()
            .statusCode(200)
            .body("tiles", hasSize(1))
            .body("tiles[0].tile_id", equalTo("tile_0_0"));
    }

    @Test
    public void testUpsertManifest_withInvalidTileSize_returnsBadRequest() {
        UUID regattaId = UUID.randomUUID();
        String token = validOperatorToken(regattaId);

        Map<String, Object> request = Map.of(
            "capture_session_id", UUID.randomUUID().toString(),
            "tile_size_px", 513,
            "primary_format", "webp_lossless",
            "x_origin_timestamp_ms", 1000000L,
            "ms_per_pixel", 0.5,
            "tiles", List.of()
        );

        given()
            .header("X-Operator-Token", token)
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/line_scan/manifests")
        .then()
            .statusCode(400);
    }

    @Test
    public void testUpsertManifest_withInvalidFormats_returnsBadRequest() {
        UUID regattaId = UUID.randomUUID();
        String token = validOperatorToken(regattaId);

        Map<String, Object> request = Map.of(
            "capture_session_id", UUID.randomUUID().toString(),
            "tile_size_px", 512,
            "primary_format", "jpeg",
            "fallback_format", "webp",
            "x_origin_timestamp_ms", 1000000L,
            "ms_per_pixel", 0.5,
            "tiles", List.of()
        );

        given()
            .header("X-Operator-Token", token)
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/regattas/" + regattaId + "/line_scan/manifests")
        .then()
            .statusCode(400);
    }
}
