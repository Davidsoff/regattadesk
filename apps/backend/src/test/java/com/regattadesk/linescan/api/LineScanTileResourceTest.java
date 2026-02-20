package com.regattadesk.linescan.api;

import com.regattadesk.linescan.LineScanTileService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LineScanTileResourceTest {

    @Test
    void uploadTile_rejectsOversizedPayload() {
        LineScanTileService tileService = mock(LineScanTileService.class);
        LineScanTileResource resource = new LineScanTileResource(tileService);

        byte[] tileData = new byte[(10 * 1024 * 1024) + 1];
        Response response = resource.uploadTile(
            UUID.randomUUID(),
            "tile_0_0",
            "test-operator-token",
            "image/webp",
            tileData
        );

        assertEquals(400, response.getStatus());
        verifyNoInteractions(tileService);
    }
}
