package com.regattadesk.operator.api;

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
                .entity(new ErrorResponse("token_id query parameter is required"))
                .build();
        }
        if (station == null || station.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("station query parameter is required"))
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
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse(e.getMessage()))
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
                .entity(new ErrorResponse("Handoff not found"))
                .build();
        }
        
        StationHandoff handoff = handoffOpt.get();
        if (!handoff.getRegattaId().equals(regattaId)) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Handoff not found"))
                .build();
        }
        
        return Response.ok(new StationHandoffResponse(handoff)).build();
    }
    
    /**
     * Reveal PIN on active station (operator auth).
     */
    @POST
    @Path("/{handoff_id}/reveal_pin")
    public Response revealPin(
            @PathParam("regatta_id") UUID regattaId,
            @PathParam("handoff_id") UUID handoffId) {
        
        try {
            StationHandoffService.HandoffPinRevealResult result = 
                handoffService.revealPin(handoffId, "operator", false);
            
            if (!result.handoff().getRegattaId().equals(regattaId)) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Handoff not found"))
                    .build();
            }
            
            return Response.ok(new StationHandoffResponse(result.handoff(), result.pin())).build();
            
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }
    
    /**
     * Admin fallback to reveal PIN remotely.
     */
    @POST
    @Path("/{handoff_id}/admin_reveal_pin")
    @RequireRole({Role.REGATTA_ADMIN, Role.SUPER_ADMIN})
    public Response adminRevealPin(
            @PathParam("regatta_id") UUID regattaId,
            @PathParam("handoff_id") UUID handoffId) {
        
        try {
            StationHandoffService.HandoffPinRevealResult result = 
                handoffService.revealPin(handoffId, "admin", true);
            
            if (!result.handoff().getRegattaId().equals(regattaId)) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Handoff not found"))
                    .build();
            }
            
            return Response.ok(new StationHandoffResponse(result.handoff(), result.pin())).build();
            
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse(e.getMessage()))
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
        
        StationHandoffService.HandoffCompletionResult result = 
            handoffService.completeHandoff(handoffId, request.getPin());
        
        if (result.handoff() != null && !result.handoff().getRegattaId().equals(regattaId)) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Handoff not found"))
                .build();
        }
        
        if (!result.success()) {
            if ("Invalid PIN".equals(result.message())) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("INVALID_PIN"))
                    .build();
            }
            return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse(result.message()))
                .build();
        }
        
        return Response.ok(new StationHandoffResponse(result.handoff())).build();
    }
    
    /**
     * Cancel pending handoff.
     */
    @POST
    @Path("/{handoff_id}/cancel")
    public Response cancelHandoff(
            @PathParam("regatta_id") UUID regattaId,
            @PathParam("handoff_id") UUID handoffId) {
        
        // Verify handoff belongs to regatta before cancelling
        Optional<StationHandoff> handoffOpt = handoffService.getHandoff(handoffId);
        if (handoffOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Handoff not found"))
                .build();
        }
        
        if (!handoffOpt.get().getRegattaId().equals(regattaId)) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Handoff not found"))
                .build();
        }
        
        boolean cancelled = handoffService.cancelHandoff(handoffId, "user-cancelled");
        if (cancelled) {
            return Response.ok(new OperationResult("Handoff cancelled successfully")).build();
        }
        
        return Response.status(Response.Status.CONFLICT)
            .entity(new ErrorResponse("Handoff cannot be cancelled"))
            .build();
    }
    
    /**
     * Simple error response DTO.
     */
    public static class ErrorResponse {
        private final String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
        
        public String getError() {
            return error;
        }
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
