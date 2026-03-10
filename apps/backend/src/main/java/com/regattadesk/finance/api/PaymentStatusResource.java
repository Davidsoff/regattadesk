package com.regattadesk.finance.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.finance.PaymentStatus;
import com.regattadesk.finance.PaymentStatusService;
import com.regattadesk.security.RequireRole;
import com.regattadesk.security.SecurityContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

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
    @Path("/finance/entries")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY, INFO_DESK, FINANCIAL_MANAGER})
    @Operation(summary = "List Finance Entries")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = FinanceEntryListResponse.class))),
        @APIResponse(responseCode = "400", description = "Bad Request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response listFinanceEntries(
        @PathParam("regatta_id") UUID regattaId,
        @QueryParam("search") String search,
        @Parameter(schema = @Schema(enumeration = {"paid", "unpaid"}))
        @QueryParam("payment_status") String paymentStatus,
        @QueryParam("cursor") String cursor,
        @Parameter(schema = @Schema(implementation = Integer.class, defaultValue = "100", minimum = "1", maximum = "100"))
        @QueryParam("limit") @DefaultValue("100") Integer limit
    ) {
        try {
            String normalizedPaymentStatus = normalizeEntryPaymentStatus(paymentStatus);
            var result = paymentStatusService.listFinanceEntries(regattaId, search, normalizedPaymentStatus, limit, cursor);
            var entries = result.entries()
                .stream()
                .map(FinanceEntrySummaryResponse::from)
                .toList();
            return Response.ok(new FinanceEntryListResponse(
                entries,
                new FinanceListPaginationResponse(result.nextCursor() != null, result.nextCursor())
            )).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to list finance entries"))
                .build();
        }
    }

    @GET
    @Path("/finance/clubs")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY, INFO_DESK, FINANCIAL_MANAGER})
    @Operation(summary = "List Finance Clubs")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = FinanceClubListResponse.class))),
        @APIResponse(responseCode = "400", description = "Bad Request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response listFinanceClubs(
        @PathParam("regatta_id") UUID regattaId,
        @QueryParam("search") String search,
        @Parameter(schema = @Schema(enumeration = {"paid", "unpaid", "partial"}))
        @QueryParam("payment_status") String paymentStatus,
        @QueryParam("cursor") String cursor,
        @Parameter(schema = @Schema(implementation = Integer.class, defaultValue = "100", minimum = "1", maximum = "100"))
        @QueryParam("limit") @DefaultValue("100") Integer limit
    ) {
        try {
            String normalizedPaymentStatus = normalizeClubPaymentStatus(paymentStatus);
            var result = paymentStatusService.listFinanceClubs(regattaId, search, normalizedPaymentStatus, limit, cursor);
            var clubs = result.clubs()
                .stream()
                .map(FinanceClubSummaryResponse::from)
                .toList();
            return Response.ok(new FinanceClubListResponse(
                clubs,
                new FinanceListPaginationResponse(result.nextCursor() != null, result.nextCursor())
            )).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to list finance clubs"))
                .build();
        }
    }

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

    @POST
    @Path("/payments/mark_bulk")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, FINANCIAL_MANAGER})
    public Response bulkMarkPaymentStatus(
        @PathParam("regatta_id") UUID regattaId,
        PaymentBulkMarkRequest request
    ) {
        try {
            if (request == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.badRequest("Request body is required"))
                    .build();
            }

            PaymentStatus targetStatus = PaymentStatus.fromValue(request.paymentStatus());
            String actor = securityContext.getPrincipal() != null
                ? securityContext.getPrincipal().getUsername()
                : "unknown";

            var result = paymentStatusService.bulkMarkPaymentStatuses(
                regattaId,
                request.entryIds(),
                request.clubIds(),
                targetStatus,
                request.paymentReference(),
                actor,
                request.idempotencyKey()
            );
            return Response.ok(PaymentBulkMarkResponse.from(result)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to bulk update payment status"))
                .build();
        }
    }

    private String normalizeEntryPaymentStatus(String paymentStatus) {
        return normalizeQueryPaymentStatus(
            paymentStatus,
            "payment_status must be one of: paid, unpaid",
            "paid",
            "unpaid"
        );
    }

    private String normalizeClubPaymentStatus(String paymentStatus) {
        return normalizeQueryPaymentStatus(
            paymentStatus,
            "payment_status must be one of: paid, unpaid, partial",
            "paid",
            "unpaid",
            "partial"
        );
    }

    private String normalizeQueryPaymentStatus(String paymentStatus, String errorMessage, String... supportedValues) {
        if (paymentStatus == null || paymentStatus.isBlank()) {
            return null;
        }

        String normalized = paymentStatus.trim().toLowerCase();
        for (String value : supportedValues) {
            if (value.equals(normalized)) {
                return normalized;
            }
        }

        throw new IllegalArgumentException(errorMessage);
    }
}
