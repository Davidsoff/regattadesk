package com.regattadesk.finance.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.finance.PaymentStatus;
import com.regattadesk.finance.PaymentStatusService;
import com.regattadesk.security.RequireRole;
import com.regattadesk.security.SecurityContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
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
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentStatusResource {

    @Inject
    PaymentStatusService paymentStatusService;

    @Inject
    SecurityContext securityContext;

    @GET
    @Path("/entries/{entry_id}/payment_status")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY, INFO_DESK, FINANCIAL_MANAGER})
    public Response getEntryPaymentStatus(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("entry_id") UUID entryId
    ) {
        try {
            return paymentStatusService.getEntryPaymentStatus(regattaId, entryId)
                .map(EntryPaymentStatusResponse::from)
                .map(payload -> Response.ok(payload).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Entry not found"))
                    .build());
        } catch (Exception e) {
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to get entry payment status"))
                .build();
        }
    }

    @PUT
    @Path("/entries/{entry_id}/payment_status")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, FINANCIAL_MANAGER})
    public Response updateEntryPaymentStatus(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("entry_id") UUID entryId,
        PaymentStatusUpdateRequest request
    ) {
        try {
            PaymentStatus targetStatus = PaymentStatus.fromValue(request.paymentStatus());
            String actor = securityContext.getPrincipal() != null
                ? securityContext.getPrincipal().getUsername()
                : "unknown";

            var updated = paymentStatusService.updateEntryPaymentStatus(
                regattaId,
                entryId,
                targetStatus,
                request.paymentReference(),
                actor
            );
            return Response.ok(EntryPaymentStatusResponse.from(updated)).build();
        } catch (IllegalArgumentException e) {
            if ("Entry not found".equals(e.getMessage())) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound(e.getMessage()))
                    .build();
            }
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to update entry payment status"))
                .build();
        }
    }

    @GET
    @Path("/clubs/{club_id}/payment_status")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY, INFO_DESK, FINANCIAL_MANAGER})
    public Response getClubPaymentStatus(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("club_id") UUID clubId
    ) {
        try {
            return paymentStatusService.getClubPaymentStatus(regattaId, clubId)
                .map(ClubPaymentStatusResponse::from)
                .map(payload -> Response.ok(payload).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Club not found"))
                    .build());
        } catch (Exception e) {
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to get club payment status"))
                .build();
        }
    }

    @PUT
    @Path("/clubs/{club_id}/payment_status")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, FINANCIAL_MANAGER})
    public Response updateClubPaymentStatus(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("club_id") UUID clubId,
        PaymentStatusUpdateRequest request
    ) {
        try {
            PaymentStatus targetStatus = PaymentStatus.fromValue(request.paymentStatus());
            String actor = securityContext.getPrincipal() != null
                ? securityContext.getPrincipal().getUsername()
                : "unknown";

            var updated = paymentStatusService.updateClubPaymentStatus(
                regattaId,
                clubId,
                targetStatus,
                request.paymentReference(),
                actor
            );
            return Response.ok(ClubPaymentStatusResponse.from(updated)).build();
        } catch (IllegalArgumentException e) {
            if ("Club not found".equals(e.getMessage())) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound(e.getMessage()))
                    .build();
            }
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to update club payment status"))
                .build();
        }
    }
}
