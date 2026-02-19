package com.regattadesk.operator.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.operator.OperatorToken;
import com.regattadesk.operator.OperatorTokenPdfService;
import com.regattadesk.operator.OperatorTokenService;
import com.regattadesk.security.RequireRole;
import com.regattadesk.security.Role;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
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
@Path("/api/v1/regattas/{regatta_id}/operator/tokens")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OperatorTokenResource {
    
    private final OperatorTokenService tokenService;
    private final OperatorTokenPdfService pdfService;
    
    @ConfigProperty(name = "regattadesk.operator.url", defaultValue = "https://operator.regattadesk.com")
    String operatorUrl;
    
    @Inject
    public OperatorTokenResource(OperatorTokenService tokenService, OperatorTokenPdfService pdfService) {
        this.tokenService = tokenService;
        this.pdfService = pdfService;
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
                .entity(ErrorResponse.badRequest(e.getMessage()))
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
            .entity(ErrorResponse.notFound("Token not found"))
            .build();
    }
    
    /**
     * Export operator token as PDF with QR code and fallback instructions.
     */
    @GET
    @Path("/{token_id}/export_pdf")
    @Produces("application/pdf")
    @RequireRole({Role.REGATTA_ADMIN, Role.SUPER_ADMIN})
    public Response exportTokenPdf(
            @PathParam("regatta_id") UUID regattaId,
            @PathParam("token_id") UUID tokenId) {
        
        Optional<OperatorToken> tokenOpt = tokenService.getTokenById(tokenId);
        if (tokenOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.notFound("Token not found"))
                .build();
        }
        
        OperatorToken token = tokenOpt.get();
        if (!token.getRegattaId().equals(regattaId)) {
            return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.notFound("Token not found"))
                .build();
        }
        
        try {
            byte[] pdfBytes = pdfService.generateTokenPdf(token, operatorUrl);
            String filename = String.format("operator-token-%s-%s.pdf", 
                token.getStation().replaceAll("[^a-zA-Z0-9-]", "-"), 
                token.getId().toString().substring(0, 8));
            
            return Response.ok(pdfBytes)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .build();
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.internalError("Failed to generate PDF: " + e.getMessage()))
                .build();
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
