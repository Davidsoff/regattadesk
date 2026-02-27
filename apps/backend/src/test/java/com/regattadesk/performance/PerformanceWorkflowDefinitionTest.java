package com.regattadesk.performance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceWorkflowDefinitionTest {

    @Test
    void includesCiAdjacentSmokeAndScheduledDeepRunWorkflow() throws IOException {
        Path workflow = Path.of("..", "..", ".github", "workflows", "performance-load.yml").normalize();
        String content = Files.readString(workflow);

        assertTrue(content.contains("pull_request:"), "workflow must run smoke checks for PRs");
        assertTrue(content.contains("schedule:"), "workflow must include scheduled deep runs");
        assertTrue(content.contains("upload-artifact"), "workflow must archive reports");
    }
}
