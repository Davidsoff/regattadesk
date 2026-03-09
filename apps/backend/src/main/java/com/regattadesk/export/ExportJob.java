package com.regattadesk.export;

import java.time.Instant;
import java.util.UUID;

/**
 * Export job domain model.
 *
 * <p>Represents an asynchronous request to generate a printable PDF for a regatta.
 * Jobs are created with {@link ExportJobStatus#PENDING}, transition through
 * {@link ExportJobStatus#PROCESSING}, and settle in either
 * {@link ExportJobStatus#COMPLETED} (with a non-null {@code artifact}) or
 * {@link ExportJobStatus#FAILED} (with a non-null {@code errorMessage}).</p>
 *
 * <p>Completed artifacts expire after one hour (see {@code expiresAt}).</p>
 */
public class ExportJob {

    /** One hour in seconds; download window after job completion. */
    public static final long ARTIFACT_TTL_SECONDS = 3600L;

    /** Job type constant for printable PDF exports. */
    public static final String TYPE_PRINTABLE = "printable";

    private final UUID id;
    private final UUID regattaId;
    private final String type;
    private final ExportJobStatus status;
    private final byte[] artifact;
    private final String errorMessage;
    private final String requestedBy;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant expiresAt;

    ExportJob(UUID id, UUID regattaId, String type, ExportJobStatus status,
              byte[] artifact, String errorMessage, String requestedBy,
              Instant createdAt, Instant updatedAt, Instant expiresAt) {
        this.id = id;
        this.regattaId = regattaId;
        this.type = type;
        this.status = status;
        this.artifact = artifact;
        this.errorMessage = errorMessage;
        this.requestedBy = requestedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRegattaId() {
        return regattaId;
    }

    public String getType() {
        return type;
    }

    public ExportJobStatus getStatus() {
        return status;
    }

    public byte[] getArtifact() {
        return artifact;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Returns {@code true} if the artifact is available and the download window has not passed.
     *
     * @param now current instant for expiry comparison
     * @return true if download is available
     */
    public boolean isDownloadable(Instant now) {
        return status == ExportJobStatus.COMPLETED
                && artifact != null
                && expiresAt != null
                && now.isBefore(expiresAt);
    }
}
