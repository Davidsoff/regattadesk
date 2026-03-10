package com.regattadesk.export;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC repository for {@link ExportJob} persistence.
 *
 * <p>All public methods are safe to call from virtual threads; each operation
 * acquires and releases its own JDBC connection.</p>
 */
@ApplicationScoped
public class ExportJobRepository {

    private static final Logger LOG = Logger.getLogger(ExportJobRepository.class);

    @Inject
    DataSource dataSource;

    /**
     * Inserts a new export job record.
     *
     * @param job job to persist
     * @throws SQLException on persistence failure
     */
    public void save(ExportJob job) throws SQLException {
        String sql = """
                INSERT INTO export_jobs
                    (id, regatta_id, type, status, artifact, error_message,
                     requested_by, created_at, updated_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, job.getId());
            stmt.setObject(2, job.getRegattaId());
            stmt.setString(3, job.getType());
            stmt.setString(4, job.getStatus().value());
            stmt.setBytes(5, job.getArtifact());
            stmt.setString(6, job.getErrorMessage());
            stmt.setString(7, job.getRequestedBy());
            stmt.setTimestamp(8, Timestamp.from(job.getCreatedAt()));
            stmt.setTimestamp(9, Timestamp.from(job.getUpdatedAt()));
            stmt.setTimestamp(10, job.getExpiresAt() != null ? Timestamp.from(job.getExpiresAt()) : null);
            stmt.executeUpdate();
        }
    }

    /**
     * Updates the status, artifact, error, updated_at, and expires_at of an existing job.
     *
     * @param job job with updated fields
     * @throws SQLException on persistence failure
     */
    public void update(ExportJob job) throws SQLException {
        String sql = """
                UPDATE export_jobs
                SET status = ?, artifact = ?, error_message = ?,
                    updated_at = ?, expires_at = ?
                WHERE id = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, job.getStatus().value());
            stmt.setBytes(2, job.getArtifact());
            stmt.setString(3, job.getErrorMessage());
            stmt.setTimestamp(4, Timestamp.from(job.getUpdatedAt()));
            stmt.setTimestamp(5, job.getExpiresAt() != null ? Timestamp.from(job.getExpiresAt()) : null);
            stmt.setObject(6, job.getId());
            stmt.executeUpdate();
        }
    }

    /**
     * Atomically transitions a job to PROCESSING if and only if it is currently PENDING.
     *
     * @param jobId job UUID
     * @param updatedAt timestamp for updated_at
     * @return true when a row was updated; false when status was not PENDING or row not found
     * @throws SQLException on persistence failure
     */
    public boolean markProcessingIfPending(UUID jobId, Instant updatedAt) throws SQLException {
        String sql = """
                UPDATE export_jobs
                SET status = ?, updated_at = ?
                WHERE id = ? AND status = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ExportJobStatus.PROCESSING.value());
            stmt.setTimestamp(2, Timestamp.from(updatedAt));
            stmt.setObject(3, jobId);
            stmt.setString(4, ExportJobStatus.PENDING.value());
            return stmt.executeUpdate() == 1;
        }
    }

    /**
     * Marks a job as FAILED regardless of current status.
     *
     * @param jobId job UUID
     * @param errorMessage human-readable failure reason
     * @param updatedAt update timestamp
     * @throws SQLException on persistence failure
     */
    public void markFailed(UUID jobId, String errorMessage, Instant updatedAt) throws SQLException {
        String sql = """
                UPDATE export_jobs
                SET status = ?, artifact = NULL, error_message = ?, expires_at = NULL, updated_at = ?
                WHERE id = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ExportJobStatus.FAILED.value());
            stmt.setString(2, errorMessage);
            stmt.setTimestamp(3, Timestamp.from(updatedAt));
            stmt.setObject(4, jobId);
            stmt.executeUpdate();
        }
    }

    /**
     * Finds a job by its unique identifier.
     *
     * @param jobId job UUID
     * @return optional job, empty when not found
     * @throws SQLException on query failure
     */
    public Optional<ExportJob> findById(UUID jobId) throws SQLException {
        String sql = """
                SELECT id, regatta_id, type, status, artifact, error_message,
                       requested_by, created_at, updated_at, expires_at
                FROM export_jobs
                WHERE id = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Finds a job by ID without loading artifact bytes.
     *
     * @param jobId job UUID
     * @return optional job metadata
     * @throws SQLException on query failure
     */
    public Optional<ExportJob> findJobMetadataById(UUID jobId) throws SQLException {
        String sql = """
                SELECT id, regatta_id, type, status, error_message,
                       requested_by, created_at, updated_at, expires_at
                FROM export_jobs
                WHERE id = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapMetadataRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Deletes expired completed jobs and stale failed jobs.
     *
     * @param now current timestamp for completed-job expiry checks
     * @param failedBefore cutoff for failed job retention
     * @return number of deleted rows
     * @throws SQLException on persistence failure
     */
    public int purgeExpiredAndFailed(Instant now, Instant failedBefore) throws SQLException {
        String sql = """
                DELETE FROM export_jobs
                WHERE (status = ? AND expires_at IS NOT NULL AND expires_at < ?)
                   OR (status = ? AND updated_at < ?)
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ExportJobStatus.COMPLETED.value());
            stmt.setTimestamp(2, Timestamp.from(now));
            stmt.setString(3, ExportJobStatus.FAILED.value());
            stmt.setTimestamp(4, Timestamp.from(failedBefore));
            return stmt.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private ExportJob mapRow(ResultSet rs) throws SQLException {
        Timestamp expiresTs = rs.getTimestamp("expires_at");
        return new ExportJob(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("regatta_id"),
                rs.getString("type"),
                ExportJobStatus.fromValue(rs.getString("status")),
                rs.getBytes("artifact"),
                rs.getString("error_message"),
                rs.getString("requested_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                expiresTs != null ? expiresTs.toInstant() : null
        );
    }

    private ExportJob mapMetadataRow(ResultSet rs) throws SQLException {
        Timestamp expiresTs = rs.getTimestamp("expires_at");
        return new ExportJob(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("regatta_id"),
                rs.getString("type"),
                ExportJobStatus.fromValue(rs.getString("status")),
                null,
                rs.getString("error_message"),
                rs.getString("requested_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                expiresTs != null ? expiresTs.toInstant() : null
        );
    }
}
