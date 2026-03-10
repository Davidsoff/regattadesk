package com.regattadesk.export;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportJobCleanupSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");

    private ExportJobRepository repository;
    private ExportJobCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = mock(ExportJobRepository.class);
        scheduler = new ExportJobCleanupScheduler();
        scheduler.repository = repository;
        scheduler.failedJobRetention = Duration.ofDays(7);
        scheduler.clock = Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Test
    void cleanup_callsRepositoryWithExpectedCutoffs() throws Exception {
        when(repository.purgeExpiredAndFailed(eq(NOW), eq(NOW.minus(Duration.ofDays(7)))))
                .thenReturn(3);

        scheduler.cleanup();

        verify(repository).purgeExpiredAndFailed(eq(NOW), eq(NOW.minus(Duration.ofDays(7))));
    }
}
