package com.regattadesk.adjudication.api;

import com.regattadesk.adjudication.AdjudicationService;
import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.security.RequireRole;
import com.regattadesk.security.SecurityContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

import static com.regattadesk.security.Role.HEAD_OF_JURY;
import static com.regattadesk.security.Role.INFO_DESK;
import static com.regattadesk.security.Role.REGATTA_ADMIN;
import static com.regattadesk.security.Role.SUPER_ADMIN;

@Path("/api/v1/regattas/{regatta_id}/adjudication")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdjudicationResource {

    @Inject
    AdjudicationService adjudicationService;

    @Inject
    SecurityContext securityContext;

    @GET
    @Path("/investigations")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY, INFO_DESK})
    public Response listInvestigations(@PathParam("regatta_id") UUID regattaId) {
        return Response.ok(adjudicationService.listInvestigations(regattaId)).build();
    }

    @POST
    @Path("/investigations")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY})
    public Response openInvestigation(@PathParam("regatta_id") UUID regattaId, OpenInvestigationRequest request) {
        try {
            return Response.ok(adjudicationService.openInvestigation(regattaId, request, actor())).build();
        } catch (IllegalArgumentException e) {
            return badRequestOrNotFound(e);
        }
    }

    @GET
    @Path("/entries/{entry_id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY, INFO_DESK})
    public Response getEntryDetail(@PathParam("regatta_id") UUID regattaId, @PathParam("entry_id") UUID entryId) {
        return adjudicationService.getEntryDetail(regattaId, entryId)
            .map(detail -> Response.ok(detail).build())
            .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Entry not found"))
                .build());
    }

    @POST
    @Path("/entries/{entry_id}/penalty")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY})
    public Response applyPenalty(@PathParam("regatta_id") UUID regattaId, @PathParam("entry_id") UUID entryId, AdjudicationActionRequest request) {
        return mutate(() -> adjudicationService.applyPenalty(regattaId, entryId, request, actor()));
    }

    @POST
    @Path("/entries/{entry_id}/dsq")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY})
    public Response applyDsq(@PathParam("regatta_id") UUID regattaId, @PathParam("entry_id") UUID entryId, AdjudicationActionRequest request) {
        return mutate(() -> adjudicationService.applyDsq(regattaId, entryId, request, actor()));
    }

    @POST
    @Path("/entries/{entry_id}/exclude")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY})
    public Response applyExclusion(@PathParam("regatta_id") UUID regattaId, @PathParam("entry_id") UUID entryId, AdjudicationActionRequest request) {
        return mutate(() -> adjudicationService.applyExclusion(regattaId, entryId, request, actor()));
    }

    @POST
    @Path("/entries/{entry_id}/revert_dsq")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY})
    public Response revertDsq(@PathParam("regatta_id") UUID regattaId, @PathParam("entry_id") UUID entryId, AdjudicationActionRequest request) {
        return mutate(() -> adjudicationService.revertDsq(regattaId, entryId, request, actor()));
    }

    private Response mutate(ThrowingSupplier supplier) {
        try {
            return Response.ok(supplier.get()).build();
        } catch (IllegalArgumentException e) {
            return badRequestOrNotFound(e);
        } catch (Exception e) {
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to apply adjudication action"))
                .build();
        }
    }

    private Response badRequestOrNotFound(IllegalArgumentException e) {
        if ("Entry not found".equals(e.getMessage()) || "Regatta not found".equals(e.getMessage())) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        }
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponse.badRequest(e.getMessage()))
            .build();
    }

    private String actor() {
        return securityContext.getPrincipal() != null
            ? securityContext.getPrincipal().getUsername()
            : "unknown";
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get() throws Exception;
    }
}
