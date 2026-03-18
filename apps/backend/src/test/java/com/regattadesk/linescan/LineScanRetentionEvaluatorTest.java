package com.regattadesk.linescan;

import com.regattadesk.linescan.model.LineScanManifest;
import com.regattadesk.linescan.model.TimingMarker;
import com.regattadesk.linescan.service.LineScanRetentionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.regattadesk.linescan.model.LineScanManifest.RetentionState.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LineScanRetentionEvaluator.
 * 
 * Tests retention state evaluation logic including:
 * - Delay period calculations
 * - Safety gate checks (archive + approval completion)
 * - State transition rules
 * - Alert generation conditions
 */
@ExtendWith(MockitoExtension.class)
class LineScanRetentionEvaluatorTest {
    
    private LineScanRetentionEvaluator evaluator;
    
    @BeforeEach
    void setUp() {
        evaluator = new LineScanRetentionEvaluator();
    }
    
    @Test
    void evaluate_fullRetainedManifestWithinDelay_remainsFullRetained() {
        // Manifest created 7 days ago (within 14-day default delay)
        Instant createdAt = Instant.now().minus(7, ChronoUnit.DAYS);
        LineScanManifest manifest = createManifest(FULL_RETAINED, createdAt, 14, null);
        
        LineScanRetentionEvaluator.EvaluationResult result = evaluator.evaluate(
            manifest,
            createdAt,
            false, // regatta not archived
            false, // not all entries approved
            Instant.now()
        );
        
        assertEquals(FULL_RETAINED, result.getTargetState());
        assertFalse(result.shouldTransitionState());
        assertFalse(result.shouldPrune());
        assertNull(result.getAlertReason());
    }
    
    @Test
    void evaluate_fullRetainedManifestDelayElapsed_transitionsToPendingDelay() {
        // Manifest created 15 days ago (exceeded 14-day delay)
        Instant createdAt = Instant.now().minus(15, ChronoUnit.DAYS);
        Instant expectedEligibleAt = createdAt.plus(14, ChronoUnit.DAYS);
        LineScanManifest manifest = createManifest(FULL_RETAINED, createdAt, 14, null);
        
        LineScanRetentionEvaluator.EvaluationResult result = evaluator.evaluate(
            manifest,
            createdAt,
            false, // regatta not archived
            false, // not all entries approved
            Instant.now()
        );
        
        assertEquals(PENDING_DELAY, result.getTargetState());
        assertTrue(result.shouldTransitionState());
        assertFalse(result.shouldPrune());
        assertNotNull(result.getPruneEligibleAt());
        assertEquals(expectedEligibleAt.getEpochSecond(), result.getPruneEligibleAt().getEpochSecond());
        assertNull(result.getAlertReason());
    }
    
    @Test
    void evaluate_pendingDelayWithArchiveComplete_transitionsToEligible() {
        Instant createdAt = Instant.now().minus(15, ChronoUnit.DAYS);
        Instant pruneEligibleAt = createdAt.plus(14, ChronoUnit.DAYS);
        LineScanManifest manifest = createManifest(PENDING_DELAY, createdAt, 14, pruneEligibleAt);
        
        LineScanRetentionEvaluator.EvaluationResult result = evaluator.evaluate(
            manifest,
            createdAt,
            true,  // regatta archived
            false, // not all entries approved (but archive is enough)
            Instant.now()
        );
        
        assertEquals(ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS, result.getTargetState());
        assertTrue(result.shouldTransitionState());
        assertFalse(result.shouldPrune());
        assertNull(result.getAlertReason());
    }
    
    @Test
    void evaluate_pendingDelayWithAllApprovalsComplete_transitionsToEligible() {
        Instant createdAt = Instant.now().minus(15, ChronoUnit.DAYS);
        Instant pruneEligibleAt = createdAt.plus(14, ChronoUnit.DAYS);
        LineScanManifest manifest = createManifest(PENDING_DELAY, createdAt, 14, pruneEligibleAt);
        
        LineScanRetentionEvaluator.EvaluationResult result = evaluator.evaluate(
            manifest,
            createdAt,
            false, // regatta not archived (but approvals are enough)
            true,  // all entries approved
            Instant.now()
        );
        
        assertEquals(ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS, result.getTargetState());
        assertTrue(result.shouldTransitionState());
        assertFalse(result.shouldPrune());
        assertNull(result.getAlertReason());
    }
    
