package com.regattadesk.performance;

public record LoadScenario(
    String id,
    String name,
    String method,
    String path,
    int users,
    int iterations,
    String trafficClass
) {
}
