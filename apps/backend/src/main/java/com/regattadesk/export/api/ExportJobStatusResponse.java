package com.regattadesk.export.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response body for job status polling.
 *
 * <ul>
 *   <li>{@code status}       – one of {@code pending}, {@code processing},
 *                              {@code completed}, {@code failed}</li>
 *   <li>{@code download_url} – relative URL to download the PDF artifact;
 *                              present only when status is {@code completed}</li>
 *   <li>{@code error}        – human-readable error description;
 *                              present only when status is {@code failed}</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ExportJobStatusResponse", description = "Export job status response for polling clients.")
public class ExportJobStatusResponse {

    @JsonProperty("status")
    @Schema(
            required = true,
            description = "Current lifecycle state of the export job.",
            enumeration = {"pending", "processing", "completed", "failed"})
    private final String status;

    @JsonProperty("download_url")
    @Schema(
            type = SchemaType.STRING,
            description = "Absolute-path URL to download the PDF artifact. Present only when status is \"completed\" and the artifact has not expired.")
    private final String downloadUrl;

    @JsonProperty("error")
    @Schema(
            type = SchemaType.STRING,
            description = "Human-readable error description. Present only when status is \"failed\".")
    private final String error;

    public ExportJobStatusResponse(String status, String downloadUrl, String error) {
        this.status = status;
        this.downloadUrl = downloadUrl;
        this.error = error;
    }

    public String getStatus() {
        return status;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getError() {
        return error;
    }
}
