package com.regattadesk.health;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/health")
public class HealthResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public HealthStatus health() {
        return new HealthStatus("UP", "0.1.0-SNAPSHOT");
    }

    public record HealthStatus(String status, String version) {}
}
