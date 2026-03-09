package com.regattadesk.export;

import com.regattadesk.pdf.PdfGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
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
 * The caller (typically a REST resource) is responsible for invoking
 * {@link #processJob(UUID)} on a background thread so that the POST endpoint
 * returns immediately.  Using Java 25 virtual threads is recommended:
 * <pre>{@code
 *   Thread.ofVirtual().start(() -> exportJobService.processJob(jobId));
 * }</pre>
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

    Clock clock = Clock.systemUTC();

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
            Optional<ExportJob> opt = repository.findById(jobId);
            if (opt.isEmpty()) {
                LOG.warnf("Export job %s not found for processing", jobId);
                return;
            }
            ExportJob job = opt.get();
            if (job.getStatus() != ExportJobStatus.PENDING) {
                LOG.warnf("Export job %s is in unexpected state %s, skipping", jobId, job.getStatus());
                return;
            }

            // Transition to PROCESSING
            updateStatus(job, ExportJobStatus.PROCESSING, null, null, null);

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
            try {
                Optional<ExportJob> opt = repository.findById(jobId);
                opt.ifPresent(job -> failJob(job, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            } catch (SQLException sqlEx) {
                LOG.errorf(sqlEx, "Failed to persist failure state for export job %s", jobId);
            }
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
}
