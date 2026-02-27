package com.regattadesk.performance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceWorkflowDefinitionTest {

    private static Path resolvePerformanceWorkflowPath() {
        Path current = Path.of(".").toAbsolutePath().normalize();
        while (current != null) {
            Path workflow = current.resolve(".github").resolve("workflows").resolve("performance-load.yml");
            if (Files.isRegularFile(workflow)) {
                return workflow;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate .github/workflows/performance-load.yml from current directory");
    }

    @Test
    void includesCiAdjacentSmokeAndScheduledDeepRunWorkflow() throws IOException {
        Path workflow = resolvePerformanceWorkflowPath();
        String content = Files.readString(workflow);

        assertTrue(content.contains("pull_request:"), "workflow must run smoke checks for PRs");
        assertTrue(content.contains("schedule:"), "workflow must include scheduled deep runs");
        assertTrue(content.contains("upload-artifact"), "workflow must archive reports");
    }
}
