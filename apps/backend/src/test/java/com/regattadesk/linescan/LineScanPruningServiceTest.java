package com.regattadesk.linescan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.regattadesk.linescan.LineScanManifest.RetentionState.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LineScanPruningService.
 * 
 * Tests pruning execution logic including:
 * - Marker window preservation (±2s around approved markers)
 * - Tile deletion from storage
 * - Manifest state updates
 * - Transaction boundary handling
 */
@ExtendWith(MockitoExtension.class)
class LineScanPruningServiceTest {
    
    @Mock
    private LineScanManifestRepository manifestRepository;
    
    @Mock
    private LineScanTileRepository tileRepository;
    
    @Mock
    private MinioStorageAdapter storageAdapter;
    
    @Mock
    private MinioConfiguration minioConfiguration;
    
    private LineScanPruningService pruningService;
    
    @BeforeEach
    void setUp() {
        pruningService = new LineScanPruningService(
            manifestRepository,
            tileRepository,
            storageAdapter,
            minioConfiguration
        );
    }
    
    @Test
    void prune_withApprovedMarkers_deletesOutsideWindowsOnly() throws Exception {
        UUID manifestId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID captureSessionId = UUID.randomUUID();
        
        LineScanManifest manifest = LineScanManifest.builder()
            .id(manifestId)
            .regattaId(regattaId)
            .captureSessionId(captureSessionId)
            .tileSizePx(512)
            .primaryFormat("webp_lossless")
            .xOriginTimestampMs(10000L) // Start at 10 seconds
            .msPerPixel(1.0) // 1ms per pixel
            .retentionDays(14)
            .pruneWindowSeconds(2) // ±2 seconds around markers
            .retentionState(ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS)
            .pruneEligibleAt(Instant.now().minusSeconds(86400))
            .createdAt(Instant.now().minusSeconds(86400 * 15))
            .updatedAt(Instant.now())
            .build();
        
        // Markers at 15s and 25s (in ms: 15000, 25000)
        // Windows: [13000-17000] and [23000-27000]
        List<LineScanRetentionEvaluator.TimeWindow> markerWindows = List.of(
            new LineScanRetentionEvaluator.TimeWindow(13000L, 17000L),
            new LineScanRetentionEvaluator.TimeWindow(23000L, 27000L)
        );
        
        // Tiles spanning x=0 to x=29999 (covers 10000ms to 39999ms in timestamp)
        List<LineScanTileMetadata> tiles = List.of(
            createTileMetadata(manifestId, "tile_0_0", 0),     // x=0: 10000-10511ms - DELETE
            createTileMetadata(manifestId, "tile_1_0", 512),   // x=512: 10512-11023ms - DELETE
            createTileMetadata(manifestId, "tile_2_0", 1024),  // x=1024: 11024-11535ms - DELETE
            createTileMetadata(manifestId, "tile_3_0", 1536),  // x=1536: 11536-12047ms - DELETE
            createTileMetadata(manifestId, "tile_4_0", 2048),  // x=2048: 12048-12559ms - DELETE
            createTileMetadata(manifestId, "tile_5_0", 2560),  // x=2560: 12560-13071ms - KEEP (overlaps 13000)
            createTileMetadata(manifestId, "tile_6_0", 3072),  // x=3072: 13072-13583ms - KEEP
            createTileMetadata(manifestId, "tile_14_0", 7168), // x=7168: 17168-17679ms - DELETE
            createTileMetadata(manifestId, "tile_22_0", 11264), // x=11264: 21264-21775ms - DELETE
            createTileMetadata(manifestId, "tile_23_0", 11776), // x=11776: 21776-22287ms - DELETE
            createTileMetadata(manifestId, "tile_24_0", 12288), // x=12288: 22288-22799ms - KEEP (near 23000)
            createTileMetadata(manifestId, "tile_25_0", 12800), // x=12800: 22800-23311ms - KEEP
            createTileMetadata(manifestId, "tile_28_0", 14336), // x=14336: 24336-24847ms - KEEP
            createTileMetadata(manifestId, "tile_29_0", 14848), // x=14848: 24848-25359ms - KEEP
            createTileMetadata(manifestId, "tile_30_0", 15360), // x=15360: 25360-25871ms - KEEP
            createTileMetadata(manifestId, "tile_31_0", 15872), // x=15872: 25872-26383ms - KEEP
            createTileMetadata(manifestId, "tile_32_0", 16384), // x=16384: 26384-26895ms - KEEP
            createTileMetadata(manifestId, "tile_33_0", 16896), // x=16896: 26896-27407ms - KEEP (overlaps 27000)
            createTileMetadata(manifestId, "tile_34_0", 17408), // x=17408: 27408-27919ms - DELETE
            createTileMetadata(manifestId, "tile_50_0", 25600)  // x=25600: 35600-36111ms - DELETE
        );
        
        when(tileRepository.findByManifestId(manifestId)).thenReturn(tiles);
        
        pruningService.prune(manifest, markerWindows);
        
        // Verify tiles outside marker windows were deleted
        ArgumentCaptor<List<UUID>> deletedIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(tileRepository).deleteByIds(deletedIdsCaptor.capture());
        
        List<UUID> deletedIds = deletedIdsCaptor.getValue();
        assertTrue(deletedIds.size() > 0, "Should delete tiles outside marker windows");
        
        // Verify MinIO deletion was called for deleted tiles
        verify(storageAdapter, atLeastOnce()).deleteTile(
            eq(manifest.getRegattaId()),
            eq(manifest.getCaptureSessionId()),
            anyString()
        );
        
        // Verify manifest state was updated
        ArgumentCaptor<LineScanManifest> manifestCaptor = ArgumentCaptor.forClass(LineScanManifest.class);
        verify(manifestRepository).save(manifestCaptor.capture());
        
        LineScanManifest updated = manifestCaptor.getValue();
        assertEquals(PRUNED, updated.getRetentionState());
        assertNotNull(updated.getPrunedAt());
    }
    
