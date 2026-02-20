package com.regattadesk.linescan.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.linescan.*;
import com.regattadesk.operator.OperatorTokenService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;
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
    private static final String LINE_SCAN_STATION = "line-scan";
    
    private final LineScanManifestService manifestService;
    private final OperatorTokenService operatorTokenService;
    
    @Inject
    public LineScanManifestResource(
            LineScanManifestService manifestService,
            OperatorTokenService operatorTokenService) {
        this.manifestService = manifestService;
        this.operatorTokenService = operatorTokenService;
    }
    
    /**
     * Upsert a line-scan manifest.
     * Auth: OperatorTokenAuth only (via x_operator_token header)
     */
    @POST
    public Response upsertManifest(
            @PathParam("regatta_id") UUID regattaId,
            @HeaderParam("X-Operator-Token") String operatorToken,
            @Valid @NotNull(message = "request body is required") LineScanManifestUpsertRequest request) {
        
        if (!isValidOperatorToken(operatorToken, regattaId)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("UNAUTHORIZED", "Missing or invalid operator token"))
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
                .entity(ErrorResponse.internalError("Internal storage error"))
                .build();
        } catch (Exception e) {
            LOG.error("Unexpected error during manifest upsert", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ErrorResponse.internalError("Internal error"))
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
            @HeaderParam("X-Operator-Token") String operatorToken,
            @HeaderParam("X-Forwarded-User") String forwardedUser) {
        
        boolean hasStaffAuth = forwardedUser != null && !forwardedUser.isBlank();
        if (!hasStaffAuth && !isValidOperatorToken(operatorToken, regattaId)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("UNAUTHORIZED", "Authentication required"))
                .build();
        }
        
        try {
            Optional<LineScanManifest> optionalManifest = manifestService.getManifest(manifestId);
            if (optionalManifest.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Manifest not found"))
                    .build();
            }
            LineScanManifest manifest = optionalManifest.get();
            
            // Verify manifest belongs to the requested regatta
            if (!manifest.getRegattaId().equals(regattaId)) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Manifest not found in this regatta"))
                    .build();
            }
            
            return Response.ok(new LineScanManifestResponse(manifest)).build();
            
        } catch (Exception e) {
            LOG.error("Error retrieving manifest", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ErrorResponse.internalError("Internal error"))
                .build();
        }
    }

    private boolean isValidOperatorToken(String operatorToken, UUID regattaId) {
        try {
            return operatorToken != null
                && !operatorToken.isBlank()
                && operatorTokenService.validateToken(operatorToken, regattaId, LINE_SCAN_STATION, null).isValid();
        } catch (RuntimeException e) {
            LOG.warn("Operator token validation failed", e);
            return false;
        }
    }
}
