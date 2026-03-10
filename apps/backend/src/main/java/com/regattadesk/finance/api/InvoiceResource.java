package com.regattadesk.finance.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.finance.InvoiceGenerationJobStatus;
import com.regattadesk.finance.InvoiceService;
import com.regattadesk.finance.InvoiceStatus;
import com.regattadesk.security.RequireRole;
import com.regattadesk.security.SecurityContext;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

import static com.regattadesk.security.Role.FINANCIAL_MANAGER;
import static com.regattadesk.security.Role.HEAD_OF_JURY;
import static com.regattadesk.security.Role.INFO_DESK;
import static com.regattadesk.security.Role.REGATTA_ADMIN;
import static com.regattadesk.security.Role.SUPER_ADMIN;

@Path("/api/v1/regattas/{regatta_id}/invoices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InvoiceResource {

    @Inject
    InvoiceService invoiceService;

    @Inject
    SecurityContext securityContext;

    @GET
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY, INFO_DESK, FINANCIAL_MANAGER})
    public Response listInvoices(
        @PathParam("regatta_id") UUID regattaId,
        @QueryParam("cursor") String cursor,
        @QueryParam("limit") @DefaultValue("20") Integer limit,
        @QueryParam("club_id") UUID clubId,
        @QueryParam("status") String status
    ) {
        try {
            InvoiceStatus filterStatus = status == null || status.isBlank() ? null : InvoiceStatus.fromValue(status);
            InvoiceService.InvoiceListResult result = invoiceService.listInvoices(regattaId, cursor, limit, clubId, filterStatus);
            return Response.ok(new InvoiceListResponse(
                result.invoices().stream().map(InvoiceResponse::from).toList(),
                new InvoiceListPaginationResponse(result.nextCursor() != null, result.nextCursor())
            )).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to list invoices"))
                .build();
        }
    }

    @POST
    @Path("/generate")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, FINANCIAL_MANAGER})
    public Response generateInvoices(
        @PathParam("regatta_id") UUID regattaId,
        @Valid InvoiceGenerateRequest request
    ) {
        try {
            List<UUID> clubIds = request == null ? List.of() : request.clubIds();
            String idempotencyKey = request == null ? null : request.idempotencyKey();
            String actor = currentActor();
            return Response.status(Response.Status.ACCEPTED)
                .entity(InvoiceGenerationJobResponse.from(
                    invoiceService.requestGeneration(regattaId, clubIds, idempotencyKey, actor)
                ))
                .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to request invoice generation"))
                .build();
        }
    }

    @GET
    @Path("/jobs/{job_id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY, INFO_DESK, FINANCIAL_MANAGER})
    public Response getGenerationJob(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("job_id") UUID jobId
    ) {
        try {
            return invoiceService.getJob(regattaId, jobId)
                .map(InvoiceGenerationJobResponse::from)
                .map(job -> Response.ok(job).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Invoice generation job not found"))
                    .build());
        } catch (Exception e) {
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to load invoice generation job"))
                .build();
        }
    }

    @GET
    @Path("/{invoice_id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY, INFO_DESK, FINANCIAL_MANAGER})
    public Response getInvoice(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("invoice_id") UUID invoiceId
    ) {
        try {
            return invoiceService.getInvoice(regattaId, invoiceId)
                .map(InvoiceResponse::from)
                .map(payload -> Response.ok(payload).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Invoice not found"))
                    .build());
        } catch (Exception e) {
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to load invoice"))
                .build();
        }
    }

    @POST
    @Path("/{invoice_id}/mark_paid")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, FINANCIAL_MANAGER})
    public Response markInvoicePaid(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("invoice_id") UUID invoiceId,
        @Valid InvoiceMarkPaidRequest request
    ) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest("Request body is required"))
                .build();
        }
        try {
            return Response.ok(InvoiceResponse.from(invoiceService.markPaid(
                regattaId,
                invoiceId,
                request.paidBy(),
                request.paidAt(),
                request.paymentReference(),
                currentActor()
            ))).build();
        } catch (InvoiceService.InvoiceNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        } catch (InvoiceService.InvoiceConflictException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflict(e.getMessage()))
                .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to mark invoice paid"))
                .build();
        }
    }

    private String currentActor() {
        if (securityContext.getPrincipal() == null || securityContext.getPrincipal().getUsername() == null) {
            return "unknown";
        }
        return securityContext.getPrincipal().getUsername();
    }
}
