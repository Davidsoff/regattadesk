package com.regattadesk.public_api;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Integration tests for PublicCacheControlFilter.
 * 
 * Tests BC05-002 requirement for cache headers by route group:
 * - /public/session: Cache-Control: no-store
 * - /public/regattas/{regatta_id}/versions: Cache-Control: no-store, must-revalidate
 * - /public/v{draw}-{results}/...: Cache-Control: public, max-age=31536000, immutable
 */
@QuarkusTest
class PublicCacheControlFilterTest {
    
    @Test
    void testSessionEndpoint_HasNoStoreCache() {
        given()
            .when()
            .post("/public/session")
            .then()
            .statusCode(204)
            .header("Cache-Control", equalTo("no-store"));
    }
    
    @Test
    void testVersionsEndpoint_HasNoStoreMustRevalidateCache() {
        // This test requires a valid regatta to exist
        // We'll test the header is set by the filter regardless of response status
        given()
            .when()
            .get("/public/regattas/00000000-0000-0000-0000-000000000001/versions")
            .then()
            .header("Cache-Control", equalTo("no-store, must-revalidate"));
    }
    
    /**
     * Mock resource for testing versioned route cache headers.
     * This would be replaced by actual schedule/results resources in implementation.
     */
    @Path("/public")
    public static class MockVersionedResource {
        
        @GET
        @Path("/v{draw}-{results}/test/schedule")
        public Response getSchedule(
                @PathParam("draw") int drawRevision,
                @PathParam("results") int resultsRevision) {
            return Response.ok("test schedule")
                .build();
        }
        
        @GET
        @Path("/v{draw}-{results}/test/results")
        public Response getResults(
                @PathParam("draw") int drawRevision,
                @PathParam("results") int resultsRevision) {
            return Response.ok("test results")
                .build();
        }
    }
}
