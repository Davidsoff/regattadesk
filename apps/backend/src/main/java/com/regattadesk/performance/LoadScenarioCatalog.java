package com.regattadesk.performance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record LoadScenarioCatalog(List<LoadScenario> scenarios) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path DEFAULT_CONFIG = Path.of("performance", "load-scenarios.json");

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
}
