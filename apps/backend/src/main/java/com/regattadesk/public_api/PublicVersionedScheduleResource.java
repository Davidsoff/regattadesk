package com.regattadesk.public_api;

import com.regattadesk.api.dto.ErrorResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Versioned public schedule resource.
 * 
 * Serves schedule data under immutable versioned paths:
 * /public/v{draw}-{results}/regattas/{regatta_id}/schedule
 * 
 * This endpoint demonstrates BC05-002 versioned routing.
 * The cache control filter automatically applies:
 * Cache-Control: public, max-age=31536000, immutable
 */
@Path("/public/v{draw}-{results}/regattas/{regatta_id}/schedule")
public class PublicVersionedScheduleResource {
    
    private static final Logger LOG = Logger.getLogger(PublicVersionedScheduleResource.class);
    
    @Inject
    RegattaVersionRepository versionRepository;
    
    /**
     * Get the schedule for a regatta at a specific version.
     * 
     * @param drawRevision the draw revision (path parameter)
     * @param resultsRevision the results revision (path parameter)
     * @param regattaId the regatta UUID
     * @return the schedule data
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSchedule(
            @PathParam("draw") int drawRevision,
            @PathParam("results") int resultsRevision,
            @PathParam("regatta_id") UUID regattaId) {
        
        LOG.infof("Fetching schedule for regatta %s at version v%d-%d", 
            regattaId, drawRevision, resultsRevision);
        
        try {
            // Verify the regatta exists and check current versions
            RegattaVersionRepository.VersionInfo versionInfo = versionRepository.fetchVersionInfo(regattaId);
            
            if (versionInfo == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Regatta not found"))
                    .build();
            }
            
            // Check if requested version matches current version
            // In a full implementation, we'd serve version-specific data
            // For now, we just verify the regatta exists and return a simple response
            
            ScheduleResponse schedule = new ScheduleResponse(
                regattaId,
                drawRevision,
                resultsRevision,
                "Schedule data would appear here"
            );
            
            return Response.ok(schedule).build();
            
        } catch (SQLException e) {
            LOG.error("Failed to fetch schedule for regatta " + regattaId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ErrorResponse.internalError("Failed to fetch schedule"))
                .build();
        }
    }
    
    /**
     * Response DTO for schedule endpoint.
     */
    public record ScheduleResponse(
        UUID regattaId,
        int drawRevision,
        int resultsRevision,
        String scheduleData
    ) {}
}
