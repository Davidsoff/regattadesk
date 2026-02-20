package com.regattadesk.linescan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LineScanManifestServiceTest {

    @Mock
    private LineScanManifestRepository manifestRepository;
    @Mock
    private LineScanTileRepository tileRepository;
    @Mock
    private MinioStorageAdapter storageAdapter;
    @Mock
    private MinioConfiguration minioConfiguration;

    @Test
    void upsertManifest_replacesTilesAndReturnsReloadedAggregate() throws Exception {
        LineScanManifestService service = new LineScanManifestService(
            manifestRepository,
            tileRepository,
            storageAdapter,
            minioConfiguration
        );

        UUID regattaId = UUID.randomUUID();
        UUID captureSessionId = UUID.randomUUID();
        UUID manifestId = UUID.randomUUID();
        LineScanManifest input = LineScanManifest.builder()
            .regattaId(regattaId)
            .captureSessionId(captureSessionId)
            .tileSizePx(512)
            .primaryFormat("webp_lossless")
            .xOriginTimestampMs(1000L)
            .msPerPixel(0.5)
            .tiles(List.of(
                new LineScanManifestTile("tile_0_0", 0, 0, "image/webp", null),
                new LineScanManifestTile("tile_1_0", 1, 0, "image/webp", null)
            ))
            .build();
        LineScanManifest persisted = LineScanManifest.builder()
            .id(manifestId)
            .regattaId(regattaId)
            .captureSessionId(captureSessionId)
            .tileSizePx(512)
            .primaryFormat("webp_lossless")
            .xOriginTimestampMs(1000L)
            .msPerPixel(0.5)
            .tiles(List.of())
            .build();
        LineScanManifest reloaded = LineScanManifest.builder()
            .id(manifestId)
            .regattaId(regattaId)
            .captureSessionId(captureSessionId)
            .tileSizePx(512)
            .primaryFormat("webp_lossless")
            .xOriginTimestampMs(1000L)
            .msPerPixel(0.5)
            .tiles(List.of(new LineScanManifestTile("tile_0_0", 0, 0, "image/webp", null)))
            .build();

        when(manifestRepository.save(any(LineScanManifest.class))).thenReturn(persisted);
        when(manifestRepository.findById(manifestId)).thenReturn(Optional.of(reloaded));
        when(minioConfiguration.getBucketName(regattaId.toString())).thenReturn("regatta-bucket");
        when(minioConfiguration.getTileObjectKey(captureSessionId.toString(), "tile_0_0")).thenReturn("session/tile_0_0.webp");
        when(minioConfiguration.getTileObjectKey(captureSessionId.toString(), "tile_1_0")).thenReturn("session/tile_1_0.webp");

        LineScanManifest result = service.upsertManifest(input);

        verify(storageAdapter).ensureBucket(regattaId);
        verify(tileRepository).deleteByManifestId(manifestId);
        verify(tileRepository, times(2)).save(any(LineScanTileMetadata.class));
        verify(manifestRepository).findById(manifestId);
        assertEquals(manifestId, result.getId());
        assertEquals(1, result.getTiles().size());
    }
}
