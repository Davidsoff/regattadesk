package com.regattadesk.linescan;

import java.util.List;

/**
 * Completion rule for marker-derived entry outcomes.
 *
 * Rules:
 * - Fewer than 2 linked markers -> incomplete.
 * - At least 2 linked markers but fewer than 2 approved markers -> pending_approval.
 * - At least 2 approved linked markers -> completed, start/finish derived from approved marker timestamps.
 */
public final class MarkerCompletionEvaluator {

    private MarkerCompletionEvaluator() {
    }

    public static CompletionResult evaluate(List<MarkerEvidence> linkedMarkers) {
        if (linkedMarkers == null || linkedMarkers.size() < 2) {
            return CompletionResult.incomplete();
        }

        List<MarkerEvidence> approved = linkedMarkers.stream()
            .filter(MarkerEvidence::isApproved)
            .toList();

        if (approved.size() < 2) {
            return CompletionResult.pendingApproval();
        }

        long start = approved.stream().mapToLong(MarkerEvidence::timestampMs).min().orElseThrow();
        long finish = approved.stream().mapToLong(MarkerEvidence::timestampMs).max().orElseThrow();

        return CompletionResult.completed(start, finish);
    }

    public record MarkerEvidence(long timestampMs, boolean isApproved) {
    }

    public record CompletionResult(String completionStatus, Long markerStartTimeMs, Long markerFinishTimeMs) {
        private static CompletionResult incomplete() {
            return new CompletionResult("incomplete", null, null);
        }

        private static CompletionResult pendingApproval() {
            return new CompletionResult("pending_approval", null, null);
        }

        private static CompletionResult completed(long start, long finish) {
            return new CompletionResult("completed", start, finish);
        }
    }
}
