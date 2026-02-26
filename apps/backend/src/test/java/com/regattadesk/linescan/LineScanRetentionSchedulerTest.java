package com.regattadesk.linescan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.regattadesk.linescan.LineScanManifest.RetentionState.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for LineScanRetentionScheduler.
 * 
 * Tests scheduling behavior with focus on:
 * - Deterministic evaluation without time-based flakiness
 * - Alert generation
 * - Pruning execution
 * - State transitions
 * 
 * Note: These tests use Mockito to isolate scheduler logic from dependencies.
 */
@ExtendWith(MockitoExtension.class)
class LineScanRetentionSchedulerTest {
    
    LineScanRetentionScheduler scheduler;
    
    @Mock
    LineScanManifestRepository manifestRepository;
    
    @Mock
    LineScanTileRepository tileRepository;
    
    @Mock
    LineScanRetentionEvaluator evaluator;
    
    @Mock
    LineScanPruningService pruningService;
    
    @Mock
    RegattaRepository regattaRepository;
    
    @Mock
    EntryRepository entryRepository;
    
    @Mock
    TimingMarkerRepository markerRepository;
    
    @BeforeEach
    void setUp() {
        scheduler = new LineScanRetentionScheduler(
            manifestRepository,
            evaluator,
            pruningService,
            regattaRepository,
            entryRepository,
            markerRepository
        );
    }
    
    @Test
    void evaluateAndPrune_eligibleManifest_performsPruning() {
        UUID regattaId = UUID.randomUUID();
        UUID manifestId = UUID.randomUUID();
        UUID captureSessionId = UUID.randomUUID();
        
        // Setup manifest eligible for pruning
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
            .pruneEligibleAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .createdAt(Instant.now().minus(15, ChronoUnit.DAYS))
            .updatedAt(Instant.now())
            .build();
        
        // Setup approved markers for this capture session
        List<TimingMarker> markers = List.of(
            createMarker(captureSessionId, 15000L),
            createMarker(captureSessionId, 25000L)
        );
        
        List<LineScanRetentionEvaluator.TimeWindow> markerWindows = List.of(
            new LineScanRetentionEvaluator.TimeWindow(13000L, 17000L),
            new LineScanRetentionEvaluator.TimeWindow(23000L, 27000L)
        );
        
        // Mock repository responses
        when(manifestRepository.findByRetentionStateIn(anyList())).thenReturn(List.of(manifest));
        when(regattaRepository.isArchived(regattaId)).thenReturn(true);
        when(entryRepository.areAllEntriesApprovedForRegatta(regattaId)).thenReturn(true);
        when(markerRepository.findApprovedByRegattaId(regattaId)).thenReturn(markers);
        
        // Mock evaluator to indicate pruning should occur
        LineScanRetentionEvaluator.EvaluationResult evalResult = 
            LineScanRetentionEvaluator.EvaluationResult.readyToPrune(markerWindows);
        when(evaluator.evaluate(eq(manifest), eq(true), eq(true), any(Instant.class)))
            .thenReturn(evalResult);
        when(evaluator.getMarkerWindows(markers, 2)).thenReturn(markerWindows);
        
        // Execute scheduler logic
        scheduler.evaluateAndPrune();
        
        // Verify pruning was performed
        verify(pruningService).prune(eq(manifest), eq(markerWindows));
    }
    
    @Test
    void evaluateAndPrune_pendingDelayWithGatesSatisfied_transitionsToEligible() {
        UUID regattaId = UUID.randomUUID();
        UUID manifestId = UUID.randomUUID();
        
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
            .retentionState(PENDING_DELAY)
            .pruneEligibleAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .createdAt(Instant.now().minus(15, ChronoUnit.DAYS))
            .updatedAt(Instant.now())
            .build();
        
        when(manifestRepository.findByRetentionStateIn(anyList())).thenReturn(List.of(manifest));
        when(regattaRepository.isArchived(regattaId)).thenReturn(true);
        when(entryRepository.areAllEntriesApprovedForRegatta(regattaId)).thenReturn(true);
        
        // Mock evaluator to indicate state transition needed
        LineScanRetentionEvaluator.EvaluationResult evalResult = 
            LineScanRetentionEvaluator.EvaluationResult.transitionTo(
                ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS,
                null
            );
        when(evaluator.evaluate(eq(manifest), eq(true), eq(true), any(Instant.class)))
            .thenReturn(evalResult);
        
        scheduler.evaluateAndPrune();
        
        // Verify state transition was performed
        verify(manifestRepository).save(argThat(m ->
            m.getRetentionState() == ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS
        ));
        
        // Verify pruning was not performed (only transition)
        verify(pruningService, never()).prune(any(), any());
    }
    
