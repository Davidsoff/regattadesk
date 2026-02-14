package com.regattadesk.operator.api;

import com.regattadesk.operator.OperatorToken;
import com.regattadesk.operator.OperatorTokenService;
import com.regattadesk.security.RequireRole;
import com.regattadesk.security.Role;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST resource for operator token management.
 * 
 * Provides endpoints for creating, listing, and revoking operator tokens.
 * All endpoints require REGATTA_ADMIN or SUPER_ADMIN role.
 */
@Path("/api/v1/regattas/{regatta_id}/operator/tokens")
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
    public Response listTokens(@PathParam("regatta_id") UUID regattaId) {
        List<OperatorToken> tokens = tokenService.listTokens(regattaId);
        List<OperatorTokenSummaryResponse> responses = tokens.stream()
            .map(OperatorTokenSummaryResponse::new)
            .collect(Collectors.toList());
        
        return Response.ok(new OperatorTokenListResponse(responses)).build();
    }
    
    /**
     * Create a new operator token.
     */
    @POST
    @RequireRole({Role.REGATTA_ADMIN, Role.SUPER_ADMIN})
    public Response createToken(
            @PathParam("regatta_id") UUID regattaId,
            @Valid @NotNull(message = "request body is required") OperatorTokenCreateRequest request) {
        
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
    @Path("/{token_id}/revoke")
    @RequireRole({Role.REGATTA_ADMIN, Role.SUPER_ADMIN})
    public Response revokeToken(
            @PathParam("regatta_id") UUID regattaId,
            @PathParam("token_id") UUID tokenId) {
        OperatorTokenService.RevokeResult revokeResult = tokenService.revokeTokenForRegatta(tokenId, regattaId);
        if (revokeResult == OperatorTokenService.RevokeResult.REVOKED) {
            return Response.ok(new OperationResult("Token revoked successfully")).build();
        }
        return Response.status(Response.Status.NOT_FOUND)
            .entity(new ErrorResponse("Token not found"))
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
