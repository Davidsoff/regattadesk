package com.regattadesk.export.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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
public class ExportJobStatusResponse {

    @JsonProperty("status")
    private final String status;

    @JsonProperty("download_url")
    private final String downloadUrl;

    @JsonProperty("error")
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
