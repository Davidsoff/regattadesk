package com.regattadesk.security;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.inject.Inject;

/**
 * Test resource for integration testing of authorization filters.
 * This resource contains endpoints with various role requirements for testing.
 */
@Path("/test/auth")
public class TestAuthResource {
    
    @Inject
    SecurityContext securityContext;
    
    /**
     * Public endpoint with no role requirement.
     */
    @GET
    @Path("/public")
    @Produces(MediaType.APPLICATION_JSON)
    public TestResponse publicEndpoint() {
        return new TestResponse(
            "public",
            securityContext.isAuthenticated() ? securityContext.getPrincipal().getUsername() : "anonymous"
        );
    }
    
    /**
     * Protected endpoint requiring REGATTA_ADMIN role.
     */
    @GET
    @Path("/admin")
    @RequireRole(Role.REGATTA_ADMIN)
    @Produces(MediaType.APPLICATION_JSON)
    public TestResponse adminEndpoint() {
        return new TestResponse("admin", securityContext.getPrincipal().getUsername());
    }
    
    /**
     * Protected endpoint requiring either INFO_DESK or REGATTA_ADMIN role.
     */
    @GET
    @Path("/desk")
    @RequireRole({Role.INFO_DESK, Role.REGATTA_ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    public TestResponse deskEndpoint() {
        return new TestResponse("desk", securityContext.getPrincipal().getUsername());
    }
    
    /**
     * Protected endpoint requiring SUPER_ADMIN role.
     */
    @GET
    @Path("/super")
    @RequireRole(Role.SUPER_ADMIN)
    @Produces(MediaType.APPLICATION_JSON)
    public TestResponse superAdminEndpoint() {
        return new TestResponse("super", securityContext.getPrincipal().getUsername());
    }
    
    public record TestResponse(String endpoint, String username) {}
}
