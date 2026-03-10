package com.regattadesk.export;

import com.regattadesk.pdf.PdfGenerator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

/**
 * Service for creating and executing asynchronous printable export jobs.
 *
 * <h2>Job Lifecycle</h2>
 * <ol>
 *   <li>{@link #createJob(UUID, String)} – persists a new job in {@code PENDING} state
 *       and returns the job ID immediately.</li>
 *   <li>{@link #processJob(UUID)} – transitions the job to {@code PROCESSING},
 *       generates the PDF, then transitions to {@code COMPLETED} (with artifact)
 *       or {@code FAILED} (with error message).</li>
 * </ol>
 *
 * <h2>Caller responsibility</h2>
 * The service exposes {@link #startProcessingAsync(UUID)} so callers can enqueue
 * processing work without managing executor lifecycle.
 */
@ApplicationScoped
public class ExportJobService {

    private static final Logger LOG = Logger.getLogger(ExportJobService.class);

    /** Default locale for PDF generation when not otherwise specified. */
    static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    @Inject
    ExportJobRepository repository;

    @Inject
    ExportRegattaRepository regattaRepository;

    @ConfigProperty(name = "regattadesk.export.max-concurrency", defaultValue = "4")
    int maxConcurrency;

    Clock clock = Clock.systemUTC();
    ExecutorService executorService;

    @PostConstruct
    void initExecutor() {
        int poolSize = Math.max(1, maxConcurrency);
        executorService = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(poolSize * 8),
                new ExportThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @PreDestroy
    void shutdownExecutor() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    /**
     * Creates a new printable export job in {@code PENDING} state.
     *
     * @param regattaId  target regatta UUID
     * @param requestedBy username or identifier of the requesting user
     * @return the new job ID
     * @throws IllegalArgumentException if regattaId is null
     * @throws ExportJobException       on persistence failure
     */
    public UUID createJob(UUID regattaId, String requestedBy) {
        if (regattaId == null) {
            throw new IllegalArgumentException("regattaId is required");
        }
        if (requestedBy == null || requestedBy.isBlank()) {
            throw new IllegalArgumentException("requestedBy is required");
        }
        Instant now = clock.instant();
        UUID jobId = UUID.randomUUID();
        ExportJob job = new ExportJob(
                jobId,
                regattaId,
                ExportJob.TYPE_PRINTABLE,
                ExportJobStatus.PENDING,
                null,
                null,
                requestedBy,
                now,
                now,
                null
        );
        try {
            repository.save(job);
        } catch (SQLException e) {
            throw new ExportJobException("Failed to create export job", e);
        }
        LOG.infof("Export job %s created for regatta %s by %s", jobId, regattaId, requestedBy);
        return jobId;
    }

    /**
     * Enqueues asynchronous processing for the given job.
     *
     * @param jobId job UUID
     * @throws ExportJobException if the job cannot be enqueued
     */
    public void startProcessingAsync(UUID jobId) {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId is required");
        }
        if (executorService == null) {
            initExecutor();
        }
        try {
            executorService.execute(() -> processJob(jobId));
        } catch (RejectedExecutionException e) {
            LOG.errorf(e, "Failed to enqueue export job %s", jobId);
            failJobById(jobId, "Failed to enqueue export processing");
            throw new ExportJobException("Failed to enqueue export job " + jobId, e);
        }
    }

