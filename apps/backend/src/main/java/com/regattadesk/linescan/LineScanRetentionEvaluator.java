package com.regattadesk.linescan;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Evaluates retention state for line-scan manifests.
 * 
 * Determines when manifests are eligible for pruning based on:
 * - Retention delay period (default 14 days)
 * - Safety gates: regatta archived OR all entries approved
 * - Current manifest retention state
 * 
 * Implements BC06-006 retention policy evaluation logic.
 */
@ApplicationScoped
public class LineScanRetentionEvaluator {
    
    /**
     * Time window for marker preservation (start and end in milliseconds).
     */
    public record TimeWindow(long startMs, long endMs) {}
    
    /**
     * Result of evaluating a manifest's retention state.
     */
    public static class EvaluationResult {
        private final LineScanManifest.RetentionState targetState;
        private final boolean shouldTransitionState;
        private final boolean shouldPrune;
        private final String alertReason;
        private final Instant pruneEligibleAt;
        private final List<TimeWindow> markerWindows;
        
        private EvaluationResult(
            LineScanManifest.RetentionState targetState,
            boolean shouldTransitionState,
            boolean shouldPrune,
            String alertReason,
            Instant pruneEligibleAt,
            List<TimeWindow> markerWindows
        ) {
            this.targetState = targetState;
            this.shouldTransitionState = shouldTransitionState;
            this.shouldPrune = shouldPrune;
            this.alertReason = alertReason;
            this.pruneEligibleAt = pruneEligibleAt;
            this.markerWindows = markerWindows;
        }
        
        public LineScanManifest.RetentionState getTargetState() {
            return targetState;
        }
        
        public boolean shouldTransitionState() {
            return shouldTransitionState;
        }
        
        public boolean shouldPrune() {
            return shouldPrune;
        }
        
        public String getAlertReason() {
            return alertReason;
        }
        
        public Instant getPruneEligibleAt() {
            return pruneEligibleAt;
        }
        
        public List<TimeWindow> getMarkerWindows() {
            return markerWindows;
        }
        
        public static EvaluationResult noAction() {
            return new EvaluationResult(null, false, false, null, null, null);
        }
        
        public static EvaluationResult transitionTo(
            LineScanManifest.RetentionState targetState,
            Instant pruneEligibleAt
        ) {
            return new EvaluationResult(targetState, true, false, null, pruneEligibleAt, null);
        }
        
        public static EvaluationResult needsAlert(String alertReason) {
            return new EvaluationResult(null, false, false, alertReason, null, null);
        }
        
        public static EvaluationResult readyToPrune(List<TimeWindow> markerWindows) {
            return new EvaluationResult(null, false, true, null, null, markerWindows);
        }
    }
    
    /**
     * Evaluates a manifest to determine retention actions.
     * 
     * @param manifest The manifest to evaluate
     * @param regattaArchived Whether the regatta is archived
     * @param allEntriesApproved Whether all entries are approved
     * @param now Current time for deterministic evaluation
     * @return Evaluation result indicating required actions
     */
    public EvaluationResult evaluate(
        LineScanManifest manifest,
        boolean regattaArchived,
        boolean allEntriesApproved,
        Instant now
    ) {
        LineScanManifest.RetentionState currentState = manifest.getRetentionState();
        
        // Already pruned - no action needed
        if (currentState == LineScanManifest.RetentionState.PRUNED) {
            return new EvaluationResult(
                LineScanManifest.RetentionState.PRUNED,
                false,
                false,
                null,
                null,
                null
            );
        }
        
        // Check if delay period has elapsed
        Instant pruneEligibleAt = manifest.getCreatedAt().plus(manifest.getRetentionDays(), ChronoUnit.DAYS);
        boolean delayElapsed = !now.isBefore(pruneEligibleAt);
        
        // Check safety gates
        boolean gatesSatisfied = regattaArchived || allEntriesApproved;
        
        // State machine transitions
        switch (currentState) {
            case FULL_RETAINED:
                if (delayElapsed) {
                    // Transition to PENDING_DELAY
                    return EvaluationResult.transitionTo(
                        LineScanManifest.RetentionState.PENDING_DELAY,
                        pruneEligibleAt
                    );
                }
                // Still within delay period
                return new EvaluationResult(
                    LineScanManifest.RetentionState.FULL_RETAINED,
                    false,
                    false,
                    null,
                    null,
                    null
                );
                
            case PENDING_DELAY:
                if (gatesSatisfied) {
                    // Gates satisfied - transition to ELIGIBLE
                    return EvaluationResult.transitionTo(
                        LineScanManifest.RetentionState.ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS,
                        null
                    );
                }
                // Delay elapsed but gates not satisfied - emit alert
                return new EvaluationResult(
                    LineScanManifest.RetentionState.PENDING_DELAY,
                    false,
                    false,
                    "Retention delay elapsed but regatta not archived and entries not all approved",
                    null,
                    null
                );
                
            case ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS:
                if (gatesSatisfied) {
                    // Ready to prune - return readyToPrune with no marker windows
                    // (Scheduler will calculate marker windows before pruning)
                    return new EvaluationResult(
                        LineScanManifest.RetentionState.ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS,
                        false,
                        true,
                        null,
                        null,
                        null
                    );
                }
                // Lost gate satisfaction (edge case - should not happen in normal flow)
                return new EvaluationResult(
                    LineScanManifest.RetentionState.ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS,
                    false,
                    false,
                    null,
                    null,
                    null
                );
                
            default:
                throw new IllegalStateException("Unknown retention state: " + currentState);
        }
    }
    
    /**
     * Calculates time windows around approved markers for preservation.
     * 
     * @param markers List of approved, linked markers
     * @param pruneWindowSeconds Window size in seconds (e.g., 2 for ±2s)
     * @return List of time windows to preserve
     */
    public List<TimeWindow> getMarkerWindows(List<TimingMarker> markers, int pruneWindowSeconds) {
        long windowMs = pruneWindowSeconds * 1000L;
        
        return markers.stream()
            .filter(TimingMarker::isApproved)
            .filter(TimingMarker::isLinked)
            .map(marker -> new TimeWindow(
                marker.timestampMs() - windowMs,
                marker.timestampMs() + windowMs
            ))
            .collect(Collectors.toList());
    }
}
