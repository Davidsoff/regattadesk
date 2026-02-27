package com.regattadesk.performance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record LoadScenarioCatalog(List<LoadScenario> scenarios) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path DEFAULT_CONFIG = resolveDefaultConfig();

    public static LoadScenarioCatalog fromDefaultConfig() {
        return fromPath(DEFAULT_CONFIG);
    }

    static LoadScenarioCatalog fromPath(Path path) {
        try {
            byte[] json = Files.readAllBytes(path);
            ScenarioConfig config = OBJECT_MAPPER.readValue(json, ScenarioConfig.class);
            return new LoadScenarioCatalog(config.scenarios);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load load-scenario config at " + path, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ScenarioConfig {
        public List<LoadScenario> scenarios;
    }

    private static Path resolveDefaultConfig() {
        List<Path> candidates = List.of(
            Path.of("performance", "load-scenarios.json"),
            Path.of("apps", "backend", "performance", "load-scenarios.json")
        );
        List<Path> attempted = new ArrayList<>();
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            attempted.add(normalized);
            if (Files.isRegularFile(normalized)) {
                return normalized;
            }
        }
        throw new IllegalStateException("Unable to locate load-scenarios.json in any known location: " + attempted);
    }
}
