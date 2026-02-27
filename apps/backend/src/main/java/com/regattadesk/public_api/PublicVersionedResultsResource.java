package com.regattadesk.public_api;

import com.regattadesk.api.dto.ErrorResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
    RegattaVersionRepository versionRepository;

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

        if (drawRevision < 0 || resultsRevision < 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest("draw and results revisions must be non-negative"))
                .build();
        }
        
        LOG.infof("Fetching results for regatta %s at version v%d-%d", 
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
            
            List<ResultRow> rows = fetchRows(regattaId, drawRevision, resultsRevision);
            ResultsResponse results = new ResultsResponse(drawRevision, resultsRevision, rows);
            
            return Response.ok(results).build();
            
        } catch (SQLException e) {
            LOG.error("Failed to fetch results for regatta " + regattaId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ErrorResponse.internalError("Failed to fetch results"))
                .build();
        }
    }
    
    /**
     * Response DTO for results endpoint.
     */
    private List<ResultRow> fetchRows(UUID regattaId, int drawRevision, int resultsRevision) throws SQLException {
        String sql = """
            SELECT entry_id, event_id, bib, crew_name, club_name, elapsed_time_ms, penalties_ms, rank,
                   status, is_provisional, is_edited, is_official
            FROM public_regatta_results
            WHERE regatta_id = ?
              AND draw_revision = ?
              AND results_revision = ?
              AND status <> 'withdrawn_before_draw'
            ORDER BY rank NULLS LAST, bib NULLS LAST, crew_name
            """;

        List<ResultRow> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            stmt.setInt(2, drawRevision);
            stmt.setInt(3, resultsRevision);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ResultRow(
                        rs.getObject("entry_id", UUID.class),
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("bib", Integer.class),
                        rs.getString("crew_name"),
                        rs.getString("club_name"),
                        rs.getObject("elapsed_time_ms", Integer.class),
                        rs.getObject("penalties_ms", Integer.class),
                        rs.getObject("rank", Integer.class),
                        rs.getString("status"),
                        rs.getBoolean("is_provisional"),
                        rs.getBoolean("is_edited"),
                        rs.getBoolean("is_official")
                    ));
                }
            }
        }

        return rows;
    }

    public record ResultsResponse(int draw_revision, int results_revision, List<ResultRow> data) {}

    public record ResultRow(
        UUID entry_id,
        UUID event_id,
        Integer bib,
        String crew_name,
        String club_name,
        Integer elapsed_time_ms,
        Integer penalties_ms,
        Integer rank,
        String status,
        boolean is_provisional,
        boolean is_edited,
        boolean is_official
    ) {}
}
