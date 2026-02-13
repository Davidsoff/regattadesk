package com.regattadesk.operator.api;

import com.regattadesk.operator.OperatorToken;
import com.regattadesk.operator.OperatorTokenService;
import com.regattadesk.security.RequireRole;
import com.regattadesk.security.Role;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST resource for operator token management.
 * 
 * Provides endpoints for creating, listing, and revoking operator tokens.
 * All endpoints require REGATTA_ADMIN or SUPER_ADMIN role.
 */
@Path("/api/v1/regattas/{regattaId}/operator/tokens")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OperatorTokenResource {
    
    private final OperatorTokenService tokenService;
    
    @Inject
    public OperatorTokenResource(OperatorTokenService tokenService) {
        this.tokenService = tokenService;
    }
    
    /**
     * List all tokens for a regatta.
     */
    @GET
    @RequireRole({Role.REGATTA_ADMIN, Role.SUPER_ADMIN})
    public Response listTokens(@PathParam("regattaId") UUID regattaId) {
        List<OperatorToken> tokens = tokenService.listTokens(regattaId);
        List<OperatorTokenResponse> responses = tokens.stream()
            .map(OperatorTokenResponse::new)
            .collect(Collectors.toList());
        
        return Response.ok(new OperatorTokenListResponse(responses)).build();
    }
    
    /**
     * Create a new operator token.
     */
    @POST
    @RequireRole({Role.REGATTA_ADMIN, Role.SUPER_ADMIN})
    public Response createToken(
            @PathParam("regattaId") UUID regattaId,
            OperatorTokenCreateRequest request) {
        
        // Validate request
        if (request.getStation() == null || request.getStation().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Station is required"))
                .build();
        }
        if (request.getValidFrom() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Valid from is required"))
                .build();
        }
        if (request.getValidUntil() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Valid until is required"))
                .build();
        }
        if (!request.getValidUntil().isAfter(request.getValidFrom())) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Valid until must be after valid from"))
                .build();
        }
        
        try {
            OperatorToken token = tokenService.issueToken(
                regattaId,
                request.getBlockId(),
                request.getStation(),
                request.getValidFrom(),
                request.getValidUntil()
            );
            
            return Response.status(Response.Status.CREATED)
                .entity(new OperatorTokenResponse(token))
                .build();
                
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }
    
    /**
     * Revoke an operator token.
     */
    @POST
    @Path("/{tokenId}/revoke")
    @RequireRole({Role.REGATTA_ADMIN, Role.SUPER_ADMIN})
    public Response revokeToken(
            @PathParam("regattaId") UUID regattaId,
            @PathParam("tokenId") UUID tokenId) {
        
        // Verify token belongs to regatta
        Optional<OperatorToken> tokenOpt = tokenService.getTokenById(tokenId);
        if (tokenOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Token not found"))
                .build();
        }
        
        OperatorToken token = tokenOpt.get();
        if (!token.getRegattaId().equals(regattaId)) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Token not found in this regatta"))
                .build();
        }
        
        boolean revoked = tokenService.revokeToken(tokenId);
        if (revoked) {
            return Response.ok(new OperationResult("Token revoked successfully")).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Token not found"))
                .build();
        }
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
