package com.regattadesk.public_api;

import com.regattadesk.jwt.JwtTokenService;
import com.regattadesk.jwt.JwtTokenService.InvalidTokenException;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * REST resource for public regatta versions endpoint.
 * 
 * This endpoint returns the current draw and results revisions for a regatta.
 * Requires a valid anonymous session cookie. Returns 401 if session is missing or invalid,
 * triggering the client bootstrap flow: /versions -> 401 -> /public/session -> retry /versions.
 */
@Path("/public/regattas/{regatta_id}/versions")
public class PublicVersionsResource {
    private static final Logger LOG = Logger.getLogger(PublicVersionsResource.class);
    
    private static final String COOKIE_NAME = "regattadesk_public_session";
    
    @Inject
    DataSource dataSource;
    
    @Inject
    JwtTokenService jwtTokenService;
    
    /**
     * Get current draw and results revisions for a regatta.
     * 
     * @param regattaId the regatta UUID
     * @param sessionCookie the public session cookie
     * @return 200 with versions payload, or 401 if session is missing/invalid
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getVersions(
            @PathParam("regatta_id") UUID regattaId,
            @CookieParam(COOKIE_NAME) Cookie sessionCookie) {
        
        // Validate session cookie
        if (!isValidSession(sessionCookie)) {
            LOG.debug("Rejecting versions request due to missing or invalid session cookie");
            return Response.status(Response.Status.UNAUTHORIZED)
                .header("Cache-Control", "no-store, must-revalidate")
                .build();
        }
        
        // Fetch versions from database
        try {
            PublicVersionsResponse versions = fetchVersions(regattaId);
            if (versions == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .header("Cache-Control", "no-store, must-revalidate")
                    .build();
            }
            
            return Response.ok(versions)
                .header("Cache-Control", "no-store, must-revalidate")
                .build();
                
        } catch (SQLException e) {
            LOG.error("Failed to fetch versions for regatta " + regattaId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .header("Cache-Control", "no-store, must-revalidate")
                .build();
        }
    }
    
    /**
     * Validates the session cookie.
     * 
     * @param cookie the session cookie
     * @return true if the session is valid, false otherwise
     */
    private boolean isValidSession(Cookie cookie) {
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return false;
        }
        
        try {
            jwtTokenService.validateToken(cookie.getValue());
            return true;
        } catch (InvalidTokenException e) {
            LOG.debug("Invalid session token: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Fetches the current draw and results revisions for a regatta.
     * 
     * @param regattaId the regatta UUID
     * @return the versions response, or null if regatta not found
     * @throws SQLException if database query fails
     */
    private PublicVersionsResponse fetchVersions(UUID regattaId) throws SQLException {
        String sql = "SELECT draw_revision, results_revision FROM regattas WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, regattaId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int drawRevision = rs.getInt("draw_revision");
                    int resultsRevision = rs.getInt("results_revision");
                    return new PublicVersionsResponse(drawRevision, resultsRevision);
                }
                return null;
            }
        }
    }
}
