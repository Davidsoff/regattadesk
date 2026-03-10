package com.regattadesk.export.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.export.ExportJobException;
import com.regattadesk.export.ExportJobService;
import com.regattadesk.export.ExportRegattaRepository;
import com.regattadesk.security.RequireRole;
import com.regattadesk.security.Role;
import com.regattadesk.security.SecurityContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.logging.Logger;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * REST resource for requesting printable PDF export jobs.
 *
 * <p>Endpoint: {@code POST /api/v1/regattas/{regatta_id}/export/printables}</p>
 *
 * <p>Creates an async export job and starts PDF generation on a bounded worker pool.
 * The caller should immediately begin polling
 * {@code GET /api/v1/jobs/{job_id}} for status.</p>
 *
 * <p>Required role: {@code SUPER_ADMIN}, {@code REGATTA_ADMIN}, {@code HEAD_OF_JURY},
 * or {@code INFO_DESK}.</p>
 */
@Path("/api/v1/regattas/{regatta_id}/export/printables")
public class PrintableExportResource {

    private static final Logger LOG = Logger.getLogger(PrintableExportResource.class);

    @Inject
    ExportJobService exportJobService;

    @Inject
    ExportRegattaRepository regattaRepository;

    @Inject
    SecurityContext securityContext;

    /**
     * Request a printable export job for a regatta.
     *
     * @param regattaId the regatta UUID
     * @return 202 Accepted with job ID, 404 if regatta not found, 403 if unauthorized
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @RequireRole({Role.SUPER_ADMIN, Role.REGATTA_ADMIN, Role.HEAD_OF_JURY, Role.INFO_DESK})
    @Operation(summary = "Request Printable Export")
    @APIResponses({
            @APIResponse(responseCode = "202", description = "Export job accepted; processing in background",
                    content = @Content(schema = @Schema(implementation = ExportJobCreatedResponse.class))),
            @APIResponse(responseCode = "403", description = "Forbidden – insufficient role",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "404", description = "Regatta not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response requestPrintableExport(@PathParam("regatta_id") UUID regattaId) {
        // Verify regatta exists before creating job
        try {
            Optional<ExportRegattaRepository.RegattaMetadata> meta =
                    regattaRepository.findMetadata(regattaId);
            if (meta.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ErrorResponse.notFound("Regatta not found"))
                        .build();
            }
        } catch (SQLException e) {
            LOG.errorf(e, "Failed to verify regatta %s", regattaId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError("Failed to verify regatta"))
                    .build();
        }

        String requestedBy = securityContext.getPrincipal() != null
                ? securityContext.getPrincipal().getUsername()
                : null;
        if (requestedBy == null || requestedBy.isBlank()) {
            LOG.errorf("Requester identity missing for regatta export %s", regattaId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError("Requester identity is required"))
                    .build();
        }
        try {
            UUID jobId = exportJobService.createJob(regattaId, requestedBy);
            exportJobService.startProcessingAsync(jobId);
            return Response.accepted(new ExportJobCreatedResponse(jobId)).build();
        } catch (ExportJobException e) {
            LOG.errorf(e, "Failed to create printable export job for regatta %s", regattaId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError("Failed to create export job"))
                    .build();
        }
    }
}
