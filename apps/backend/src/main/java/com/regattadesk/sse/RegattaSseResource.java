package com.regattadesk.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.jwt.JwtTokenService;
import com.regattadesk.jwt.JwtTokenService.InvalidTokenException;
import com.regattadesk.jwt.JwtTokenService.ValidatedToken;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.jboss.resteasy.reactive.ResponseHeader;

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
    private static final int MAX_CONNECTIONS_PER_CLIENT_PER_REGATTA = 20;
    
    @Inject
    DataSource dataSource;
    
    @Inject
    JwtTokenService jwtTokenService;
    
    @Inject
    RegattaSsePublisher ssePublisher;

    @Inject
    ObjectMapper objectMapper;
    
    // Per-client-per-regatta connection counters
    private final Map<ConnectionKey, AtomicInteger> connectionCounts = new ConcurrentHashMap<>();
    
    /**
     * Opens an SSE stream for regatta live updates.
     * 
     * @param regattaId the regatta UUID
     * @param sessionCookie the public session cookie
     * @return SSE stream or error response
     */
    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    @ResponseHeader(name = "Cache-Control", value = "no-cache")
    @ResponseHeader(name = "X-Accel-Buffering", value = "no")
    public Multi<String> streamEvents(
            @PathParam("regatta_id") UUID regattaId,
            @CookieParam(COOKIE_NAME) Cookie sessionCookie) {
        
        // Validate session cookie
        String sessionId = validateAndExtractSessionId(sessionCookie);
        if (sessionId == null) {
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
        ConnectionKey connectionKey = new ConnectionKey(regattaId, sessionId);
        AtomicInteger connectionCount = connectionCounts.computeIfAbsent(connectionKey, id -> new AtomicInteger(0));
        int currentConnections = tryIncrementWithinLimit(connectionCount, MAX_CONNECTIONS_PER_CLIENT_PER_REGATTA);
        if (currentConnections < 0) {
            int existingCount = connectionCount.get();
            LOG.warnf("Connection cap reached for regatta %s session %s (%d connections)",
                     regattaId, sessionId, existingCount);
            return Multi.createFrom().failure(
                new WebApplicationException(
                    Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(new ErrorResponse("TOO_MANY_CONNECTIONS", 
                               "Connection limit reached for this client"))
                        .build()
                )
            );
        }
        
        // Get the stream from publisher
        Multi<String> stream = ssePublisher.getStream(regattaId, true);
        
        // Prepend snapshot event before joining the broadcast stream
        String snapshotEventId = SseEventIdGenerator.generate(
            regattaId, revisions.drawRevision, revisions.resultsRevision, 0
        );
        SseEvent snapshotEvent = new SseEvent(revisions.drawRevision, revisions.resultsRevision);
        String snapshotMessage;
        try {
            String data = objectMapper.writeValueAsString(snapshotEvent);
            snapshotMessage = String.format("id: %s\nevent: snapshot\ndata: %s\n\n", 
                                          snapshotEventId, data);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to serialize snapshot event for regatta %s", regattaId);
            releaseConnection(connectionKey, connectionCount, regattaId);
            return Multi.createFrom().failure(
                new WebApplicationException(
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ErrorResponse.internalError("Failed to create snapshot"))
                        .build()
                )
            );
        }

        LOG.infof("SSE connection opened for regatta %s session %s (session total: %d)",
            regattaId, sessionId, currentConnections);

        // Subscribe to the broadcaster before emitting snapshot to avoid missing updates during connect.
        return Multi.createFrom().emitter(emitter -> {
            AtomicInteger snapshotSent = new AtomicInteger(0);
            java.util.Queue<String> pendingBeforeSnapshot = new java.util.concurrent.ConcurrentLinkedQueue<>();

            var cancellable = stream.subscribe().with(
                item -> {
                    if (snapshotSent.get() == 1) {
                        emitter.emit(item);
                    } else {
                        pendingBeforeSnapshot.offer(item);
                    }
                },
                emitter::fail
            );

            emitter.emit(snapshotMessage);
            snapshotSent.set(1);

            String buffered;
            while ((buffered = pendingBeforeSnapshot.poll()) != null) {
                emitter.emit(buffered);
            }

            emitter.onTermination(() -> {
                cancellable.cancel();
                releaseConnection(connectionKey, connectionCount, regattaId);
            });
        });
    }
    
    /**
     * Validates the session cookie.
     * 
     * @param cookie the session cookie
     * @return true if the session is valid, false otherwise
     */
    private String validateAndExtractSessionId(Cookie cookie) {
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return null;
        }
        
        try {
            ValidatedToken token = jwtTokenService.validateToken(cookie.getValue());
            return token.sessionId();
        } catch (InvalidTokenException e) {
            LOG.debug("Invalid session token: " + e.getMessage());
            return null;
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
        String sql = "SELECT draw_revision, results_revision, status FROM regattas WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, regattaId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String status = rs.getString("status");
                    if (!"published".equals(status)) {
                        return null;
                    }
                    int drawRevision = rs.getInt("draw_revision");
                    int resultsRevision = rs.getInt("results_revision");
                    return new RegattaRevisions(drawRevision, resultsRevision);
                }
                return null;
            }
        }
    }

    private int tryIncrementWithinLimit(AtomicInteger counter, int limit) {
        while (true) {
            int current = counter.get();
            if (current >= limit) {
                return -1;
            }
            if (counter.compareAndSet(current, current + 1)) {
                return current + 1;
            }
        }
    }

    private void releaseConnection(ConnectionKey key, AtomicInteger counter, UUID regattaId) {
        int remaining = counter.decrementAndGet();
        if (remaining <= 0) {
            connectionCounts.remove(key, counter);
            remaining = 0;
        }
        LOG.infof("SSE connection closed for regatta %s session %s (session remaining: %d)",
            regattaId, key.sessionId(), remaining);
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
        return connectionCounts.entrySet().stream()
            .filter(entry -> entry.getKey().regattaId().equals(regattaId))
            .mapToInt(entry -> entry.getValue().get())
            .sum();
    }

    private record ConnectionKey(UUID regattaId, String sessionId) {}
}
