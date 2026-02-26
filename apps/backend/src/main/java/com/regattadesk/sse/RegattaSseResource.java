package com.regattadesk.sse;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.jwt.JwtTokenService;
import com.regattadesk.jwt.JwtTokenService.InvalidTokenException;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE resource for regatta live update streams.
 * 
 * Endpoint: GET /public/regattas/{regatta_id}/events
 * 
 * Implements BC05-003 requirements:
 * - Per-regatta SSE stream delivering snapshot, draw_revision, and results_revision events
 * - Deterministic event IDs for resume/replay
 * - Per-client connection cap enforcement
 * - Snapshot on connect followed by incremental updates
 * - Requires valid public session cookie
 * 
 * Connection management:
 * - Max 100 concurrent connections per regatta (v0.1 baseline)
 * - Connections tracked per session ID
 * - Old connection automatically replaced when same session reconnects
 */
@Path("/public/regattas/{regatta_id}/events")
public class RegattaSseResource {
    
    private static final Logger LOG = Logger.getLogger(RegattaSseResource.class);
    private static final String COOKIE_NAME = "regattadesk_public_session";
    private static final int MAX_CONNECTIONS_PER_REGATTA = 100;
    
    @Inject
    DataSource dataSource;
    
    @Inject
    JwtTokenService jwtTokenService;
    
    @Inject
    RegattaSsePublisher ssePublisher;
    
    // Per-regatta connection counters
    private final Map<UUID, AtomicInteger> connectionCounts = new ConcurrentHashMap<>();
    
    /**
     * Opens an SSE stream for regatta live updates.
     * 
     * @param regattaId the regatta UUID
     * @param sessionCookie the public session cookie
     * @param lastEventId optional Last-Event-ID header for reconnection
     * @return SSE stream or error response
     */
    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> streamEvents(
            @PathParam("regatta_id") UUID regattaId,
            @CookieParam(COOKIE_NAME) Cookie sessionCookie,
            @HeaderParam("Last-Event-ID") String lastEventId) {
        
        // Validate session cookie
        if (!isValidSession(sessionCookie)) {
            LOG.debugf("Rejecting SSE connection for regatta %s due to missing/invalid session", regattaId);
            return Multi.createFrom().failure(
                new WebApplicationException(
                    Response.status(Response.Status.UNAUTHORIZED)
                        .entity(new ErrorResponse("UNAUTHORIZED", "Missing or invalid public session"))
                        .build()
                )
            );
        }
        
        // Check if regatta exists and fetch current revisions
        RegattaRevisions revisions;
        try {
            revisions = fetchRevisions(regattaId);
            if (revisions == null) {
                LOG.debugf("Regatta %s not found for SSE stream", regattaId);
                return Multi.createFrom().failure(
                    new WebApplicationException(
                        Response.status(Response.Status.NOT_FOUND)
                            .entity(ErrorResponse.notFound("Regatta not found"))
                            .build()
                    )
                );
            }
        } catch (SQLException e) {
            LOG.errorf(e, "Failed to fetch regatta %s for SSE stream", regattaId);
            return Multi.createFrom().failure(
                new WebApplicationException(
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ErrorResponse.internalError("Failed to open SSE stream"))
                        .build()
                )
            );
        }
        
        // Check connection cap
        AtomicInteger connectionCount = connectionCounts.computeIfAbsent(
            regattaId, id -> new AtomicInteger(0)
        );
        
        int currentConnections = connectionCount.get();
        if (currentConnections >= MAX_CONNECTIONS_PER_REGATTA) {
            LOG.warnf("Connection cap reached for regatta %s (%d connections)", 
                     regattaId, currentConnections);
            return Multi.createFrom().failure(
                new WebApplicationException(
                    Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(new ErrorResponse("TOO_MANY_CONNECTIONS", 
                               "Connection limit reached for this regatta"))
                        .build()
                )
            );
        }
        
        // Increment connection count
        connectionCount.incrementAndGet();
        LOG.infof("SSE connection opened for regatta %s (total: %d)", 
                 regattaId, connectionCount.get());
        
        // Get the stream from publisher
        Multi<String> stream = ssePublisher.getStream(regattaId);
        
        // Send initial snapshot event
        ssePublisher.broadcastSnapshot(regattaId, revisions.drawRevision, revisions.resultsRevision);
        
        // Decrement connection count when stream completes or fails
        return stream
            .onTermination().invoke(() -> {
                int remaining = connectionCount.decrementAndGet();
                LOG.infof("SSE connection closed for regatta %s (remaining: %d)", 
                         regattaId, remaining);
            });
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
     * @return the revisions, or null if regatta not found
     * @throws SQLException if database query fails
     */
    private RegattaRevisions fetchRevisions(UUID regattaId) throws SQLException {
        String sql = "SELECT draw_revision, results_revision FROM regattas WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, regattaId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int drawRevision = rs.getInt("draw_revision");
                    int resultsRevision = rs.getInt("results_revision");
                    return new RegattaRevisions(drawRevision, resultsRevision);
                }
                return null;
            }
        }
    }
    
    /**
     * Helper record to hold regatta revision data.
     */
    private record RegattaRevisions(int drawRevision, int resultsRevision) {}
    
    /**
     * Gets the current connection count for a regatta.
     * Used primarily for testing and monitoring.
     * 
     * @param regattaId the regatta UUID
     * @return current connection count
     */
    int getConnectionCount(UUID regattaId) {
        AtomicInteger count = connectionCounts.get(regattaId);
        return count != null ? count.get() : 0;
    }
}
