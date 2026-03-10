package com.regattadesk.export.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.export.ExportJob;
import com.regattadesk.export.ExportJobException;
import com.regattadesk.export.ExportJobStatus;
import com.regattadesk.export.ExportJobService;
import com.regattadesk.security.RequireRole;
import com.regattadesk.security.Role;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * REST resource for polling export job status and downloading completed artifacts.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET /api/v1/jobs/{job_id}}          – job status response</li>
 *   <li>{@code GET /api/v1/jobs/{job_id}/download}  – download completed PDF</li>
 * </ul>
 *
 * <p>Required role: {@code SUPER_ADMIN}, {@code REGATTA_ADMIN}, {@code HEAD_OF_JURY},
 * or {@code INFO_DESK}.</p>
 */
@Path("/api/v1/jobs/{job_id}")
public class ExportJobResource {

    private static final Logger LOG = Logger.getLogger(ExportJobResource.class);

    /** Absolute-path URL template for artifact download links. */
    private static final String DOWNLOAD_URL_TEMPLATE = "/api/v1/jobs/%s/download";

    @Inject
    ExportJobService exportJobService;

    /** Clock used for expiry checks. Package-visible for test overrides. */
    Clock clock = Clock.systemUTC();

    /**
     * Get the current status of an export job.
     *
     * @param jobId job UUID
     * @return 200 with status response, 404 if not found
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RequireRole({Role.SUPER_ADMIN, Role.REGATTA_ADMIN, Role.HEAD_OF_JURY, Role.INFO_DESK})
    @Operation(summary = "Get Export Job Status")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Job status response",
                    content = @Content(schema = @Schema(implementation = ExportJobStatusResponse.class))),
            @APIResponse(responseCode = "403", description = "Forbidden – insufficient role",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "404", description = "Job not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getJobStatus(@PathParam("job_id") UUID jobId) {
        try {
            Optional<ExportJob> opt = exportJobService.getJobMetadata(jobId);
            if (opt.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ErrorResponse.notFound("Export job not found"))
                        .build();
            }

            ExportJob job = opt.get();
            String downloadUrl = null;
            if (job.getStatus() == ExportJobStatus.COMPLETED
                    && job.getExpiresAt() != null
                    && clock.instant().isBefore(job.getExpiresAt())) {
                downloadUrl = String.format(DOWNLOAD_URL_TEMPLATE, jobId);
            }

            ExportJobStatusResponse body = new ExportJobStatusResponse(
                    job.getStatus().value(),
                    downloadUrl,
                    job.getErrorMessage()
            );
            return Response.ok(body).build();
        } catch (ExportJobException e) {
            LOG.errorf(e, "Failed to retrieve export job %s", jobId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError("Failed to retrieve export job"))
                    .build();
        }
    }

    /**
     * Download the completed PDF artifact for an export job.
     *
     * @param jobId job UUID
     * @return 200 with PDF bytes, 404 if not found, 410 if expired
     */
    @GET
    @Path("/download")
    @Produces("application/pdf")
    @RequireRole({Role.SUPER_ADMIN, Role.REGATTA_ADMIN, Role.HEAD_OF_JURY, Role.INFO_DESK})
    @Operation(summary = "Download Export Artifact")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "PDF artifact",
                    content = @Content(mediaType = "application/pdf"),
                    headers = {
                            @Header(name = "Content-Disposition",
                                    description = "attachment; filename=\"export-{job_id}.pdf\"",
                                    schema = @Schema(type = SchemaType.STRING))
                    }),
            @APIResponse(responseCode = "403", description = "Forbidden – insufficient role",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "404", description = "Job not found or artifact not available",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "410", description = "Artifact has expired",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response downloadArtifact(@PathParam("job_id") UUID jobId) {
        try {
            Optional<ExportJob> opt = exportJobService.getJob(jobId);
            if (opt.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(ErrorResponse.notFound("Export job not found"))
                        .build();
            }

            ExportJob job = opt.get();
            Instant now = clock.instant();

            if (job.getStatus() != ExportJobStatus.COMPLETED || job.getArtifact() == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(ErrorResponse.notFound("Export artifact not available"))
                        .build();
            }

            if (!job.isDownloadable(now)) {
                return Response.status(Response.Status.GONE)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(ErrorResponse.gone("ARTIFACT_EXPIRED", "Export artifact has expired"))
                        .build();
            }

            String filename = "export-" + jobId + ".pdf";
            return Response.ok(job.getArtifact())
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", "application/pdf")
                    .build();
        } catch (ExportJobException e) {
            LOG.errorf(e, "Failed to download export artifact for job %s", jobId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ErrorResponse.internalError("Failed to retrieve export job"))
                    .build();
        }
    }
}
