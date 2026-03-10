package com.regattadesk.draw.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.regatta.RegattaWorkflowService;
import com.regattadesk.security.RequireRole;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

import static com.regattadesk.security.Role.REGATTA_ADMIN;
import static com.regattadesk.security.Role.SUPER_ADMIN;

@Path("/api/v1/regattas/{regatta_id}/draw")
@Produces(MediaType.APPLICATION_JSON)
public class DrawResource {

    @Inject
    RegattaWorkflowService regattaWorkflowService;

    @POST
    @Path("/generate")
    @Consumes(MediaType.APPLICATION_JSON)
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN})
    public Response generateDraw(@PathParam("regatta_id") UUID regattaId, GenerateDrawRequest request) {
        try {
            Long seed = request != null ? request.seed() : null;
            var state = regattaWorkflowService.generateDraw(regattaId, seed);
            return Response.ok(new GenerateDrawResponse(state.seed(), state.generated(), state.published())).build();
        } catch (IllegalArgumentException e) {
            Response.Status status = "Regatta not found".equals(e.getMessage())
                ? Response.Status.NOT_FOUND
                : Response.Status.BAD_REQUEST;
            return Response.status(status)
                .entity("Regatta not found".equals(e.getMessage())
                    ? ErrorResponse.notFound(e.getMessage())
                    : ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflict(e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/publish")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN})
    public Response publishDraw(@PathParam("regatta_id") UUID regattaId) {
        try {
            var state = regattaWorkflowService.publishDraw(regattaId);
            return Response.ok(new DrawPublicationResponse(
                state.drawRevision(),
                state.resultsRevision(),
                state.generated(),
                state.published()
            )).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflict(e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/unpublish")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN})
    public Response unpublishDraw(@PathParam("regatta_id") UUID regattaId) {
        try {
            var state = regattaWorkflowService.unpublishDraw(regattaId);
            return Response.ok(new DrawPublicationResponse(
                state.drawRevision(),
                state.resultsRevision(),
                state.generated(),
                state.published()
            )).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        }
    }

    public record GenerateDrawRequest(Long seed) {
    }

    public record GenerateDrawResponse(long seed, boolean generated, boolean published) {
    }

    public record DrawPublicationResponse(
        int draw_revision,
        int results_revision,
        boolean generated,
        boolean published
    ) {
    }
}
