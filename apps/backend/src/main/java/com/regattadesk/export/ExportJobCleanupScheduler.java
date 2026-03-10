package com.regattadesk.export;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Scheduled cleanup for stale export jobs.
 *
 * Removes completed jobs after artifact expiry and failed jobs after a retention window.
 */
@ApplicationScoped
public class ExportJobCleanupScheduler {

    private static final Logger LOG = Logger.getLogger(ExportJobCleanupScheduler.class);

    @Inject
    ExportJobRepository repository;

    @ConfigProperty(name = "regattadesk.export.cleanup.failed-retention", defaultValue = "P7D")
    Duration failedJobRetention;

    Clock clock = Clock.systemUTC();

    @Scheduled(
            cron = "{regattadesk.export.cleanup.cron:0 0 * * * ?}",
            identity = "export-job-cleanup"
    )
    void cleanup() {
        Instant now = clock.instant();
        Instant failedBefore = now.minus(failedJobRetention);
        try {
            int purged = repository.purgeExpiredAndFailed(now, failedBefore);
            if (purged > 0) {
                LOG.infof("Purged %d stale export jobs", purged);
            }
        } catch (SQLException e) {
            LOG.error("Failed to purge stale export jobs", e);
        }
    }
}
