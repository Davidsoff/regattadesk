package com.regattadesk.operator.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.operator.CaptureSession;
import com.regattadesk.operator.CaptureSessionService;
import com.regattadesk.operator.OperatorTokenService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST resource for capture session lifecycle management.
 *
 * <p>Implements endpoints:
 * <ul>
 *   <li>POST   /api/v1/regattas/{regatta_id}/operator/capture_sessions — start session</li>
 *   <li>GET    /api/v1/regattas/{regatta_id}/operator/capture_sessions — list sessions</li>
 *   <li>GET    /api/v1/regattas/{regatta_id}/operator/capture_sessions/{capture_session_id} — get session</li>
 *   <li>POST   /api/v1/regattas/{regatta_id}/operator/capture_sessions/{capture_session_id}/sync_state — update sync state</li>
 *   <li>POST   /api/v1/regattas/{regatta_id}/operator/capture_sessions/{capture_session_id}/close — close session</li>
 * </ul>
 *
 * <p>Authentication: operator token via {@code X-Operator-Token} header for create/update/close;
 * operator token or staff auth ({@code X-Forwarded-User}) for read operations.
 */
@Path("/api/v1/regattas/{regatta_id}/operator/capture_sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CaptureSessionResource {

    private static final Logger LOG = Logger.getLogger(CaptureSessionResource.class);

    private final CaptureSessionService captureSessionService;
    private final OperatorTokenService operatorTokenService;

    @Inject
    public CaptureSessionResource(
            CaptureSessionService captureSessionService,
            OperatorTokenService operatorTokenService) {
        this.captureSessionService = captureSessionService;
        this.operatorTokenService = operatorTokenService;
    }

    /**
     * Start (create) a new capture session.
     *
     * <p>Auth: OperatorTokenAuth only — requires {@code X-Operator-Token} header.
     */
    @POST
    @Operation(summary = "Start Session")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Created",
                    content = @Content(schema = @Schema(implementation = CaptureSessionResponse.class))),
            @APIResponse(responseCode = "400", description = "Bad Request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response startSession(
            @PathParam("regatta_id") UUID regattaId,
            @HeaderParam("X-Operator-Token") String operatorToken,
            @Valid @NotNull(message = "request body is required") CaptureSessionCreateRequest request) {

        if (!isValidOperatorToken(operatorToken, regattaId, request.getStation())) {
            return unauthorizedResponse();
        }

        try {
            CaptureSession.SessionType sessionType =
                    CaptureSession.SessionType.valueOf(request.getSessionType());

            CaptureSession session = captureSessionService.startSession(
                    regattaId,
                    request.getBlockId(),
                    request.getStation(),
                    request.getDeviceId(),
                    sessionType,
                    request.getServerTimeAtStart(),
                    request.getDeviceMonotonicOffsetMs(),
                    request.getFps(),
                    "operator"
            );

            return Response.status(Response.Status.CREATED)
                    .entity(new CaptureSessionResponse(session))
                    .build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.badRequest(e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.error("Failed to start capture session", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError("Internal error"))
                    .build();
        }
    }

    /**
     * List capture sessions for a regatta.
     *
     * <p>Auth: OperatorTokenAuth or StaffProxyAuth.
     *
     * <p>Query params:
     * <ul>
     *   <li>{@code block_id} — optional, filter by block</li>
     *   <li>{@code station} — optional, filter by station (only applied when state=open)</li>
     *   <li>{@code state} — optional, {@code open} filters to open sessions only</li>
     * </ul>
     */
    @GET
    @Operation(summary = "List Sessions")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CaptureSessionListResponse.class))),
            @APIResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response listSessions(
            @PathParam("regatta_id") UUID regattaId,
            @HeaderParam("X-Operator-Token") String operatorToken,
            @HeaderParam("X-Forwarded-User") String forwardedUser,
            @QueryParam("block_id") UUID blockId,
            @QueryParam("station") String station,
            @QueryParam("state") String state) {

        if (!hasAuth(operatorToken, regattaId, forwardedUser)) {
            return unauthorizedResponse();
        }

        try {
            List<CaptureSession> sessions;

            if ("open".equals(state) && blockId != null) {
                sessions = captureSessionService.listSessionsByBlock(regattaId, blockId).stream()
                        .filter(CaptureSession::isOpen)
                        .filter(session -> station == null || station.isBlank() || station.equals(session.getStation()))
                        .collect(Collectors.toList());
            } else if ("open".equals(state)) {
                sessions = captureSessionService.listOpenSessions(regattaId, station);
            } else if (blockId != null) {
                sessions = captureSessionService.listSessionsByBlock(regattaId, blockId);
            } else {
                sessions = captureSessionService.listSessions(regattaId);
            }

            List<CaptureSessionResponse> responses = sessions.stream()
                    .map(CaptureSessionResponse::new)
                    .collect(Collectors.toList());

            return Response.ok(new CaptureSessionListResponse(responses)).build();

        } catch (Exception e) {
            LOG.error("Failed to list capture sessions", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError("Internal error"))
                    .build();
        }
    }

    /**
     * Get a specific capture session.
     *
     * <p>Auth: OperatorTokenAuth or StaffProxyAuth.
     */
    @GET
    @Path("/{capture_session_id}")
    @Operation(summary = "Get Session")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CaptureSessionResponse.class))),
            @APIResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "404", description = "Not Found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getSession(
            @PathParam("regatta_id") UUID regattaId,
            @PathParam("capture_session_id") UUID captureSessionId,
            @HeaderParam("X-Operator-Token") String operatorToken,
            @HeaderParam("X-Forwarded-User") String forwardedUser) {

        if (!hasAuth(operatorToken, regattaId, forwardedUser)) {
            return unauthorizedResponse();
        }

        Optional<CaptureSession> sessionOpt = captureSessionService.getSession(captureSessionId, regattaId);
        if (sessionOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Capture session not found"))
                    .build();
        }

        return Response.ok(new CaptureSessionResponse(sessionOpt.get())).build();
    }

    /**
     * Update the sync state of an open capture session.
     *
     * <p>Auth: OperatorTokenAuth only — requires {@code X-Operator-Token} header.
     */
    @POST
    @Path("/{capture_session_id}/sync_state")
    @Operation(summary = "Update Sync State")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CaptureSessionResponse.class))),
            @APIResponse(responseCode = "400", description = "Bad Request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "404", description = "Not Found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "409", description = "Conflict",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response updateSyncState(
            @PathParam("regatta_id") UUID regattaId,
            @PathParam("capture_session_id") UUID captureSessionId,
            @HeaderParam("X-Operator-Token") String operatorToken,
            @Valid @NotNull(message = "request body is required") CaptureSessionSyncStateRequest request) {

        Optional<CaptureSession> sessionOpt = captureSessionService.getSession(captureSessionId, regattaId);
        if (sessionOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Capture session not found"))
                    .build();
        }

        CaptureSession session = sessionOpt.get();
        if (!isValidOperatorToken(operatorToken, regattaId, session.getStation())) {
            return unauthorizedResponse();
        }

        try {
            CaptureSession updated = captureSessionService.updateSyncState(
                    captureSessionId,
                    regattaId,
                    request.getIsSynced(),
                    request.isDriftExceededThreshold(),
                    request.getUnsyncedReason(),
                    "operator"
            );

            return Response.ok(new CaptureSessionResponse(updated)).build();

        } catch (CaptureSessionService.CaptureSessionNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Capture session not found"))
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ErrorResponse.conflict(e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.error("Failed to update sync state", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError("Internal error"))
                    .build();
        }
    }

    /**
     * Update device control parameters for an open capture session.
     *
     * <p>Auth: OperatorTokenAuth only — requires {@code X-Operator-Token} header.
     * Only available when device_control_mode is not "unsupported".
     */
    @POST
    @Path("/{capture_session_id}/device_controls")
    @Operation(summary = "Update Device Controls")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CaptureSessionResponse.class))),
            @APIResponse(responseCode = "400", description = "Bad Request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "404", description = "Not Found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "409", description = "Conflict",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response updateDeviceControls(
            @PathParam("regatta_id") UUID regattaId,
            @PathParam("capture_session_id") UUID captureSessionId,
            @HeaderParam("X-Operator-Token") String operatorToken,
            @Valid @NotNull(message = "request body is required") DeviceControlRequest request) {

        Optional<CaptureSession> sessionOpt = captureSessionService.getSession(captureSessionId, regattaId);
        if (sessionOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Capture session not found"))
                    .build();
        }

        CaptureSession session = sessionOpt.get();
        if (!isValidOperatorToken(operatorToken, regattaId, session.getStation())) {
            return unauthorizedResponse();
        }

        if (session.isClosed()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ErrorResponse.conflict("Cannot update device controls of a closed session"))
                    .build();
        }

        if (!"line-scan".equals(session.getStation())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ErrorResponse.conflict("Device controls not supported for this station"))
                    .build();
        }

        try {
            CaptureSession updated = captureSessionService.updateDeviceControls(
                    captureSessionId,
                    regattaId,
                    request.getScanLinePosition(),
                    request.getCaptureRate(),
                    "operator"
            );

            return Response.ok(new CaptureSessionResponse(updated)).build();

        } catch (CaptureSessionService.CaptureSessionNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Capture session not found"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.badRequest(e.getMessage()))
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ErrorResponse.conflict(e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.error("Failed to update device controls", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError("Internal error"))
                    .build();
        }
    }

    /**
     * Close a capture session.
     *
     * <p>Auth: OperatorTokenAuth only — requires {@code X-Operator-Token} header.
     * Closing an already-closed session returns 409 Conflict.
     */
    @POST
    @Path("/{capture_session_id}/close")
    @Operation(summary = "Close Session")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CaptureSessionResponse.class))),
            @APIResponse(responseCode = "400", description = "Bad Request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "404", description = "Not Found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "409", description = "Conflict",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response closeSession(
            @PathParam("regatta_id") UUID regattaId,
            @PathParam("capture_session_id") UUID captureSessionId,
            @HeaderParam("X-Operator-Token") String operatorToken,
            CaptureSessionCloseRequest request) {

        Optional<CaptureSession> sessionOpt = captureSessionService.getSession(captureSessionId, regattaId);
        if (sessionOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Capture session not found"))
                    .build();
        }

        CaptureSession session = sessionOpt.get();
        if (!isValidOperatorToken(operatorToken, regattaId, session.getStation())) {
            return unauthorizedResponse();
        }

        String closeReason = (request != null) ? request.getCloseReason() : null;

        try {
            CaptureSession closed = captureSessionService.closeSession(
                    captureSessionId,
                    regattaId,
                    closeReason,
                    "operator"
            );

            return Response.ok(new CaptureSessionResponse(closed)).build();

        } catch (CaptureSessionService.CaptureSessionNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Capture session not found"))
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ErrorResponse.conflict(e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.error("Failed to close capture session", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError("Internal error"))
                    .build();
        }
    }

    // ---- Auth helpers ----------------------------------------------------

    /**
     * Validates operator token for a regatta.
     *
     * <p>When {@code station} is non-null, the token must also be scoped to that
     * station (used when starting a new session). When {@code station} is null,
     * only regatta scope is verified.
     */
    private boolean isValidOperatorToken(String operatorToken, UUID regattaId, String station) {
        try {
            if (operatorToken == null || operatorToken.isBlank()) {
                return false;
            }
            if (station != null && !station.isBlank()) {
                // Full scope check including station.
                OperatorTokenService.TokenValidationResult result =
                        operatorTokenService.validateToken(operatorToken, regattaId, station, null);
                return result.isValid();
            } else {
                // Regatta-only scope check (station-agnostic).
                return operatorTokenService.isTokenActiveForRegatta(operatorToken, regattaId);
            }
        } catch (RuntimeException e) {
            LOG.warn("Operator token validation failed", e);
            return false;
        }
    }

    private boolean hasAuth(String operatorToken, UUID regattaId, String forwardedUser) {
        if (forwardedUser != null && !forwardedUser.isBlank()) {
            return true;
        }
        return isValidOperatorToken(operatorToken, regattaId, null);
    }

    private Response unauthorizedResponse() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("UNAUTHORIZED", "Missing or invalid authentication"))
                .build();
    }
}
