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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
     * List pending handoffs for staff oversight.
     */
    @GET
    @RequireRole({Role.REGATTA_ADMIN, Role.SUPER_ADMIN})
    @Operation(summary = "List pending station handoffs")
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = StationHandoffListResponse.class))
        ),
        @APIResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public Response listPendingHandoffs(
            @PathParam("regatta_id") UUID regattaId,
            @QueryParam("station") String station,
            @QueryParam("token_id") UUID tokenId) {

        List<StationHandoffResponse> responses = handoffService
            .listPendingHandoffs(regattaId, station, tokenId)
            .stream()
            .map(StationHandoffResponse::new)
            .collect(Collectors.toList());

        return Response.ok(new StationHandoffListResponse(responses)).build();
    }
    
    /**
     * Request a new station handoff.
     * 
     * This creates a pending handoff with a generated PIN that must be
     * revealed on the active device and verified on the requesting device.
     */
    @POST
    @Operation(summary = "Request Handoff")
    @APIResponses({
        @APIResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(schema = @Schema(implementation = StationHandoffResponse.class))
        ),
        @APIResponse(
            responseCode = "400",
            description = "Bad Request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @APIResponse(
            responseCode = "409",
            description = "Conflict",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
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
    @Operation(summary = "Get Handoff")
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = StationHandoffResponse.class))
        ),
        @APIResponse(
            responseCode = "404",
            description = "Not Found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
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
    @Operation(summary = "Reveal Pin")
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = StationHandoffResponse.class))
        ),
        @APIResponse(
            responseCode = "404",
            description = "Not Found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @APIResponse(
            responseCode = "409",
            description = "Conflict",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @APIResponse(
            responseCode = "410",
            description = "Gone",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
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
    @Operation(summary = "Admin Reveal Pin")
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = StationHandoffResponse.class))
        ),
        @APIResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @APIResponse(
            responseCode = "404",
            description = "Not Found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @APIResponse(
            responseCode = "409",
            description = "Conflict",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @APIResponse(
            responseCode = "410",
            description = "Gone",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
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
    @Operation(summary = "Complete Handoff")
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = StationHandoffResponse.class))
        ),
        @APIResponse(
            responseCode = "400",
            description = "Bad Request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @APIResponse(
            responseCode = "404",
            description = "Not Found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @APIResponse(
            responseCode = "409",
            description = "Conflict",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @APIResponse(
            responseCode = "410",
            description = "Gone",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
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
                    .entity(new ErrorResponse("INVALID_PIN", "Invalid PIN"))
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
    @Operation(summary = "Cancel Handoff")
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = OperationResult.class))
        ),
        @APIResponse(
            responseCode = "404",
            description = "Not Found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @APIResponse(
            responseCode = "409",
            description = "Conflict",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
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
