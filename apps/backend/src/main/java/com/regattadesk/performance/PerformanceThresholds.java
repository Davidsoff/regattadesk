package com.regattadesk.performance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record PerformanceThresholds(
    double maxP95LatencyMs,
    double maxErrorRatePct,
    double maxCpuUtilizationPct,
    double maxMemoryUtilizationPct
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path DEFAULT_CONFIG = resolveDefaultConfig();

    public static PerformanceThresholds fromDefaultConfig() {
        try {
            byte[] json = Files.readAllBytes(DEFAULT_CONFIG);
            return OBJECT_MAPPER.readValue(json, ThresholdConfig.class).toThresholds();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load performance thresholds at " + DEFAULT_CONFIG, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ThresholdConfig {
        public double maxP95LatencyMs;
        public double maxErrorRatePct;
        public double maxCpuUtilizationPct;
        public double maxMemoryUtilizationPct;

        PerformanceThresholds toThresholds() {
            return new PerformanceThresholds(
                maxP95LatencyMs,
                maxErrorRatePct,
                maxCpuUtilizationPct,
                maxMemoryUtilizationPct
            );
        }
    }

    private static Path resolveDefaultConfig() {
        List<Path> candidates = List.of(
            Path.of("performance", "performance-thresholds.json"),
            Path.of("apps", "backend", "performance", "performance-thresholds.json")
        );
        List<Path> attempted = new ArrayList<>();
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            attempted.add(normalized);
            if (Files.isRegularFile(normalized)) {
                return normalized;
            }
        }
        throw new IllegalStateException(
            "Unable to locate performance-thresholds.json in any known location: " + attempted
        );
    }
}
