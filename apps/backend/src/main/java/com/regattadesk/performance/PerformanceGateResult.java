package com.regattadesk.performance;

import java.util.List;

public record PerformanceGateResult(
    PerformanceGateStatus status,
    List<String> bottlenecks,
    String regressionRiskSummary
) {
}
