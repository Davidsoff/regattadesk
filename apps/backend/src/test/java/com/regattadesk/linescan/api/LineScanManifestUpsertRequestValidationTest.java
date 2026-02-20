package com.regattadesk.linescan.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineScanManifestUpsertRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void rejectsInvalidTileSize() {
        LineScanManifestUpsertRequest request = validRequest();
        request.setTileSizePx(513);

        Set<ConstraintViolation<LineScanManifestUpsertRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("tile_size_px must be 512 or 1024")));
    }

    @Test
    void rejectsInvalidFormats() {
        LineScanManifestUpsertRequest request = validRequest();
        request.setPrimaryFormat("jpeg");
        request.setFallbackFormat("webp_lossless");

        Set<ConstraintViolation<LineScanManifestUpsertRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("primary_format must be webp_lossless or png")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("fallback_format must be png")));
    }

    @Test
    void allowsEmptyTiles() {
        LineScanManifestUpsertRequest request = validRequest();
        request.setTiles(List.of());

        Set<ConstraintViolation<LineScanManifestUpsertRequest>> violations = validator.validate(request);

        assertFalse(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("tiles")));
    }

    private static LineScanManifestUpsertRequest validRequest() {
        LineScanManifestUpsertRequest request = new LineScanManifestUpsertRequest();
        request.setCaptureSessionId(UUID.randomUUID());
        request.setTileSizePx(512);
        request.setPrimaryFormat("webp_lossless");
        request.setFallbackFormat("png");
        request.setXOriginTimestampMs(1_000L);
        request.setMsPerPixel(0.5);

        LineScanManifestUpsertRequest.TileDto tile = new LineScanManifestUpsertRequest.TileDto();
        tile.setTileId("tile_0_0");
        tile.setTileX(0);
        tile.setTileY(0);
        tile.setContentType("image/webp");
        tile.setByteSize(123);
        request.setTiles(List.of(tile));
        return request;
    }
}
