package com.regattadesk.regatta.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.regatta.RegattaWorkflowService;
import com.regattadesk.security.RequireRole;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

import static com.regattadesk.security.Role.FINANCIAL_MANAGER;
import static com.regattadesk.security.Role.HEAD_OF_JURY;
import static com.regattadesk.security.Role.INFO_DESK;
import static com.regattadesk.security.Role.REGATTA_ADMIN;
import static com.regattadesk.security.Role.SUPER_ADMIN;

@Path("/api/v1/regattas/{regatta_id}")
@Produces(MediaType.APPLICATION_JSON)
public class RegattaResource {

    @Inject
    RegattaWorkflowService regattaWorkflowService;

    @GET
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY, INFO_DESK, FINANCIAL_MANAGER})
    public Response getRegatta(@PathParam("regatta_id") UUID regattaId) {
        return regattaWorkflowService.getRegatta(regattaId)
            .map(state -> Response.ok(new RegattaResponse(state)).build())
            .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Regatta not found"))
                .build());
    }

    public record RegattaResponse(
        UUID id,
        String name,
        String description,
        String time_zone,
        String status,
        java.math.BigDecimal entry_fee,
        String currency,
        int draw_revision,
        int results_revision,
        Long draw_seed,
        boolean draw_generated,
        boolean draw_published
    ) {
        public RegattaResponse(RegattaWorkflowService.RegattaState state) {
            this(
                state.id(),
                state.name(),
                state.description(),
                state.timeZone(),
                state.status(),
                state.entryFee(),
                state.currency(),
                state.drawRevision(),
                state.resultsRevision(),
                state.drawSeed(),
                state.drawGenerated(),
                state.drawPublished()
            );
        }
    }
}