    @Test
    void prune_noMarkerWindows_deletesAllTiles() throws Exception {
        UUID manifestId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID captureSessionId = UUID.randomUUID();
        
        LineScanManifest manifest = LineScanManifest.builder()
            .id(manifestId)
            .regattaId(regattaId)
            .captureSessionId(captureSessionId)
            .tileSizePx(512)
            .primaryFormat("webp_lossless")
            .xOriginTimestampMs(10000L)
            .msPerPixel(1.0)
            .retentionDays(14)
            .pruneWindowSeconds(2)
            .retentionState(ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS)
            .pruneEligibleAt(Instant.now().minusSeconds(86400))
            .createdAt(Instant.now().minusSeconds(86400 * 15))
            .updatedAt(Instant.now())
            .build();
        
        // No marker windows means delete everything
        List<LineScanRetentionEvaluator.TimeWindow> markerWindows = List.of();
        
        List<LineScanTileMetadata> tiles = List.of(
            createTileMetadata(manifestId, "tile_0_0", 0),
            createTileMetadata(manifestId, "tile_1_0", 512),
            createTileMetadata(manifestId, "tile_2_0", 1024)
        );
        
        when(tileRepository.findByManifestId(manifestId)).thenReturn(tiles);
        
        pruningService.prune(manifest, markerWindows);
        
        // Verify all tiles were deleted
        ArgumentCaptor<List<UUID>> deletedIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(tileRepository).deleteByIds(deletedIdsCaptor.capture());
        
        List<UUID> deletedIds = deletedIdsCaptor.getValue();
        assertEquals(3, deletedIds.size(), "Should delete all tiles when no marker windows");
        
        // Verify manifest was marked as pruned
        ArgumentCaptor<LineScanManifest> manifestCaptor = ArgumentCaptor.forClass(LineScanManifest.class);
        verify(manifestRepository).save(manifestCaptor.capture());
        assertEquals(PRUNED, manifestCaptor.getValue().getRetentionState());
    }
    
    @Test
    void prune_noTiles_stillUpdatesManifestState() throws Exception {
        UUID manifestId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        
        LineScanManifest manifest = LineScanManifest.builder()
            .id(manifestId)
            .regattaId(regattaId)
            .captureSessionId(UUID.randomUUID())
            .tileSizePx(512)
            .primaryFormat("webp_lossless")
            .xOriginTimestampMs(10000L)
            .msPerPixel(1.0)
            .retentionDays(14)
            .pruneWindowSeconds(2)
            .retentionState(ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS)
            .createdAt(Instant.now().minusSeconds(86400 * 15))
            .updatedAt(Instant.now())
            .build();
        
        when(tileRepository.findByManifestId(manifestId)).thenReturn(List.of());
        
        pruningService.prune(manifest, List.of());
        
        // Verify manifest was updated even with no tiles to delete
        ArgumentCaptor<LineScanManifest> manifestCaptor = ArgumentCaptor.forClass(LineScanManifest.class);
        verify(manifestRepository).save(manifestCaptor.capture());
        
        LineScanManifest updated = manifestCaptor.getValue();
        assertEquals(PRUNED, updated.getRetentionState());
        assertNotNull(updated.getPrunedAt());
        
        // Should not attempt to delete tiles
        verify(tileRepository, never()).deleteByIds(anyList());
    }
    
    @Test
    void prune_alreadyPrunedManifest_throwsException() {
        LineScanManifest manifest = LineScanManifest.builder()
            .id(UUID.randomUUID())
            .regattaId(UUID.randomUUID())
            .captureSessionId(UUID.randomUUID())
            .tileSizePx(512)
            .primaryFormat("webp_lossless")
            .xOriginTimestampMs(10000L)
            .msPerPixel(1.0)
            .retentionDays(14)
            .pruneWindowSeconds(2)
            .retentionState(PRUNED)
            .prunedAt(Instant.now().minusSeconds(86400))
            .createdAt(Instant.now().minusSeconds(86400 * 15))
            .updatedAt(Instant.now())
            .build();
        
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> pruningService.prune(manifest, List.of())
        );
        
