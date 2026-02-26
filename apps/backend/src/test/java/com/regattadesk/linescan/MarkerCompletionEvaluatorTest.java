package com.regattadesk.linescan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MarkerCompletionEvaluatorTest {

    @Test
    void evaluate_withLessThanTwoLinkedMarkers_isIncomplete() {
        var result = MarkerCompletionEvaluator.evaluate(List.of(
            new MarkerCompletionEvaluator.MarkerEvidence(1_000L, true)
        ));

        assertEquals("incomplete", result.completionStatus());
        assertNull(result.markerStartTimeMs());
        assertNull(result.markerFinishTimeMs());
    }

    @Test
    void evaluate_withTwoLinkedButUnapprovedMarkers_isPendingApproval() {
        var result = MarkerCompletionEvaluator.evaluate(List.of(
            new MarkerCompletionEvaluator.MarkerEvidence(1_000L, false),
            new MarkerCompletionEvaluator.MarkerEvidence(1_200L, false)
        ));

        assertEquals("pending_approval", result.completionStatus());
        assertNull(result.markerStartTimeMs());
        assertNull(result.markerFinishTimeMs());
    }

    @Test
    void evaluate_withTwoApprovedMarkers_isCompleted() {
        var result = MarkerCompletionEvaluator.evaluate(List.of(
            new MarkerCompletionEvaluator.MarkerEvidence(1_400L, true),
            new MarkerCompletionEvaluator.MarkerEvidence(1_100L, true),
            new MarkerCompletionEvaluator.MarkerEvidence(1_200L, false)
        ));

        assertEquals("completed", result.completionStatus());
        assertEquals(1_100L, result.markerStartTimeMs());
        assertEquals(1_400L, result.markerFinishTimeMs());
    }
}
