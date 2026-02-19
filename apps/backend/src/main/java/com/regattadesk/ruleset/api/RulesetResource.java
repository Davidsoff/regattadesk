package com.regattadesk.ruleset.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.ruleset.RulesetAggregate;
import com.regattadesk.ruleset.RulesetService;
import com.regattadesk.security.RequireRole;
import com.regattadesk.security.Role;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST resource for ruleset management.
 * 
 * Provides endpoints for creating, listing, retrieving, and updating rulesets.
 * All endpoints require REGATTA_ADMIN or SUPER_ADMIN role.
 */
@Path("/api/v1/rulesets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RulesetResource {
    
    private final RulesetService rulesetService;
    
    @Inject
    public RulesetResource(RulesetService rulesetService) {
        this.rulesetService = rulesetService;
    }
    
    /**
     * List all rulesets.
     */
    @GET
    @RequireRole({Role.REGATTA_ADMIN, Role.SUPER_ADMIN})
    public Response listRulesets(@QueryParam("is_global") Boolean isGlobal) {
        // TODO: Implement filtering by is_global when projection/read model is available
        List<RulesetAggregate> rulesets = rulesetService.listRulesets();
        List<RulesetResponse> responses = rulesets.stream()
            .map(RulesetResponse::new)
            .collect(Collectors.toList());
        
        return Response.ok(new RulesetListResponse(responses)).build();
    }
    
    /**
     * Create a new ruleset.
     */
    @POST
    @RequireRole({Role.REGATTA_ADMIN, Role.SUPER_ADMIN})
    public Response createRuleset(
            @Valid @NotNull(message = "request body is required") RulesetCreateRequest request) {
        
        try {
            UUID id = request.getId() != null ? request.getId() : UUID.randomUUID();
            RulesetAggregate ruleset = rulesetService.createRuleset(
                id,
                request.getName(),
                request.getVersion(),
                request.getDescription(),
                request.getAgeCalculationType()
            );
            
            return Response.status(Response.Status.CREATED)
                .entity(new RulesetResponse(ruleset))
                .build();
                
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        }
    }
    
    /**
     * Get a specific ruleset by ID.
     */
    @GET
    @Path("/{ruleset_id}")
    @RequireRole({Role.REGATTA_ADMIN, Role.SUPER_ADMIN})
    public Response getRuleset(@PathParam("ruleset_id") UUID rulesetId) {
        Optional<RulesetAggregate> ruleset = rulesetService.getRuleset(rulesetId);
        
        if (ruleset.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Ruleset not found"))
                .build();
        }
        
        return Response.ok(new RulesetResponse(ruleset.get())).build();
    }
    
    /**
     * Update an existing ruleset.
     */
    @PATCH
    @Path("/{ruleset_id}")
    @RequireRole({Role.REGATTA_ADMIN, Role.SUPER_ADMIN})
    public Response updateRuleset(
            @PathParam("ruleset_id") UUID rulesetId,
            @Valid @NotNull(message = "request body is required") RulesetUpdateRequest request) {
        
        try {
            // Load existing ruleset to get current values for unspecified fields
            Optional<RulesetAggregate> existing = rulesetService.getRuleset(rulesetId);
            if (existing.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Ruleset not found"))
                    .build();
            }
            
            RulesetAggregate current = existing.get();
            
            // Use provided values or keep existing ones
            String name = request.getName() != null ? request.getName() : current.getName();
            String version = request.getVersion() != null ? request.getVersion() : current.getRulesetVersion();
            String description = request.getDescription() != null ? request.getDescription() : current.getDescription();
            String ageCalculationType = request.getAgeCalculationType() != null 
                ? request.getAgeCalculationType() 
                : current.getAgeCalculationType();
            
            RulesetAggregate ruleset = rulesetService.updateRuleset(
                current,
                name,
                version,
                description,
                ageCalculationType
            );
            
            return Response.ok(new RulesetResponse(ruleset)).build();
                
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        }
    }

    /**
     * Duplicate an existing ruleset with a new name and version.
     */
    @POST
    @Path("/{ruleset_id}/duplicate")
    @RequireRole({Role.REGATTA_ADMIN, Role.SUPER_ADMIN})
    public Response duplicateRuleset(
            @PathParam("ruleset_id") UUID rulesetId,
            @Valid @NotNull(message = "request body is required") RulesetDuplicateRequest request) {
        try {
            RulesetAggregate ruleset = rulesetService.duplicateRuleset(
                rulesetId,
                request.getNewName(),
                request.getNewVersion()
            );
            return Response.status(Response.Status.CREATED)
                .entity(new RulesetResponse(ruleset))
                .build();
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Source ruleset not found:")) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Ruleset not found"))
                    .build();
            }
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        }
    }
}
