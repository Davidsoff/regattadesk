package com.regattadesk.operator;

import com.regattadesk.linescan.model.LineScanTileMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperatorEvidenceWorkspaceServiceTest {

    @Test
    void deriveUploadStateSummarizesTileLifecycle() {
        assertEquals("pending", OperatorEvidenceWorkspaceService.deriveUploadState(List.of()));
        assertEquals("pending", OperatorEvidenceWorkspaceService.deriveUploadState(List.of(tile(LineScanTileMetadata.UploadState.PENDING))));
        assertEquals("completed", OperatorEvidenceWorkspaceService.deriveUploadState(List.of(tile(LineScanTileMetadata.UploadState.READY))));
        assertEquals("syncing", OperatorEvidenceWorkspaceService.deriveUploadState(List.of(
            tile(LineScanTileMetadata.UploadState.READY),
            tile(LineScanTileMetadata.UploadState.PENDING)
        )));
        assertEquals("partial_failure", OperatorEvidenceWorkspaceService.deriveUploadState(List.of(
            tile(LineScanTileMetadata.UploadState.READY),
            tile(LineScanTileMetadata.UploadState.FAILED)
        )));
        assertEquals("failed", OperatorEvidenceWorkspaceService.deriveUploadState(List.of(
            tile(LineScanTileMetadata.UploadState.FAILED),
            tile(LineScanTileMetadata.UploadState.FAILED)
        )));
    }

    private LineScanTileMetadata tile(LineScanTileMetadata.UploadState uploadState) {
        return LineScanTileMetadata.builder()
            .id(UUID.randomUUID())
            .manifestId(UUID.randomUUID())
            .tileId(UUID.randomUUID().toString())
            .tileX(0)
            .tileY(0)
            .contentType("image/webp")
            .uploadState(uploadState)
            .uploadAttempts(0)
            .minioBucket("bucket")
            .minioObjectKey("object")
            .build();
    }
}
