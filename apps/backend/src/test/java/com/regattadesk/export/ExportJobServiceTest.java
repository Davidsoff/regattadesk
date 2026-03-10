package com.regattadesk.export;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExportJobService}.
 *
 * <p>Uses Mockito to isolate the service from database I/O.</p>
 */
class ExportJobServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-15T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private ExportJobRepository repository;
    private ExportRegattaRepository regattaRepository;
    private ExportJobService service;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        repository = mock(ExportJobRepository.class);
        regattaRepository = mock(ExportRegattaRepository.class);
        executorService = mock(ExecutorService.class);
        service = new ExportJobService();
        service.repository = repository;
        service.regattaRepository = regattaRepository;
        service.clock = FIXED_CLOCK;
        service.executorService = executorService;
    }

    // -------------------------------------------------------------------------
    // createJob
    // -------------------------------------------------------------------------

    @Test
    void createJob_savesNewPendingJob() throws SQLException {
        UUID regattaId = UUID.randomUUID();
        AtomicReference<ExportJob> saved = new AtomicReference<>();
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return null;
        }).when(repository).save(any(ExportJob.class));

        UUID jobId = service.createJob(regattaId, "admin");

        assertNotNull(jobId);
        ExportJob job = saved.get();
        assertNotNull(job);
        assertEquals(jobId, job.getId());
        assertEquals(regattaId, job.getRegattaId());
        assertEquals(ExportJobStatus.PENDING, job.getStatus());
        assertEquals(ExportJob.TYPE_PRINTABLE, job.getType());
        assertEquals("admin", job.getRequestedBy());
        assertNull(job.getArtifact());
        assertNull(job.getErrorMessage());
    }

    @Test
    void createJob_requiresRegattaId() {
        assertThrows(IllegalArgumentException.class, () -> service.createJob(null, "user"));
    }

    @Test
    void createJob_requiresRequestedBy() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createJob(UUID.randomUUID(), null));
        assertThrows(IllegalArgumentException.class,
                () -> service.createJob(UUID.randomUUID(), "   "));
    }

    @Test
    void createJob_propagatesRepositoryException() throws SQLException {
        doThrow(new SQLException("DB error")).when(repository).save(any());
        assertThrows(ExportJobException.class,
                () -> service.createJob(UUID.randomUUID(), "user"));
    }

    // -------------------------------------------------------------------------
    // processJob – regatta not found
    // -------------------------------------------------------------------------

    @Test
    void processJob_failsWhenRegattaNotFound() throws SQLException {
        UUID regattaId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        ExportJob pendingJob = pendingJob(jobId, regattaId);

        when(repository.markProcessingIfPending(eq(jobId), any())).thenReturn(true);
        when(repository.findById(jobId)).thenReturn(Optional.of(pendingJob));
        when(regattaRepository.findMetadata(regattaId)).thenReturn(Optional.empty());

        AtomicReference<ExportJob> lastUpdate = new AtomicReference<>();
        doAnswer(inv -> { lastUpdate.set(inv.getArgument(0)); return null; })
                .when(repository).update(any());

        service.processJob(jobId);

        ExportJob result = lastUpdate.get();
        assertNotNull(result);
        assertEquals(ExportJobStatus.FAILED, result.getStatus());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains(regattaId.toString()));
    }

    // -------------------------------------------------------------------------
    // processJob – completed successfully
    // -------------------------------------------------------------------------

    @Test
    void processJob_completesWithArtifact() throws SQLException {
        UUID regattaId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        ExportJob pendingJob = pendingJob(jobId, regattaId);

        when(repository.markProcessingIfPending(eq(jobId), any())).thenReturn(true);
        when(repository.findById(jobId))
                .thenReturn(Optional.of(pendingJob))
                // second call (in catch) should not be needed on happy path
                .thenReturn(Optional.of(pendingJob));
        when(regattaRepository.findMetadata(regattaId))
                .thenReturn(Optional.of(new ExportRegattaRepository.RegattaMetadata(
                        "Test Regatta", "UTC", 1, 2)));

        AtomicReference<ExportJob> lastUpdate = new AtomicReference<>();
        doAnswer(inv -> { lastUpdate.set(inv.getArgument(0)); return null; })
                .when(repository).update(any());

        service.processJob(jobId);

        ExportJob result = lastUpdate.get();
        assertNotNull(result);
        assertEquals(ExportJobStatus.COMPLETED, result.getStatus());
        assertNotNull(result.getArtifact());
        assertTrue(result.getArtifact().length > 0);
        assertNotNull(result.getExpiresAt());
        assertEquals(FIXED_INSTANT.plusSeconds(ExportJob.ARTIFACT_TTL_SECONDS), result.getExpiresAt());
    }

    @Test
    void processJob_transitionsThroughProcessingState() throws SQLException {
        UUID regattaId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        ExportJob pendingJob = pendingJob(jobId, regattaId);

        when(repository.markProcessingIfPending(eq(jobId), any())).thenReturn(true);
        when(repository.findById(jobId)).thenReturn(Optional.of(pendingJob));
        when(regattaRepository.findMetadata(regattaId))
                .thenReturn(Optional.of(new ExportRegattaRepository.RegattaMetadata(
                        "Test Regatta", "UTC", 1, 2)));

        java.util.List<ExportJobStatus> statusSequence = new java.util.ArrayList<>();
        doAnswer(inv -> {
            statusSequence.add(((ExportJob) inv.getArgument(0)).getStatus());
            return null;
        }).when(repository).update(any());

        service.processJob(jobId);

        assertFalse(statusSequence.isEmpty(), "Should write at least one final update");
        assertEquals(ExportJobStatus.COMPLETED, statusSequence.get(statusSequence.size() - 1));
    }

    // -------------------------------------------------------------------------
    // processJob – skips non-pending jobs
    // -------------------------------------------------------------------------

    @Test
    void processJob_skipsAlreadyProcessingJob() throws SQLException {
        UUID jobId = UUID.randomUUID();
        when(repository.markProcessingIfPending(eq(jobId), any())).thenReturn(false);
        when(repository.findJobMetadataById(jobId)).thenReturn(Optional.of(
                new ExportJob(jobId, UUID.randomUUID(), ExportJob.TYPE_PRINTABLE,
                        ExportJobStatus.PROCESSING, null, null, null,
                        FIXED_INSTANT, FIXED_INSTANT, null)
        ));

        service.processJob(jobId);

        verify(repository, never()).update(any());
    }

    @Test
    void processJob_skipsCompletedJob() throws SQLException {
        UUID jobId = UUID.randomUUID();
        when(repository.markProcessingIfPending(eq(jobId), any())).thenReturn(false);
        when(repository.findJobMetadataById(jobId)).thenReturn(Optional.of(
                new ExportJob(jobId, UUID.randomUUID(), ExportJob.TYPE_PRINTABLE,
                        ExportJobStatus.COMPLETED, null, null, null,
                        FIXED_INSTANT, FIXED_INSTANT,
                        FIXED_INSTANT.plusSeconds(ExportJob.ARTIFACT_TTL_SECONDS))
        ));

        service.processJob(jobId);

        verify(repository, never()).update(any());
    }

    @Test
    void processJob_gracefullyHandlesMissingJob() throws SQLException {
        UUID jobId = UUID.randomUUID();
        when(repository.markProcessingIfPending(eq(jobId), any())).thenReturn(false);
        when(repository.findJobMetadataById(jobId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.processJob(jobId));
        verify(repository, never()).update(any());
    }

    // -------------------------------------------------------------------------
    // getJob
    // -------------------------------------------------------------------------

    @Test
    void getJob_delegatesToRepository() throws SQLException {
        UUID jobId = UUID.randomUUID();
        ExportJob job = pendingJob(jobId, UUID.randomUUID());
        when(repository.findById(jobId)).thenReturn(Optional.of(job));

        Optional<ExportJob> result = service.getJob(jobId);

        assertTrue(result.isPresent());
        assertEquals(jobId, result.get().getId());
    }

    @Test
    void getJob_returnsEmptyForMissingJob() throws SQLException {
        UUID jobId = UUID.randomUUID();
        when(repository.findById(jobId)).thenReturn(Optional.empty());

        Optional<ExportJob> result = service.getJob(jobId);

        assertFalse(result.isPresent());
    }

    @Test
    void getJob_wrapsRepositoryException() throws SQLException {
        UUID jobId = UUID.randomUUID();
        when(repository.findById(jobId)).thenThrow(new SQLException("DB error"));

        assertThrows(ExportJobException.class, () -> service.getJob(jobId));
    }

    @Test
    void getJobMetadata_delegatesToRepository() throws SQLException {
        UUID jobId = UUID.randomUUID();
        ExportJob job = pendingJob(jobId, UUID.randomUUID());
        when(repository.findJobMetadataById(jobId)).thenReturn(Optional.of(job));

        Optional<ExportJob> result = service.getJobMetadata(jobId);

        assertTrue(result.isPresent());
        assertEquals(jobId, result.get().getId());
    }

    @Test
    void startProcessingAsync_submitsToExecutor() {
        UUID jobId = UUID.randomUUID();
        service.startProcessingAsync(jobId);
        verify(executorService).execute(any(Runnable.class));
    }

    // -------------------------------------------------------------------------
    // ExportJob.isDownloadable
    // -------------------------------------------------------------------------

    @Test
    void isDownloadable_trueForNonExpiredCompletedJob() {
        Instant expiresAt = FIXED_INSTANT.plusSeconds(600);
        ExportJob job = new ExportJob(UUID.randomUUID(), UUID.randomUUID(),
                ExportJob.TYPE_PRINTABLE, ExportJobStatus.COMPLETED,
                new byte[]{1, 2, 3}, null, null,
                FIXED_INSTANT, FIXED_INSTANT, expiresAt);

        assertTrue(job.isDownloadable(FIXED_INSTANT));
    }

    @Test
    void isDownloadable_falseAfterExpiry() {
        Instant expiresAt = FIXED_INSTANT.minusSeconds(1);
        ExportJob job = new ExportJob(UUID.randomUUID(), UUID.randomUUID(),
                ExportJob.TYPE_PRINTABLE, ExportJobStatus.COMPLETED,
                new byte[]{1, 2, 3}, null, null,
                FIXED_INSTANT, FIXED_INSTANT, expiresAt);

        assertFalse(job.isDownloadable(FIXED_INSTANT));
    }

    @Test
    void isDownloadable_falseForPendingJob() {
        ExportJob job = pendingJob(UUID.randomUUID(), UUID.randomUUID());
        assertFalse(job.isDownloadable(FIXED_INSTANT));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ExportJob pendingJob(UUID jobId, UUID regattaId) {
        return new ExportJob(jobId, regattaId, ExportJob.TYPE_PRINTABLE,
                ExportJobStatus.PENDING, null, null, null,
                FIXED_INSTANT, FIXED_INSTANT, null);
    }
}
