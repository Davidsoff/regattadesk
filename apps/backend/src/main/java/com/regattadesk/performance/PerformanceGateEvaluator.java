package com.regattadesk.performance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PerformanceGateEvaluator {
    private final PerformanceThresholds thresholds;

    public PerformanceGateEvaluator(PerformanceThresholds thresholds) {
        this.thresholds = thresholds;
    }

    public PerformanceGateResult evaluate(PerformanceSample sample) {
        List<String> breaches = new ArrayList<>();
        List<String> bottlenecks = new ArrayList<>();

        if (sample.p95LatencyMs() > thresholds.maxP95LatencyMs()) {
            breaches.add("p95 latency");
        }
        if (sample.errorRatePct() > thresholds.maxErrorRatePct()) {
            breaches.add("error rate");
        }
        if (sample.cpuUtilizationPct() > thresholds.maxCpuUtilizationPct()) {
            breaches.add("cpu utilization");
        }
        if (sample.memoryUtilizationPct() > thresholds.maxMemoryUtilizationPct()) {
            breaches.add("memory utilization");
        }

        for (Map.Entry<String, Double> entry : sample.scenarioP95LatencyMs().entrySet()) {
            if (entry.getValue() > thresholds.maxP95LatencyMs()) {
                bottlenecks.add(entry.getKey());
            }
        }

        PerformanceGateStatus status = breaches.isEmpty() ? PerformanceGateStatus.PASS : PerformanceGateStatus.FAIL;
        String summary = breaches.isEmpty()
            ? "No regression risk detected."
            : "Regression risk due to threshold breach in: " + String.join(", ", breaches) + ".";

        return new PerformanceGateResult(status, List.copyOf(bottlenecks), summary);
    }
}
