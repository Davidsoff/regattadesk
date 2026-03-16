package com.regattadesk.linescan.service;

import java.util.LongSummaryStatistics;
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

    public static final String STATUS_INCOMPLETE = "incomplete";
    public static final String STATUS_PENDING_APPROVAL = "pending_approval";
    public static final String STATUS_COMPLETED = "completed";

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

        LongSummaryStatistics summary = approved.stream()
            .mapToLong(MarkerEvidence::timestampMs)
            .summaryStatistics();

        return CompletionResult.completed(summary.getMin(), summary.getMax());
    }

    public record MarkerEvidence(long timestampMs, boolean isApproved) {
    }

    public record CompletionResult(String completionStatus, Long markerStartTimeMs, Long markerFinishTimeMs) {
        private static CompletionResult incomplete() {
            return new CompletionResult(STATUS_INCOMPLETE, null, null);
        }

        private static CompletionResult pendingApproval() {
            return new CompletionResult(STATUS_PENDING_APPROVAL, null, null);
        }

        private static CompletionResult completed(long start, long finish) {
            return new CompletionResult(STATUS_COMPLETED, start, finish);
        }
    }
}
