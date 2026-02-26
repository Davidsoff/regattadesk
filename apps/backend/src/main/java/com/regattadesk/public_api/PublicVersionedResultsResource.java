package com.regattadesk.public_api;

import com.regattadesk.api.dto.ErrorResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Versioned public results resource.
 * 
 * Serves results data under immutable versioned paths:
 * /public/v{draw}-{results}/regattas/{regatta_id}/results
 * 
 * This endpoint demonstrates BC05-002 versioned routing.
 * The cache control filter automatically applies:
 * Cache-Control: public, max-age=31536000, immutable
 */
@Path("/public/v{draw}-{results}/regattas/{regatta_id}/results")
public class PublicVersionedResultsResource {
    
    private static final Logger LOG = Logger.getLogger(PublicVersionedResultsResource.class);
    
    @Inject
    DataSource dataSource;
    
    /**
     * Get the results for a regatta at a specific version.
     * 
     * @param drawRevision the draw revision (path parameter)
     * @param resultsRevision the results revision (path parameter)
     * @param regattaId the regatta UUID
     * @return the results data
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getResults(
            @PathParam("draw") int drawRevision,
            @PathParam("results") int resultsRevision,
            @PathParam("regatta_id") UUID regattaId) {
        
        LOG.infof("Fetching results for regatta %s at version v%d-%d", 
            regattaId, drawRevision, resultsRevision);
        
        try {
            // Verify the regatta exists and check current versions
            RegattaVersionInfo versionInfo = fetchRegattaVersionInfo(regattaId);
            
            if (versionInfo == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Regatta not found"))
                    .build();
            }
            
            // Check if requested version matches current version
            // In a full implementation, we'd serve version-specific data
            // For now, we just verify the regatta exists and return a simple response
            
            ResultsResponse results = new ResultsResponse(
                regattaId,
                drawRevision,
                resultsRevision,
                "Results data would appear here"
            );
            
            return Response.ok(results).build();
            
        } catch (SQLException e) {
            LOG.error("Failed to fetch results for regatta " + regattaId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ErrorResponse.internalError("Failed to fetch results"))
                .build();
        }
    }
    
    /**
     * Fetches version information for a regatta.
     */
    private RegattaVersionInfo fetchRegattaVersionInfo(UUID regattaId) throws SQLException {
        String sql = "SELECT draw_revision, results_revision FROM regattas WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, regattaId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new RegattaVersionInfo(
                        rs.getInt("draw_revision"),
                        rs.getInt("results_revision")
                    );
                }
                return null;
            }
        }
    }
    
    /**
     * Response DTO for results endpoint.
     */
    public record ResultsResponse(
        UUID regattaId,
        int drawRevision,
        int resultsRevision,
        String resultsData
    ) {}
    
    /**
     * Internal DTO for regatta version information.
     */
    private record RegattaVersionInfo(int drawRevision, int resultsRevision) {}
}
