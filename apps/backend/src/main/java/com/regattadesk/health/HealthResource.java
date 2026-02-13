package com.regattadesk.health;

import com.regattadesk.security.RequireRole;
import com.regattadesk.security.Role;
import com.regattadesk.security.SecurityContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/health")
public class HealthResource {

    @Inject
    SecurityContext securityContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public HealthStatus health() {
        return new HealthStatus("UP", "0.1.0-SNAPSHOT");
    }

    @GET
    @Path("/secure")
    @RequireRole({Role.REGATTA_ADMIN, Role.SUPER_ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    public SecureHealthStatus secureHealth() {
        String username = securityContext.isAuthenticated() 
            ? securityContext.getPrincipal().getUsername() 
            : "unknown";
        return new SecureHealthStatus("UP", "0.1.0-SNAPSHOT", username);
    }

    public record HealthStatus(String status, String version) {}
    public record SecureHealthStatus(String status, String version, String authenticatedUser) {}
}
