package com.regattadesk.performance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadTestingSuiteDefinitionTest {

    @Test
    void definesRequiredTrafficProfiles() {
        LoadScenarioCatalog catalog = LoadScenarioCatalog.fromDefaultConfig();

        List<String> profileIds = catalog.scenarios().stream().map(LoadScenario::id).toList();
        assertTrue(profileIds.contains("public-read"));
        assertTrue(profileIds.contains("public-sse"));
        assertTrue(profileIds.contains("operator-ingest-burst"));
        assertTrue(profileIds.contains("staff-api"));
    }

    @Test
    void declaresObjectiveThresholdsForReleaseGate() {
        PerformanceThresholds thresholds = PerformanceThresholds.fromDefaultConfig();

        assertTrue(thresholds.maxP95LatencyMs() > 0);
        assertTrue(thresholds.maxErrorRatePct() > 0);
        assertTrue(thresholds.maxCpuUtilizationPct() > 0);
        assertTrue(thresholds.maxMemoryUtilizationPct() > 0);
    }

    @Test
    void flagsBottlenecksAndRegressionRiskInGateReport() {
        PerformanceGateEvaluator evaluator = new PerformanceGateEvaluator(PerformanceThresholds.fromDefaultConfig());
        PerformanceSample sample = new PerformanceSample(
            650,
            1.9,
            88.0,
            84.0,
            Map.of("public-sse", 710.0, "staff-api", 603.0)
        );

        PerformanceGateResult result = evaluator.evaluate(sample);

        assertEquals(PerformanceGateStatus.FAIL, result.status());
        assertTrue(result.bottlenecks().contains("public-sse"));
        assertTrue(result.regressionRiskSummary().contains("p95"));
    }
}