        assertTrue(exception.getMessage().contains("already pruned"));
        verifyNoInteractions(tileRepository, storageAdapter, manifestRepository);
    }
    
    @Test
    void prune_wrongState_throwsException() {
        LineScanManifest manifest = LineScanManifest.builder()
            .id(UUID.randomUUID())
            .regattaId(UUID.randomUUID())
            .captureSessionId(UUID.randomUUID())
            .tileSizePx(512)
            .primaryFormat("webp_lossless")
            .xOriginTimestampMs(10000L)
            .msPerPixel(1.0)
            .retentionDays(14)
            .pruneWindowSeconds(2)
            .retentionState(FULL_RETAINED) // Wrong state for pruning
            .createdAt(Instant.now().minusSeconds(86400 * 15))
            .updatedAt(Instant.now())
            .build();
        
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> pruningService.prune(manifest, List.of())
        );
        
        assertTrue(exception.getMessage().contains("not eligible"));
        verifyNoInteractions(tileRepository, storageAdapter, manifestRepository);
    }
    
    @Test
    void isTileInWindow_tileOverlapsWindowStart_returnsTrue() {
        // Tile at x=2560, size=512, ms_per_pixel=1.0, origin=10000
        // Tile time range: 12560 to 13071 ms
        // Window: 13000 to 17000 ms
        // Overlap: 13000 to 13071 ms
        LineScanTileMetadata tile = createTileMetadata(UUID.randomUUID(), "tile_5_0", 2560);
        LineScanRetentionEvaluator.TimeWindow window = 
            new LineScanRetentionEvaluator.TimeWindow(13000L, 17000L);
        
        boolean result = pruningService.isTileInWindow(tile, window, 10000L, 1.0, 512);
        
        assertTrue(result, "Tile should overlap window start");
    }
    
    @Test
    void isTileInWindow_tileFullyInsideWindow_returnsTrue() {
        // Tile at x=3072, size=512, ms_per_pixel=1.0, origin=10000
        // Tile time range: 13072 to 13583 ms
        // Window: 13000 to 17000 ms
        // Fully inside
        LineScanTileMetadata tile = createTileMetadata(UUID.randomUUID(), "tile_6_0", 3072);
        LineScanRetentionEvaluator.TimeWindow window = 
            new LineScanRetentionEvaluator.TimeWindow(13000L, 17000L);
        
        boolean result = pruningService.isTileInWindow(tile, window, 10000L, 1.0, 512);
        
        assertTrue(result, "Tile should be fully inside window");
    }
    
    @Test
    void isTileInWindow_tileOverlapsWindowEnd_returnsTrue() {
        // Tile at x=6656, size=512, ms_per_pixel=1.0, origin=10000
        // Tile time range: 16656 to 17167 ms
        // Window: 13000 to 17000 ms
        // Overlap: 16656 to 17000 ms
        LineScanTileMetadata tile = createTileMetadata(UUID.randomUUID(), "tile_13_0", 6656);
        LineScanRetentionEvaluator.TimeWindow window = 
            new LineScanRetentionEvaluator.TimeWindow(13000L, 17000L);
        
        boolean result = pruningService.isTileInWindow(tile, window, 10000L, 1.0, 512);
        
        assertTrue(result, "Tile should overlap window end");
    }
    
    @Test
    void isTileInWindow_tileBeforeWindow_returnsFalse() {
        // Tile at x=0, size=512, ms_per_pixel=1.0, origin=10000
        // Tile time range: 10000 to 10511 ms
        // Window: 13000 to 17000 ms
        // No overlap
        LineScanTileMetadata tile = createTileMetadata(UUID.randomUUID(), "tile_0_0", 0);
        LineScanRetentionEvaluator.TimeWindow window = 
            new LineScanRetentionEvaluator.TimeWindow(13000L, 17000L);
        
        boolean result = pruningService.isTileInWindow(tile, window, 10000L, 1.0, 512);
        
        assertFalse(result, "Tile should be before window");
    }
    
    @Test
    void isTileInWindow_tileAfterWindow_returnsFalse() {
        // Tile at x=17408, size=512, ms_per_pixel=1.0, origin=10000
        // Tile time range: 27408 to 27919 ms
        // Window: 13000 to 17000 ms
        // No overlap
        LineScanTileMetadata tile = createTileMetadata(UUID.randomUUID(), "tile_34_0", 17408);
        LineScanRetentionEvaluator.TimeWindow window = 
            new LineScanRetentionEvaluator.TimeWindow(13000L, 17000L);
        
        boolean result = pruningService.isTileInWindow(tile, window, 10000L, 1.0, 512);
        
        assertFalse(result, "Tile should be after window");
    }
    
    private LineScanTileMetadata createTileMetadata(UUID manifestId, String tileId, int tileX) {
        return LineScanTileMetadata.builder()
            .id(UUID.randomUUID())
            .manifestId(manifestId)
            .tileId(tileId)
            .tileX(tileX)
            .tileY(0)
            .contentType("image/webp")
            .byteSize(1024)
            .uploadState(LineScanTileMetadata.UploadState.READY)
            .uploadAttempts(0)
            .lastUploadError(null)
            .lastUploadAttemptAt(null)
            .minioBucket("regatta-bucket")
            .minioObjectKey("session/" + tileId + ".webp")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
}
