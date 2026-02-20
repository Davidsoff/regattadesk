package com.regattadesk.linescan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LineScanTileServiceTest {

    @Mock
    private LineScanTileRepository tileRepository;
    @Mock
    private LineScanManifestRepository manifestRepository;
    @Mock
    private MinioStorageAdapter storageAdapter;

    @Test
    void storeTile_marksPendingThenReady() throws Exception {
        UUID regattaId = UUID.randomUUID();
        UUID manifestId = UUID.randomUUID();
        UUID captureSessionId = UUID.randomUUID();

        LineScanTileMetadata metadata = baseMetadata(manifestId, "tile_0_0", LineScanTileMetadata.UploadState.PENDING, 0);
        LineScanManifest manifest = baseManifest(regattaId, captureSessionId, manifestId);

        when(tileRepository.findByRegattaAndTileId(regattaId, "tile_0_0")).thenReturn(Optional.of(metadata));
        when(manifestRepository.findById(manifestId)).thenReturn(Optional.of(manifest));

        LineScanTileService service = new LineScanTileService(tileRepository, manifestRepository, storageAdapter);

        service.storeTile(regattaId, "tile_0_0", new byte[]{1, 2, 3}, "image/webp");

        verify(storageAdapter).storeTile(eq(regattaId), eq(captureSessionId), eq("tile_0_0"), any(byte[].class), eq("image/webp"));

        ArgumentCaptor<LineScanTileMetadata> metadataCaptor = ArgumentCaptor.forClass(LineScanTileMetadata.class);
        verify(tileRepository, times(2)).save(metadataCaptor.capture());

        LineScanTileMetadata pending = metadataCaptor.getAllValues().get(0);
        assertEquals(LineScanTileMetadata.UploadState.PENDING, pending.getUploadState());
        assertEquals(1, pending.getUploadAttempts());
        assertNull(pending.getByteSize());

        LineScanTileMetadata ready = metadataCaptor.getAllValues().get(1);
        assertEquals(LineScanTileMetadata.UploadState.READY, ready.getUploadState());
        assertEquals(1, ready.getUploadAttempts());
        assertEquals(3, ready.getByteSize());
        assertNull(ready.getLastUploadError());
    }

    @Test
    void storeTile_marksFailedWhenUploadThrows() throws Exception {
        UUID regattaId = UUID.randomUUID();
        UUID manifestId = UUID.randomUUID();
        UUID captureSessionId = UUID.randomUUID();

        LineScanTileMetadata metadata = baseMetadata(manifestId, "tile_0_0", LineScanTileMetadata.UploadState.PENDING, 1);
        LineScanManifest manifest = baseManifest(regattaId, captureSessionId, manifestId);

        when(tileRepository.findByRegattaAndTileId(regattaId, "tile_0_0")).thenReturn(Optional.of(metadata));
        when(manifestRepository.findById(manifestId)).thenReturn(Optional.of(manifest));
        doThrow(new MinioStorageAdapter.MinioStorageException("minio down", null))
            .when(storageAdapter)
            .storeTile(eq(regattaId), eq(captureSessionId), eq("tile_0_0"), any(byte[].class), eq("image/png"));

        LineScanTileService service = new LineScanTileService(tileRepository, manifestRepository, storageAdapter);

        MinioStorageAdapter.MinioStorageException error = assertThrows(
            MinioStorageAdapter.MinioStorageException.class,
            () -> service.storeTile(regattaId, "tile_0_0", new byte[]{7}, "image/png")
        );

        assertEquals("minio down", error.getMessage());

        ArgumentCaptor<LineScanTileMetadata> metadataCaptor = ArgumentCaptor.forClass(LineScanTileMetadata.class);
        verify(tileRepository, times(2)).save(metadataCaptor.capture());

        LineScanTileMetadata failed = metadataCaptor.getAllValues().get(1);
        assertEquals(LineScanTileMetadata.UploadState.FAILED, failed.getUploadState());
        assertEquals(2, failed.getUploadAttempts());
        assertEquals("minio down", failed.getLastUploadError());
        assertNull(failed.getByteSize());
    }

    @Test
    void retrieveTile_rejectsWhenUploadNotReady() throws Exception {
        UUID regattaId = UUID.randomUUID();
        UUID manifestId = UUID.randomUUID();
        UUID captureSessionId = UUID.randomUUID();

        LineScanTileMetadata pending = baseMetadata(manifestId, "tile_0_0", LineScanTileMetadata.UploadState.PENDING, 0);
        LineScanManifest manifest = baseManifest(regattaId, captureSessionId, manifestId);

        when(tileRepository.findByRegattaAndTileId(regattaId, "tile_0_0")).thenReturn(Optional.of(pending));
        when(manifestRepository.findById(manifestId)).thenReturn(Optional.of(manifest));

        LineScanTileService service = new LineScanTileService(tileRepository, manifestRepository, storageAdapter);

        LineScanTileService.TileNotFoundException error = assertThrows(
            LineScanTileService.TileNotFoundException.class,
            () -> service.retrieveTile(regattaId, "tile_0_0")
        );

        assertEquals("Tile data not yet available: tile_0_0", error.getMessage());
    }

    private LineScanManifest baseManifest(UUID regattaId, UUID captureSessionId, UUID manifestId) {
        return LineScanManifest.builder()
            .id(manifestId)
            .regattaId(regattaId)
            .captureSessionId(captureSessionId)
            .tileSizePx(512)
            .primaryFormat("webp_lossless")
            .xOriginTimestampMs(1000L)
            .msPerPixel(0.5)
            .build();
    }

    private LineScanTileMetadata baseMetadata(UUID manifestId, String tileId,
                                              LineScanTileMetadata.UploadState state,
                                              Integer attempts) {
        return LineScanTileMetadata.builder()
            .id(UUID.randomUUID())
            .manifestId(manifestId)
            .tileId(tileId)
            .tileX(0)
            .tileY(0)
            .contentType("image/webp")
            .byteSize(null)
            .uploadState(state)
            .uploadAttempts(attempts)
            .lastUploadError(null)
            .minioBucket("bucket")
            .minioObjectKey("session/tile_0_0.webp")
            .build();
    }
}
