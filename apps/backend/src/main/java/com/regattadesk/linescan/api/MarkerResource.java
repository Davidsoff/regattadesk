package com.regattadesk.linescan.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.linescan.TimingMarker;
import com.regattadesk.linescan.TimingMarkerService;
import com.regattadesk.operator.OperatorTokenService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

/**
 * Marker management API for BC06-004.
 */
@Path("/api/v1/regattas/{regatta_id}/operator/markers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MarkerResource {

    private static final Logger LOG = Logger.getLogger(MarkerResource.class);
    private static final String LINE_SCAN_STATION = "line-scan";

    private final TimingMarkerService markerService;
    private final OperatorTokenService operatorTokenService;

    @Inject
    public MarkerResource(TimingMarkerService markerService, OperatorTokenService operatorTokenService) {
        this.markerService = markerService;
        this.operatorTokenService = operatorTokenService;
    }

    @GET
    public Response list(
        @PathParam("regatta_id") UUID regattaId,
        @QueryParam("capture_session_id") UUID captureSessionId,
        @HeaderParam("X-Operator-Token") String operatorToken
    ) {
        if (!isValidOperatorToken(operatorToken, regattaId)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("UNAUTHORIZED", "Missing or invalid operator token"))
                .build();
        }

        List<MarkerResponse> data = markerService.listByRegatta(regattaId, captureSessionId).stream()
            .map(MarkerResponse::from)
            .toList();

        return Response.ok(new MarkerListResponse(data)).build();
    }

    @POST
    public Response create(
        @PathParam("regatta_id") UUID regattaId,
        @HeaderParam("X-Operator-Token") String operatorToken,
        @Valid @NotNull(message = "request body is required") MarkerCreateRequest request
    ) {
        if (!isValidOperatorToken(operatorToken, regattaId)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("UNAUTHORIZED", "Missing or invalid operator token"))
                .build();
        }

        try {
            TimingMarker marker = markerService.create(
                regattaId,
                request.captureSessionId(),
                request.frameOffset(),
                request.timestampMs(),
                request.tileId(),
                request.tileX(),
                request.tileY()
            );
            return Response.status(Response.Status.CREATED).entity(MarkerResponse.from(marker)).build();
        } catch (TimingMarkerService.NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        } catch (Exception e) {
            LOG.error("Failed to create marker", e);
            return Response.serverError().entity(ErrorResponse.internalError("Failed to create marker")).build();
        }
    }

    @PATCH
    @Path("/{marker_id}")
    public Response update(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("marker_id") UUID markerId,
        @HeaderParam("X-Operator-Token") String operatorToken,
        @Valid @NotNull(message = "request body is required") MarkerUpdateRequest request
    ) {
        if (!isValidOperatorToken(operatorToken, regattaId)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("UNAUTHORIZED", "Missing or invalid operator token"))
                .build();
        }

        try {
            TimingMarker marker = markerService.update(
                regattaId,
                markerId,
                request.frameOffset(),
                request.timestampMs(),
                request.tileId(),
                request.tileX(),
                request.tileY(),
                request.isApproved()
            );
            return Response.ok(MarkerResponse.from(marker)).build();
        } catch (TimingMarkerService.NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflict(e.getMessage()))
                .build();
        } catch (Exception e) {
            LOG.error("Failed to update marker", e);
            return Response.serverError().entity(ErrorResponse.internalError("Failed to update marker")).build();
        }
    }

    @DELETE
    @Path("/{marker_id}")
    public Response delete(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("marker_id") UUID markerId,
        @HeaderParam("X-Operator-Token") String operatorToken
    ) {
        if (!isValidOperatorToken(operatorToken, regattaId)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("UNAUTHORIZED", "Missing or invalid operator token"))
                .build();
        }

        try {
            markerService.delete(regattaId, markerId);
            return Response.noContent().build();
        } catch (TimingMarkerService.NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflict(e.getMessage()))
                .build();
        } catch (Exception e) {
            LOG.error("Failed to delete marker", e);
            return Response.serverError().entity(ErrorResponse.internalError("Failed to delete marker")).build();
        }
    }

    @POST
    @Path("/{marker_id}/link")
    public Response link(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("marker_id") UUID markerId,
        @HeaderParam("X-Operator-Token") String operatorToken,
        @Valid @NotNull(message = "request body is required") MarkerLinkRequest request
    ) {
        if (!isValidOperatorToken(operatorToken, regattaId)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("UNAUTHORIZED", "Missing or invalid operator token"))
                .build();
        }

        try {
            TimingMarker marker = markerService.link(regattaId, markerId, request.entryId());
            return Response.ok(MarkerResponse.from(marker)).build();
        } catch (TimingMarkerService.NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflict(e.getMessage()))
                .build();
        } catch (Exception e) {
            LOG.error("Failed to link marker", e);
            return Response.serverError().entity(ErrorResponse.internalError("Failed to link marker")).build();
        }
    }

    @POST
    @Path("/{marker_id}/unlink")
    public Response unlink(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("marker_id") UUID markerId,
        @HeaderParam("X-Operator-Token") String operatorToken
    ) {
        if (!isValidOperatorToken(operatorToken, regattaId)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("UNAUTHORIZED", "Missing or invalid operator token"))
                .build();
        }

        try {
            TimingMarker marker = markerService.unlink(regattaId, markerId);
            return Response.ok(MarkerResponse.from(marker)).build();
        } catch (TimingMarkerService.NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflict(e.getMessage()))
                .build();
        } catch (Exception e) {
            LOG.error("Failed to unlink marker", e);
            return Response.serverError().entity(ErrorResponse.internalError("Failed to unlink marker")).build();
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