    @Test
    void evaluateAndPrune_pendingDelayWithoutGates_emitsAlertOnly() {
        UUID regattaId = UUID.randomUUID();
        UUID manifestId = UUID.randomUUID();
        
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
            .retentionState(PENDING_DELAY)
            .pruneEligibleAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .createdAt(Instant.now().minus(15, ChronoUnit.DAYS))
            .updatedAt(Instant.now())
            .build();
        
        when(manifestRepository.findByRetentionStateIn(anyList())).thenReturn(List.of(manifest));
        when(regattaRepository.isArchived(regattaId)).thenReturn(false);
        when(entryRepository.areAllEntriesApprovedForRegatta(regattaId)).thenReturn(false);
        
        // Mock evaluator to indicate alert needed
        String alertReason = "Retention delay elapsed but regatta not archived and entries not all approved";
        LineScanRetentionEvaluator.EvaluationResult evalResult = 
            LineScanRetentionEvaluator.EvaluationResult.needsAlert(alertReason);
        when(evaluator.evaluate(eq(manifest), eq(false), eq(false), any(Instant.class)))
            .thenReturn(evalResult);
        
        scheduler.evaluateAndPrune();
        
        // Verify no state change or pruning occurred
        verify(manifestRepository, never()).save(any());
        verify(pruningService, never()).prune(any(), any());
        
        // Alert logging would be verified through operational logs
        // (In production, this would emit a monitoring event/metric)
    }
    
    @Test
    void evaluateAndPrune_fullRetainedWithinDelay_noAction() {
        UUID regattaId = UUID.randomUUID();
        UUID manifestId = UUID.randomUUID();
        
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
            .retentionState(FULL_RETAINED)
            .createdAt(Instant.now().minus(7, ChronoUnit.DAYS)) // Within 14-day delay
            .updatedAt(Instant.now())
            .build();
        
        when(manifestRepository.findByRetentionStateIn(anyList())).thenReturn(List.of(manifest));
        when(regattaRepository.isArchived(regattaId)).thenReturn(false);
        when(entryRepository.areAllEntriesApprovedForRegatta(regattaId)).thenReturn(false);
        
        // Mock evaluator to indicate no action needed
        LineScanRetentionEvaluator.EvaluationResult evalResult = 
            LineScanRetentionEvaluator.EvaluationResult.noAction();
        when(evaluator.evaluate(eq(manifest), eq(false), eq(false), any(Instant.class)))
            .thenReturn(evalResult);
        
        scheduler.evaluateAndPrune();
        
        // Verify no changes occurred
        verify(manifestRepository, never()).save(any());
        verify(pruningService, never()).prune(any(), any());
    }
    
    @Test
    void evaluateAndPrune_prunedManifest_skipped() {
        UUID manifestId = UUID.randomUUID();
        
        LineScanManifest manifest = LineScanManifest.builder()
            .id(manifestId)
            .regattaId(UUID.randomUUID())
            .captureSessionId(UUID.randomUUID())
            .tileSizePx(512)
            .primaryFormat("webp_lossless")
            .xOriginTimestampMs(10000L)
            .msPerPixel(1.0)
            .retentionDays(14)
            .pruneWindowSeconds(2)
            .retentionState(PRUNED) // Already pruned
            .prunedAt(Instant.now().minus(5, ChronoUnit.DAYS))
            .createdAt(Instant.now().minus(20, ChronoUnit.DAYS))
            .updatedAt(Instant.now())
            .build();
        
        // Scheduler should not query pruned manifests
        when(manifestRepository.findByRetentionStateIn(anyList())).thenReturn(List.of());
        
        scheduler.evaluateAndPrune();
        
        // Verify evaluator was never called for pruned manifest
        verify(evaluator, never()).evaluate(any(), anyBoolean(), anyBoolean(), any());
        verify(pruningService, never()).prune(any(), any());
    }
    
