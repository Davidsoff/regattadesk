package com.regattadesk.entry.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.entry.EntryNotFoundException;
import com.regattadesk.entry.EntryService;
import com.regattadesk.security.RequireRole;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

import static com.regattadesk.security.Role.FINANCIAL_MANAGER;
import static com.regattadesk.security.Role.REGATTA_ADMIN;
import static com.regattadesk.security.Role.SUPER_ADMIN;
import static com.regattadesk.security.Role.INFO_DESK;
import static com.regattadesk.security.Role.HEAD_OF_JURY;

/**
 * REST API for Entry management (BC03-004, BC08-001).
 * 
 * Payment status endpoints enforce FINANCIAL_MANAGER role authorization.
 */
@Path("/api/v1/entries")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EntryResource {

    @Inject
    EntryService entryService;

    /**
     * Gets an entry by ID.
     * 
     * Accessible by staff roles for operational visibility.
     */
    @GET
    @Path("/{id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY, INFO_DESK, FINANCIAL_MANAGER})
    public Response getEntry(@PathParam("id") UUID id) {
        try {
            var entry = entryService.getEntry(id);
            if (entry.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Entry not found"))
                    .build();
            }
            
            return Response.ok(EntryResponse.from(entry.get())).build();
        } catch (Exception e) {
            Log.errorf(e, "Failed to retrieve entry: %s", id);
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to retrieve entry"))
                .build();
        }
    }

    /**
     * Updates the payment status of an entry (BC08-001).
     * 
     * Requires FINANCIAL_MANAGER role. Also accessible by REGATTA_ADMIN and SUPER_ADMIN
     * as they have elevated permissions for all regatta operations.
     * 
     * Emits EntryPaymentStatusUpdatedEvent for audit trail.
     * 
     * @param id the entry ID
     * @param request the payment status update request
     * @return the updated entry with new payment status
     */
    @PATCH
    @Path("/{id}/payment-status")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, FINANCIAL_MANAGER})
    public Response updatePaymentStatus(
            @PathParam("id") UUID id,
            @Valid UpdatePaymentStatusRequest request
    ) {
        try {
            var entry = entryService.updatePaymentStatus(
                id,
                request.paymentStatus(),
                request.paidAt(),
                request.paidBy(),
                request.paymentReference()
            );
            
            return Response.ok(EntryResponse.from(entry)).build();
        } catch (EntryNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (Exception e) {
            Log.errorf(e, "Failed to update payment status for entry: %s", id);
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to update payment status"))
                .build();
        }
    }
}