    /**
     * Processes a pending export job.
     *
     * <p>This method is intended to run on a background thread.  It will:</p>
     * <ol>
     *   <li>Transition the job to {@code PROCESSING}</li>
     *   <li>Fetch regatta metadata</li>
     *   <li>Generate the PDF</li>
     *   <li>Transition to {@code COMPLETED} with the artifact and expiry, or
     *       {@code FAILED} with the error message</li>
     * </ol>
     *
     * @param jobId job to process
     */
    public void processJob(UUID jobId) {
        LOG.infof("Starting processing for export job %s", jobId);
        try {
            if (!repository.markProcessingIfPending(jobId, clock.instant())) {
                Optional<ExportJob> current = repository.findJobMetadataById(jobId);
                if (current.isEmpty()) {
                    LOG.warnf("Export job %s not found for processing", jobId);
                    return;
                }
                LOG.warnf("Export job %s is in unexpected state %s, skipping",
                        jobId, current.get().getStatus());
                return;
            }
            Optional<ExportJob> opt = repository.findById(jobId);
            if (opt.isEmpty()) {
                LOG.warnf("Export job %s not found for processing", jobId);
                return;
            }
            ExportJob job = opt.get();

            // Fetch regatta metadata
            Optional<ExportRegattaRepository.RegattaMetadata> metaOpt =
                    regattaRepository.findMetadata(job.getRegattaId());

            if (metaOpt.isEmpty()) {
                failJob(job, "Regatta " + job.getRegattaId() + " not found");
                return;
            }

            ExportRegattaRepository.RegattaMetadata meta = metaOpt.get();

            // Generate PDF
            byte[] pdf = PdfGenerator.generateSamplePdf(
                    meta.name(),
                    meta.drawRevision(),
                    meta.resultsRevision(),
                    DEFAULT_LOCALE,
                    ZoneId.of(meta.timeZone()),
                    clock
            );

            Instant now = clock.instant();
            Instant expiresAt = now.plusSeconds(ExportJob.ARTIFACT_TTL_SECONDS);
            updateStatus(job, ExportJobStatus.COMPLETED, pdf, null, expiresAt);
            LOG.infof("Export job %s completed, artifact size=%d bytes, expires=%s",
                    jobId, pdf.length, expiresAt);

        } catch (Exception e) {
            LOG.errorf(e, "Export job %s failed", jobId);
            failJobById(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    /**
     * Retrieves a job by ID.
     *
     * @param jobId job UUID
     * @return optional job
     * @throws ExportJobException on persistence failure
     */
    public Optional<ExportJob> getJob(UUID jobId) {
        try {
            return repository.findById(jobId);
        } catch (SQLException e) {
            throw new ExportJobException("Failed to retrieve export job " + jobId, e);
        }
    }

    /**
     * Retrieves a job by ID without loading the artifact payload.
     *
     * @param jobId job UUID
     * @return optional job metadata
     * @throws ExportJobException on persistence failure
     */
    public Optional<ExportJob> getJobMetadata(UUID jobId) {
        try {
            return repository.findJobMetadataById(jobId);
        } catch (SQLException e) {
            throw new ExportJobException("Failed to retrieve export job metadata " + jobId, e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void updateStatus(ExportJob job, ExportJobStatus status,
                               byte[] artifact, String errorMessage, Instant expiresAt) {
        ExportJob updated = new ExportJob(
                job.getId(),
                job.getRegattaId(),
                job.getType(),
                status,
                artifact,
                errorMessage,
                job.getRequestedBy(),
                job.getCreatedAt(),
                clock.instant(),
                expiresAt
        );
        try {
            repository.update(updated);
        } catch (SQLException e) {
            throw new ExportJobException("Failed to update export job " + job.getId(), e);
        }
    }

    private void failJob(ExportJob job, String errorMessage) {
        try {
            updateStatus(job, ExportJobStatus.FAILED, null, errorMessage, null);
        } catch (ExportJobException e) {
            LOG.errorf(e, "Failed to persist FAILED state for job %s", job.getId());
        }
    }

    private void failJobById(UUID jobId, String errorMessage) {
        try {
            repository.markFailed(jobId, errorMessage, clock.instant());
        } catch (SQLException e) {
            LOG.errorf(e, "Failed to persist FAILED state for export job %s", jobId);
        }
    }

    private static final class ExportThreadFactory implements ThreadFactory {
        private final AtomicInteger index = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = Thread.ofPlatform()
                    .name("export-job-worker-" + index.getAndIncrement())
                    .daemon(true)
                    .unstarted(r);
            thread.setUncaughtExceptionHandler(
                    (t, e) -> LOG.errorf(e, "Unhandled exception in %s", t.getName()));
            return thread;
        }
    }
}