    @Test
    void evaluate_pendingDelayWithBothGatesSatisfied_transitionsToEligible() {
        Instant createdAt = Instant.now().minus(15, ChronoUnit.DAYS);
        Instant pruneEligibleAt = createdAt.plus(14, ChronoUnit.DAYS);
        LineScanManifest manifest = createManifest(PENDING_DELAY, createdAt, 14, pruneEligibleAt);
        
        LineScanRetentionEvaluator.EvaluationResult result = evaluator.evaluate(
            manifest,
            createdAt,
            true, // regatta archived
            true, // all entries approved
            Instant.now()
        );
        
        assertEquals(ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS, result.getTargetState());
        assertTrue(result.shouldTransitionState());
        assertFalse(result.shouldPrune());
        assertNull(result.getAlertReason());
    }
    
    @Test
    void evaluate_pendingDelayWithNoGatesSatisfied_emitsAlert() {
        Instant createdAt = Instant.now().minus(15, ChronoUnit.DAYS);
        Instant pruneEligibleAt = createdAt.plus(14, ChronoUnit.DAYS);
        LineScanManifest manifest = createManifest(PENDING_DELAY, createdAt, 14, pruneEligibleAt);
        
        LineScanRetentionEvaluator.EvaluationResult result = evaluator.evaluate(
            manifest,
            createdAt,
            false, // regatta not archived
            false, // not all entries approved
            Instant.now()
        );
        
        assertEquals(PENDING_DELAY, result.getTargetState());
        assertFalse(result.shouldTransitionState());
        assertFalse(result.shouldPrune());
        assertNotNull(result.getAlertReason());
        assertTrue(result.getAlertReason().contains("delay elapsed"));
    }
    
    @Test
    void evaluate_eligibleWaitingState_readyToPrune() {
        Instant createdAt = Instant.now().minus(20, ChronoUnit.DAYS);
        Instant pruneEligibleAt = createdAt.plus(14, ChronoUnit.DAYS);
        LineScanManifest manifest = createManifest(
            ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS, 
            createdAt, 
            14, 
            pruneEligibleAt
        );
        
        LineScanRetentionEvaluator.EvaluationResult result = evaluator.evaluate(
            manifest,
            createdAt,
            true, // regatta archived
            true, // all entries approved
            Instant.now()
        );
        
        assertEquals(ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS, result.getTargetState());
        assertFalse(result.shouldTransitionState());
        assertTrue(result.shouldPrune());
        assertNull(result.getAlertReason());
    }
    
    @Test
    void evaluate_prunedManifest_noFurtherAction() {
        Instant createdAt = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant pruneEligibleAt = createdAt.plus(14, ChronoUnit.DAYS);
        LineScanManifest manifest = LineScanManifest.builder()
            .id(UUID.randomUUID())
            .regattaId(UUID.randomUUID())
            .captureSessionId(UUID.randomUUID())
            .tileSizePx(512)
            .primaryFormat("webp_lossless")
            .xOriginTimestampMs(1000L)
            .msPerPixel(0.5)
            .retentionDays(14)
            .pruneWindowSeconds(2)
            .retentionState(PRUNED)
            .pruneEligibleAt(pruneEligibleAt)
            .prunedAt(Instant.now().minus(5, ChronoUnit.DAYS))
            .createdAt(createdAt)
            .updatedAt(Instant.now())
            .build();
        
        LineScanRetentionEvaluator.EvaluationResult result = evaluator.evaluate(
            manifest,
            createdAt,
            true,
            true,
            Instant.now()
        );
        
        assertEquals(PRUNED, result.getTargetState());
        assertFalse(result.shouldTransitionState());
        assertFalse(result.shouldPrune());
        assertNull(result.getAlertReason());
    }
    
    @Test
    void evaluate_customRetentionPeriod_respectsCustomDelay() {
        // Manifest created 8 days ago with 7-day retention period
        Instant createdAt = Instant.now().minus(8, ChronoUnit.DAYS);
        LineScanManifest manifest = createManifest(FULL_RETAINED, createdAt, 7, null);
        
        LineScanRetentionEvaluator.EvaluationResult result = evaluator.evaluate(
            manifest,
            createdAt,
            false,
            false,
            Instant.now()
        );
        
        // Should transition because 8 days > 7 days
        assertEquals(PENDING_DELAY, result.getTargetState());
        assertTrue(result.shouldTransitionState());
        assertFalse(result.shouldPrune());
    }
    
