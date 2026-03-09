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
}
