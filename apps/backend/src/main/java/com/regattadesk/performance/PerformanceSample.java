package com.regattadesk.performance;

import java.util.Map;

public record PerformanceSample(
    double p95LatencyMs,
    double errorRatePct,
    double cpuUtilizationPct,
    double memoryUtilizationPct,
    Map<String, Double> scenarioP95LatencyMs
) {
}