    @Test
    void evaluate_exactlyAtDelayBoundary_transitionsToPendingDelay() {
        // Test boundary condition: exactly at retention period
        Instant now = Instant.now();
        Instant createdAt = now.minus(14, ChronoUnit.DAYS);
        LineScanManifest manifest = createManifest(FULL_RETAINED, createdAt, 14, null);
        
        LineScanRetentionEvaluator.EvaluationResult result = evaluator.evaluate(
            manifest,
            createdAt,
            false,
            false,
            now
        );
        
        // At exact boundary, should transition to pending
        assertEquals(PENDING_DELAY, result.getTargetState());
        assertTrue(result.shouldTransitionState());
    }
    
    @Test
    void getMarkerWindows_calculatesCorrectTimeRanges() {
        List<TimingMarker> markers = List.of(
            createMarker(10000L), // 10 seconds
            createMarker(25000L), // 25 seconds
            createMarker(40000L)  // 40 seconds
        );
        
        int pruneWindowSeconds = 2;
        
        List<LineScanRetentionEvaluator.TimeWindow> windows = 
            evaluator.getMarkerWindows(markers, pruneWindowSeconds);
        
        assertEquals(3, windows.size());
        
        // First marker: 10000ms ± 2000ms = [8000, 12000]
        assertEquals(8000L, windows.get(0).startMs());
        assertEquals(12000L, windows.get(0).endMs());
        
        // Second marker: 25000ms ± 2000ms = [23000, 27000]
        assertEquals(23000L, windows.get(1).startMs());
        assertEquals(27000L, windows.get(1).endMs());
        
        // Third marker: 40000ms ± 2000ms = [38000, 42000]
        assertEquals(38000L, windows.get(2).startMs());
        assertEquals(42000L, windows.get(2).endMs());
    }

    @Test
    void evaluate_missingRegattaEndAt_keepsFullRetained() {
        Instant createdAt = Instant.now().minus(30, ChronoUnit.DAYS);
        LineScanManifest manifest = createManifest(FULL_RETAINED, createdAt, 14, null);

        LineScanRetentionEvaluator.EvaluationResult result = evaluator.evaluate(
            manifest,
            null,
            false,
            false,
            Instant.now()
        );

        assertEquals(FULL_RETAINED, result.getTargetState());
        assertFalse(result.shouldTransitionState());
        assertFalse(result.shouldPrune());
        assertNull(result.getAlertReason());
    }
    
    @Test
    void getMarkerWindows_emptyMarkerList_returnsEmptyWindows() {
        List<TimingMarker> markers = List.of();
        
        List<LineScanRetentionEvaluator.TimeWindow> windows = 
            evaluator.getMarkerWindows(markers, 2);
        
        assertTrue(windows.isEmpty());
    }
    
    @Test
    void getMarkerWindows_singleMarker_returnsSingleWindow() {
        List<TimingMarker> markers = List.of(createMarker(15000L));
        
        List<LineScanRetentionEvaluator.TimeWindow> windows = 
            evaluator.getMarkerWindows(markers, 3); // ±3 seconds
        
        assertEquals(1, windows.size());
        assertEquals(12000L, windows.get(0).startMs()); // 15000 - 3000
        assertEquals(18000L, windows.get(0).endMs());   // 15000 + 3000
    }
    
    private LineScanManifest createManifest(
        LineScanManifest.RetentionState state, 
        Instant createdAt,
        int retentionDays,
        Instant pruneEligibleAt
    ) {
        return LineScanManifest.builder()
            .id(UUID.randomUUID())
            .regattaId(UUID.randomUUID())
            .captureSessionId(UUID.randomUUID())
            .tileSizePx(512)
            .primaryFormat("webp_lossless")
            .xOriginTimestampMs(1000L)
            .msPerPixel(0.5)
            .retentionDays(retentionDays)
            .pruneWindowSeconds(2)
            .retentionState(state)
            .pruneEligibleAt(pruneEligibleAt)
            .createdAt(createdAt)
            .updatedAt(Instant.now())
            .build();
    }
    
    private TimingMarker createMarker(long timestampMs) {
        return new TimingMarker(
            UUID.randomUUID(),
            UUID.randomUUID(),
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
