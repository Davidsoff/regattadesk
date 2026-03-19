package com.regattadesk.operator.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.operator.OperatorEvidenceWorkspaceService;
import com.regattadesk.operator.OperatorTokenService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.UUID;

@Path("/api/v1/regattas/{regatta_id}/operator/evidence_workspace")
@Produces(MediaType.APPLICATION_JSON)
public class OperatorEvidenceWorkspaceResource {

    private static final Logger LOG = Logger.getLogger(OperatorEvidenceWorkspaceResource.class);

    private final OperatorEvidenceWorkspaceService workspaceService;
    private final OperatorTokenService operatorTokenService;

    @Inject
    public OperatorEvidenceWorkspaceResource(
        OperatorEvidenceWorkspaceService workspaceService,
        OperatorTokenService operatorTokenService
    ) {
        this.workspaceService = workspaceService;
        this.operatorTokenService = operatorTokenService;
    }

    @GET
    public Response getWorkspace(
        @PathParam("regatta_id") UUID regattaId,
        @QueryParam("capture_session_id") UUID captureSessionId,
        @QueryParam("event_id") UUID eventId,
        @HeaderParam("X-Operator-Token") String operatorToken,
        @HeaderParam("X-Forwarded-User") String forwardedUser
    ) {
        if (!hasAuth(operatorToken, regattaId, forwardedUser)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("UNAUTHORIZED", "Missing or invalid authentication"))
                .build();
        }

        try {
            return Response.ok(workspaceService.getWorkspace(regattaId, captureSessionId, eventId)).build();
        } catch (OperatorEvidenceWorkspaceService.BadRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (OperatorEvidenceWorkspaceService.NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        } catch (Exception e) {
            LOG.error("Failed to build operator evidence workspace", e);
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to load operator evidence workspace"))
                .build();
        }
    }

    private boolean hasAuth(String operatorToken, UUID regattaId, String forwardedUser) {
        if (forwardedUser != null && !forwardedUser.isBlank()) {
            return true;
        }
        try {
            return operatorToken != null
                && !operatorToken.isBlank()
                && operatorTokenService.isTokenActiveForRegatta(operatorToken, regattaId);
        } catch (RuntimeException e) {
            LOG.warn("Operator token validation failed", e);
            return false;
        }
    }
}
