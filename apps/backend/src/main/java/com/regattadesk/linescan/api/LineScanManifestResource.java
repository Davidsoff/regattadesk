package com.regattadesk.linescan.api;

import com.regattadesk.linescan.*;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST resource for line-scan manifest operations.
 * 
 * Implements endpoints:
 * - POST /api/v1/regattas/{regatta_id}/line_scan/manifests (OperatorTokenAuth)
 * - GET /api/v1/regattas/{regatta_id}/line_scan/manifests/{manifest_id} (OperatorTokenAuth or StaffProxyAuth)
 */
@Path("/api/v1/regattas/{regatta_id}/line_scan/manifests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LineScanManifestResource {
    
    private static final Logger LOG = Logger.getLogger(LineScanManifestResource.class);
    
    private final LineScanManifestService manifestService;
    
    @Inject
    public LineScanManifestResource(LineScanManifestService manifestService) {
        this.manifestService = manifestService;
    }
    
    /**
     * Upsert a line-scan manifest.
     * Auth: OperatorTokenAuth only (via x_operator_token header)
     */
    @POST
    public Response upsertManifest(
            @PathParam("regatta_id") UUID regattaId,
            @HeaderParam("x_operator_token") String operatorToken,
            @Valid @NotNull(message = "request body is required") LineScanManifestUpsertRequest request) {
        
        // TODO: Validate operator token (BC06-001 should provide validation)
        // For now, we'll proceed assuming BC06-001 implements token validation
        if (operatorToken == null || operatorToken.isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("Missing or invalid operator token"))
                .build();
        }
        
        try {
            // Convert request to domain model
            List<LineScanManifestTile> tiles = request.getTiles().stream()
                .map(t -> new LineScanManifestTile(
                    t.getTileId(),
                    t.getTileX(),
                    t.getTileY(),
                    t.getContentType(),
                    t.getByteSize()
                ))
                .collect(Collectors.toList());
            
            LineScanManifest manifest = LineScanManifest.builder()
                .regattaId(regattaId)
                .captureSessionId(request.getCaptureSessionId())
                .tileSizePx(request.getTileSizePx())
                .primaryFormat(request.getPrimaryFormat())
                .fallbackFormat(request.getFallbackFormat())
                .xOriginTimestampMs(request.getXOriginTimestampMs())
                .msPerPixel(request.getMsPerPixel())
                .tiles(tiles)
                .build();
            
            LineScanManifest saved = manifestService.upsertManifest(manifest);
            
            return Response.status(Response.Status.CREATED)
                .entity(new LineScanManifestResponse(saved))
                .build();
                
        } catch (MinioStorageAdapter.MinioStorageException e) {
            LOG.error("MinIO storage error during manifest upsert", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Storage error: " + e.getMessage()))
                .build();
        } catch (Exception e) {
            LOG.error("Unexpected error during manifest upsert", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Internal error"))
                .build();
        }
    }
    
    /**
     * Get a line-scan manifest by ID.
     * Auth: OperatorTokenAuth or StaffProxyAuth (via x_operator_token or forwarded headers)
     */
    @GET
    @Path("/{manifest_id}")
    public Response getManifest(
            @PathParam("regatta_id") UUID regattaId,
            @PathParam("manifest_id") UUID manifestId,
            @HeaderParam("x_operator_token") String operatorToken,
            @HeaderParam("x_forwarded_user") String forwardedUser) {
        
        // Auth check: either operator token or staff proxy auth
        if ((operatorToken == null || operatorToken.isBlank()) && 
            (forwardedUser == null || forwardedUser.isBlank())) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("Authentication required"))
                .build();
        }
        
        try {
            LineScanManifest manifest = manifestService.getManifest(manifestId)
                .orElse(null);
            
            if (manifest == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Manifest not found"))
                    .build();
            }
            
            // Verify manifest belongs to the requested regatta
            if (!manifest.getRegattaId().equals(regattaId)) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Manifest not found in this regatta"))
                    .build();
            }
            
            return Response.ok(new LineScanManifestResponse(manifest)).build();
            
        } catch (Exception e) {
            LOG.error("Error retrieving manifest", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Internal error"))
                .build();
        }
    }
    
    /**
     * Simple error response DTO.
     */
    public static class ErrorResponse {
        private final String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
        
        public String getError() {
            return error;
        }
    }
}
