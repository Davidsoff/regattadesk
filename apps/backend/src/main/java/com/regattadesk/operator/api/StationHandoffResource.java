package com.regattadesk.operator.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.operator.StationHandoff;
import com.regattadesk.operator.StationHandoffService;
import com.regattadesk.security.RequireRole;
import com.regattadesk.security.Role;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Optional;
import java.util.UUID;

/**
 * REST resource for station handoff management.
 * 
 * Provides endpoints for requesting, revealing PIN, completing, and cancelling
 * station handoffs for non-interrupting device transfer.
 */
@Path("/api/v1/regattas/{regatta_id}/operator/station_handoffs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StationHandoffResource {
    
    private final StationHandoffService handoffService;
    
    @Inject
    public StationHandoffResource(StationHandoffService handoffService) {
        this.handoffService = handoffService;
    }
    
    /**
     * Request a new station handoff.
     * 
     * This creates a pending handoff with a generated PIN that must be
     * revealed on the active device and verified on the requesting device.
     */
    @POST
    public Response requestHandoff(
            @PathParam("regatta_id") UUID regattaId,
            @QueryParam("token_id") UUID tokenId,
            @QueryParam("station") String station,
            @Valid @NotNull(message = "request body is required") StationHandoffCreateRequest request) {
        
        if (tokenId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest("token_id query parameter is required"))
                .build();
        }
        if (station == null || station.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest("station query parameter is required"))
                .build();
        }
        
        try {
            StationHandoff handoff = handoffService.requestHandoff(
                regattaId,
                tokenId,
                station,
                request.getRequestingDeviceId()
            );
            
            return Response.status(Response.Status.CREATED)
                .entity(new StationHandoffResponse(handoff))
                .build();
                
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflict(e.getMessage()))
                .build();
        }
    }
    
    /**
     * Get handoff status.
     */
    @GET
    @Path("/{handoff_id}")
    public Response getHandoff(
            @PathParam("regatta_id") UUID regattaId,
            @PathParam("handoff_id") UUID handoffId) {
        
        Optional<StationHandoff> handoffOpt = handoffService.getHandoff(handoffId);
        if (handoffOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Handoff not found"))
                .build();
        }
        
        StationHandoff handoff = handoffOpt.get();
        if (!handoff.getRegattaId().equals(regattaId)) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Handoff not found"))
                .build();
        }
        
        return Response.ok(new StationHandoffResponse(handoff)).build();
    }
    
    /**
     * Reveal PIN on active station (operator auth).
     */
    @POST
    @Path("/{handoff_id}/reveal_pin")
    @Consumes(MediaType.WILDCARD)
    public Response revealPin(
            @PathParam("regatta_id") UUID regattaId,
            @PathParam("handoff_id") UUID handoffId) {

        Optional<StationHandoff> scoped = getScopedHandoff(regattaId, handoffId);
        if (scoped.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Handoff not found"))
                .build();
        }
        
        try {
            StationHandoffService.HandoffPinRevealResult result = 
                handoffService.revealPin(handoffId, "operator", false);
            return Response.ok(new StationHandoffResponse(result.handoff(), result.pin())).build();
            
        } catch (IllegalStateException e) {
            if ("Handoff has expired".equals(e.getMessage())) {
                return Response.status(Response.Status.GONE)
                    .entity(ErrorResponse.gone("HANDOFF_EXPIRED", "HANDOFF_EXPIRED"))
                    .build();
            }
            return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflict(e.getMessage()))
                .build();
        }
    }
    
    /**
     * Admin fallback to reveal PIN remotely.
     */
    @POST
    @Path("/{handoff_id}/admin_reveal_pin")
    @Consumes(MediaType.WILDCARD)
    @RequireRole({Role.REGATTA_ADMIN, Role.SUPER_ADMIN})
    public Response adminRevealPin(
            @PathParam("regatta_id") UUID regattaId,
            @PathParam("handoff_id") UUID handoffId) {

        Optional<StationHandoff> scoped = getScopedHandoff(regattaId, handoffId);
        if (scoped.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Handoff not found"))
                .build();
        }
        
        try {
            StationHandoffService.HandoffPinRevealResult result = 
                handoffService.revealPin(handoffId, "admin", true);
            return Response.ok(new StationHandoffResponse(result.handoff(), result.pin())).build();
            
        } catch (IllegalStateException e) {
            if ("Handoff has expired".equals(e.getMessage())) {
                return Response.status(Response.Status.GONE)
                    .entity(ErrorResponse.gone("HANDOFF_EXPIRED", "HANDOFF_EXPIRED"))
                    .build();
            }
            return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflict(e.getMessage()))
                .build();
        }
    }
    
    /**
     * Complete handoff with PIN verification.
     */
    @POST
    @Path("/{handoff_id}/complete")
    public Response completeHandoff(
            @PathParam("regatta_id") UUID regattaId,
            @PathParam("handoff_id") UUID handoffId,
            @Valid @NotNull(message = "request body is required") StationHandoffCompleteRequest request) {

        Optional<StationHandoff> scoped = getScopedHandoff(regattaId, handoffId);
        if (scoped.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Handoff not found"))
                .build();
        }
        
        StationHandoffService.HandoffCompletionResult result = 
            handoffService.completeHandoff(handoffId, request.getPin());

        if (!result.success()) {
            if ("Invalid PIN".equals(result.message())) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.badRequest("INVALID_PIN"))
                    .build();
            }
            if ("Handoff has expired".equals(result.message())) {
                return Response.status(Response.Status.GONE)
                    .entity(ErrorResponse.gone("HANDOFF_EXPIRED", "HANDOFF_EXPIRED"))
                    .build();
            }
            return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflict(result.message()))
                .build();
        }
        
        return Response.ok(new StationHandoffResponse(result.handoff())).build();
    }

    private Optional<StationHandoff> getScopedHandoff(UUID regattaId, UUID handoffId) {
        Optional<StationHandoff> handoffOpt = handoffService.getHandoff(handoffId);
        if (handoffOpt.isEmpty()) {
            return Optional.empty();
        }
        if (!handoffOpt.get().getRegattaId().equals(regattaId)) {
            return Optional.empty();
        }
        return handoffOpt;
    }
    
    /**
     * Cancel pending handoff.
     */
    @POST
    @Path("/{handoff_id}/cancel")
    @Consumes(MediaType.WILDCARD)
    public Response cancelHandoff(
            @PathParam("regatta_id") UUID regattaId,
            @PathParam("handoff_id") UUID handoffId) {
        
        // Verify handoff belongs to regatta before cancelling
        Optional<StationHandoff> handoffOpt = handoffService.getHandoff(handoffId);
        if (handoffOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Handoff not found"))
                .build();
        }
        
        if (!handoffOpt.get().getRegattaId().equals(regattaId)) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Handoff not found"))
                .build();
        }
        
        boolean cancelled = handoffService.cancelHandoff(handoffId, "user-cancelled");
        if (cancelled) {
            return Response.ok(new OperationResult("Handoff cancelled successfully")).build();
        }
        
        return Response.status(Response.Status.CONFLICT)
            .entity(ErrorResponse.conflict("Handoff cannot be cancelled"))
            .build();
    }
    
    /**
     * Simple operation result DTO.
     */
    public static class OperationResult {
        private final String message;
        
        public OperationResult(String message) {
            this.message = message;
        }
        
        public String getMessage() {
            return message;
        }
    }
}
