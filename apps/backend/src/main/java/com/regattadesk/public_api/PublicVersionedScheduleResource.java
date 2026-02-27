package com.regattadesk.public_api;

import com.regattadesk.api.dto.ErrorResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.List;
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

    @Inject
    PublicScheduleRepository scheduleRepository;
    
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

        if (drawRevision < 0 || resultsRevision < 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest("draw and results revisions must be non-negative"))
                .build();
        }
        
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

            if (drawRevision != versionInfo.drawRevision()
                    || resultsRevision != versionInfo.resultsRevision()) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Requested version not found for regatta"))
                    .build();
            }

            List<ScheduleRowResponse> rows = scheduleRepository.fetchSchedule(regattaId, drawRevision)
                .stream()
                .map(row -> new ScheduleRowResponse(
                    row.entryId(),
                    row.eventId(),
                    row.bib(),
                    row.lane(),
                    row.scheduledStartTime(),
                    row.crewName(),
                    row.clubName(),
                    row.status()
                ))
                .toList();
            
            ScheduleResponse schedule = new ScheduleResponse(
                drawRevision,
                resultsRevision,
                rows
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
        @JsonProperty("draw_revision") int drawRevision,
        @JsonProperty("results_revision") int resultsRevision,
        @JsonProperty("data") List<ScheduleRowResponse> data
    ) {}

    public record ScheduleRowResponse(
        @JsonProperty("entry_id") UUID entryId,
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("bib") Integer bib,
        @JsonProperty("lane") Integer lane,
        @JsonProperty("scheduled_start_time") java.time.OffsetDateTime scheduledStartTime,
        @JsonProperty("crew_name") String crewName,
        @JsonProperty("club_name") String clubName,
        @JsonProperty("status") String status
    ) {}
}