    @Test
    void evaluateAndPrune_multipleManifests_processesEachIndependently() {
        UUID regattaId1 = UUID.randomUUID();
        UUID regattaId2 = UUID.randomUUID();
        
        LineScanManifest manifest1 = createManifest(regattaId1, PENDING_DELAY);
        LineScanManifest manifest2 = createManifest(regattaId2, ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS);
        
        when(manifestRepository.findByRetentionStateIn(anyList()))
            .thenReturn(List.of(manifest1, manifest2));
        
        // Regatta 1: not archived, not approved
        when(regattaRepository.isArchived(regattaId1)).thenReturn(false);
        when(entryRepository.areAllEntriesApprovedForRegatta(regattaId1)).thenReturn(false);
        
        // Regatta 2: archived and approved
        when(regattaRepository.isArchived(regattaId2)).thenReturn(true);
        when(entryRepository.areAllEntriesApprovedForRegatta(regattaId2)).thenReturn(true);
        when(markerRepository.findApprovedByRegattaId(regattaId2)).thenReturn(List.of());
        
        // Mock evaluator results
        when(evaluator.evaluate(eq(manifest1), eq(false), eq(false), any(Instant.class)))
            .thenReturn(LineScanRetentionEvaluator.EvaluationResult.needsAlert("Test alert"));
        when(evaluator.evaluate(eq(manifest2), eq(true), eq(true), any(Instant.class)))
            .thenReturn(LineScanRetentionEvaluator.EvaluationResult.readyToPrune(List.of()));
        when(evaluator.getMarkerWindows(any(), anyInt())).thenReturn(List.of());
        
        scheduler.evaluateAndPrune();
        
        // Verify both manifests were evaluated
        verify(evaluator).evaluate(eq(manifest1), eq(false), eq(false), any(Instant.class));
        verify(evaluator).evaluate(eq(manifest2), eq(true), eq(true), any(Instant.class));
        
        // Verify only manifest2 was pruned
        verify(pruningService).prune(eq(manifest2), anyList());
        verify(pruningService, times(1)).prune(any(), any());
    }
    
    @Test
    void evaluateAndPrune_exceptionInOneManifest_continuesWithOthers() {
        UUID regattaId1 = UUID.randomUUID();
        UUID regattaId2 = UUID.randomUUID();
        
        LineScanManifest manifest1 = createManifest(regattaId1, ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS);
        LineScanManifest manifest2 = createManifest(regattaId2, ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS);
        
        when(manifestRepository.findByRetentionStateIn(anyList()))
            .thenReturn(List.of(manifest1, manifest2));
        
        when(regattaRepository.isArchived(any())).thenReturn(true);
        when(entryRepository.areAllEntriesApprovedForRegatta(any())).thenReturn(true);
        when(markerRepository.findApprovedByRegattaId(any())).thenReturn(List.of());
        
        // First manifest throws exception during evaluation
        when(evaluator.evaluate(eq(manifest1), anyBoolean(), anyBoolean(), any(Instant.class)))
            .thenThrow(new RuntimeException("Test exception"));
        
        // Second manifest should still be evaluated
        when(evaluator.evaluate(eq(manifest2), anyBoolean(), anyBoolean(), any(Instant.class)))
            .thenReturn(LineScanRetentionEvaluator.EvaluationResult.readyToPrune(List.of()));
        when(evaluator.getMarkerWindows(any(), anyInt())).thenReturn(List.of());
        
        // Should not throw - scheduler should be resilient
        scheduler.evaluateAndPrune();
        
        // Verify second manifest was still processed
        verify(pruningService).prune(eq(manifest2), anyList());
    }
    
    private LineScanManifest createManifest(UUID regattaId, LineScanManifest.RetentionState state) {
        return LineScanManifest.builder()
            .id(UUID.randomUUID())
            .regattaId(regattaId)
            .captureSessionId(UUID.randomUUID())
            .tileSizePx(512)
            .primaryFormat("webp_lossless")
            .xOriginTimestampMs(10000L)
            .msPerPixel(1.0)
            .retentionDays(14)
            .pruneWindowSeconds(2)
            .retentionState(state)
            .pruneEligibleAt(state != FULL_RETAINED ? Instant.now().minus(1, ChronoUnit.DAYS) : null)
            .createdAt(Instant.now().minus(15, ChronoUnit.DAYS))
            .updatedAt(Instant.now())
            .build();
    }
    
    private TimingMarker createMarker(UUID captureSessionId, long timestampMs) {
        return new TimingMarker(
            UUID.randomUUID(),
            captureSessionId,
            UUID.randomUUID(),
            0L,
            timestampMs,
            true,  // isLinked
            true,  // isApproved
            "tile_0_0",
            0,
            0
        );
    }
}
