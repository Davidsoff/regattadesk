package com.regattadesk.linescan.api;

import com.regattadesk.linescan.LineScanTileService;
import com.regattadesk.linescan.MinioStorageAdapter;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * REST resource for line-scan tile operations.
 * 
 * Implements endpoints:
 * - PUT /api/v1/regattas/{regatta_id}/line_scan/tiles/{tile_id} (OperatorTokenAuth)
 * - GET /api/v1/regattas/{regatta_id}/line_scan/tiles/{tile_id} (OperatorTokenAuth or StaffProxyAuth)
 */
@Path("/api/v1/regattas/{regatta_id}/line_scan/tiles/{tile_id}")
public class LineScanTileResource {
    
    private static final Logger LOG = Logger.getLogger(LineScanTileResource.class);
    
    private final LineScanTileService tileService;
    
    @Inject
    public LineScanTileResource(LineScanTileService tileService) {
        this.tileService = tileService;
    }
    
    /**
     * Upload or replace a line-scan tile.
     * Auth: OperatorTokenAuth only (via x_operator_token header)
     */
    @PUT
    @Consumes({"image/webp", "image/png", MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadTile(
            @PathParam("regatta_id") UUID regattaId,
            @PathParam("tile_id") String tileId,
            @HeaderParam("x_operator_token") String operatorToken,
            @HeaderParam("Content-Type") String contentType,
            byte[] tileData) {
        
        // Auth check: operator token required for upload
        if (operatorToken == null || operatorToken.isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new OperationResult("error", "Missing or invalid operator token"))
                .build();
        }
        
        // Validate content type
        if (contentType == null || 
            (!contentType.equals("image/webp") && !contentType.equals("image/png"))) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new OperationResult("error", "Content-Type must be image/webp or image/png"))
                .build();
        }
        
        // Validate tile data
        if (tileData == null || tileData.length == 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new OperationResult("error", "Tile data is required"))
                .build();
        }
        
        try {
            tileService.storeTile(regattaId, tileId, tileData, contentType);
            
            return Response.ok(new OperationResult("success", "Tile stored successfully"))
                .build();
                
        } catch (LineScanTileService.TileNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new OperationResult("error", e.getMessage()))
                .build();
        } catch (MinioStorageAdapter.MinioStorageException e) {
            LOG.error("MinIO storage error during tile upload", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new OperationResult("error", "Storage error"))
                .build();
        } catch (Exception e) {
            LOG.error("Unexpected error during tile upload", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new OperationResult("error", "Internal error"))
                .build();
        }
    }
    
    /**
     * Download a line-scan tile.
     * Auth: OperatorTokenAuth or StaffProxyAuth (via x_operator_token or forwarded headers)
     */
    @GET
    @Produces({"image/webp", "image/png", MediaType.APPLICATION_OCTET_STREAM})
    public Response downloadTile(
            @PathParam("regatta_id") UUID regattaId,
            @PathParam("tile_id") String tileId,
            @HeaderParam("x_operator_token") String operatorToken,
            @HeaderParam("x_forwarded_user") String forwardedUser) {
        
        // Auth check: either operator token or staff proxy auth
        if ((operatorToken == null || operatorToken.isBlank()) && 
            (forwardedUser == null || forwardedUser.isBlank())) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity("Authentication required")
                .type(MediaType.TEXT_PLAIN)
                .build();
        }
        
        try {
            MinioStorageAdapter.TileData tileData = tileService.retrieveTile(regattaId, tileId);
            
            return Response.ok(tileData.getData())
                .type(tileData.getContentType())
                .build();
                
        } catch (LineScanTileService.TileNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity("Tile not found")
                .type(MediaType.TEXT_PLAIN)
                .build();
        } catch (MinioStorageAdapter.TileNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity("Tile data not found in storage")
                .type(MediaType.TEXT_PLAIN)
                .build();
        } catch (MinioStorageAdapter.MinioStorageException e) {
            LOG.error("MinIO storage error during tile download", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Storage error")
                .type(MediaType.TEXT_PLAIN)
                .build();
        } catch (Exception e) {
            LOG.error("Unexpected error during tile download", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Internal error")
                .type(MediaType.TEXT_PLAIN)
                .build();
        }
    }
    
    /**
     * Simple operation result DTO.
     */
    public static class OperationResult {
        private final String status;
        private final String message;
        
        public OperationResult(String status, String message) {
            this.status = status;
            this.message = message;
        }
        
        public String getStatus() {
            return status;
        }
        
        public String getMessage() {
            return message;
        }
    }
}
