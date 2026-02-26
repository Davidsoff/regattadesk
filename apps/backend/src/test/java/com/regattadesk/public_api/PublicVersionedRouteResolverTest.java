package com.regattadesk.public_api;

import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PublicVersionedRouteResolver.
 * 
 * Tests BC05-002 requirement for route resolver to generate 
 * versioned paths: /public/v{draw}-{results}/...
 */
class PublicVersionedRouteResolverTest {
    
    @Test
    void testBuildVersionedPath_BasicPath() {
        PublicVersionedRouteResolver resolver = new PublicVersionedRouteResolver();
        
        int drawRevision = 2;
        int resultsRevision = 3;
        String basePath = "/schedule";
        
        String result = resolver.buildVersionedPath(drawRevision, resultsRevision, basePath);
        
        assertEquals("/public/v2-3/schedule", result);
    }
    
    @Test
    void testBuildVersionedPath_WithRegattaId() {
        PublicVersionedRouteResolver resolver = new PublicVersionedRouteResolver();
        
        UUID regattaId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        int drawRevision = 5;
        int resultsRevision = 7;
        String basePath = String.format("/regattas/%s/schedule", regattaId);
        
        String result = resolver.buildVersionedPath(drawRevision, resultsRevision, basePath);
        
        assertEquals("/public/v5-7/regattas/123e4567-e89b-12d3-a456-426614174000/schedule", result);
    }
    
    @Test
    void testBuildVersionedPath_ZeroRevisions() {
        PublicVersionedRouteResolver resolver = new PublicVersionedRouteResolver();
        
        String result = resolver.buildVersionedPath(0, 0, "/schedule");
        
        assertEquals("/public/v0-0/schedule", result);
    }
    
    @Test
    void testBuildVersionedPath_LargeRevisions() {
        PublicVersionedRouteResolver resolver = new PublicVersionedRouteResolver();
        
        String result = resolver.buildVersionedPath(999, 888, "/results");
        
        assertEquals("/public/v999-888/results", result);
    }
    
    @Test
    void testBuildVersionedPath_WithQueryParameters() {
        PublicVersionedRouteResolver resolver = new PublicVersionedRouteResolver();
        
        String result = resolver.buildVersionedPath(1, 2, "/schedule?event=M1x");
        
        assertEquals("/public/v1-2/schedule?event=M1x", result);
    }
    
    @Test
    void testBuildVersionedPath_BasePathWithoutLeadingSlash() {
        PublicVersionedRouteResolver resolver = new PublicVersionedRouteResolver();
        
        // Should handle base paths without leading slash
        String result = resolver.buildVersionedPath(1, 2, "schedule");
        
        assertEquals("/public/v1-2/schedule", result);
    }
    
    @Test
    void testBuildVersionedPath_EmptyBasePath() {
        PublicVersionedRouteResolver resolver = new PublicVersionedRouteResolver();
        
        String result = resolver.buildVersionedPath(1, 2, "");
        
        assertEquals("/public/v1-2/", result);
    }
    
    @Test
    void testBuildVersionedPath_RootBasePath() {
        PublicVersionedRouteResolver resolver = new PublicVersionedRouteResolver();
        
        String result = resolver.buildVersionedPath(1, 2, "/");
        
        assertEquals("/public/v1-2/", result);
    }
    
    @Test
    void testBuildVersionedPath_NegativeRevisionsThrowsException() {
        PublicVersionedRouteResolver resolver = new PublicVersionedRouteResolver();
        
        assertThrows(IllegalArgumentException.class, 
            () -> resolver.buildVersionedPath(-1, 2, "/schedule"),
            "Negative draw revision should throw IllegalArgumentException");
        
        assertThrows(IllegalArgumentException.class, 
            () -> resolver.buildVersionedPath(1, -1, "/schedule"),
            "Negative results revision should throw IllegalArgumentException");
    }
    
    @Test
    void testBuildVersionedPath_NullBasePathThrowsException() {
        PublicVersionedRouteResolver resolver = new PublicVersionedRouteResolver();
        
        assertThrows(NullPointerException.class, 
            () -> resolver.buildVersionedPath(1, 2, null),
            "Null base path should throw NullPointerException");
    }
    
    @Test
    void testGetVersionPrefix() {
        PublicVersionedRouteResolver resolver = new PublicVersionedRouteResolver();
        
        String prefix = resolver.getVersionPrefix(3, 5);
        
        assertEquals("v3-5", prefix);
    }
    
    @Test
    void testGetVersionPrefix_ZeroRevisions() {
        PublicVersionedRouteResolver resolver = new PublicVersionedRouteResolver();
        
        String prefix = resolver.getVersionPrefix(0, 0);
        
        assertEquals("v0-0", prefix);
    }
    
    @Test
    void testBuildVersionedPath_NestedPaths() {
        PublicVersionedRouteResolver resolver = new PublicVersionedRouteResolver();
        
        UUID regattaId = UUID.randomUUID();
        String basePath = String.format("/regattas/%s/events/M1x/results", regattaId);
        
        String result = resolver.buildVersionedPath(2, 4, basePath);
        
        assertTrue(result.startsWith("/public/v2-4/"));
        assertTrue(result.contains("/regattas/"));
        assertTrue(result.endsWith("/results"));
    }
    
    @Test
    void testExtractVersionFromPath() {
        PublicVersionedRouteResolver resolver = new PublicVersionedRouteResolver();
        
        String path = "/public/v3-5/regattas/123/schedule";
        PublicVersionedRouteResolver.VersionTuple version = resolver.extractVersionFromPath(path);
        
        assertNotNull(version);
        assertEquals(3, version.drawRevision());
        assertEquals(5, version.resultsRevision());
    }
    
    @Test
    void testExtractVersionFromPath_InvalidPath() {
        PublicVersionedRouteResolver resolver = new PublicVersionedRouteResolver();
        
        String path = "/public/schedule";
        PublicVersionedRouteResolver.VersionTuple version = resolver.extractVersionFromPath(path);
        
        assertNull(version, "Non-versioned path should return null");
    }
    
    @Test
    void testExtractVersionFromPath_MalformedVersion() {
        PublicVersionedRouteResolver resolver = new PublicVersionedRouteResolver();
        
        String path = "/public/v-invalid/schedule";
        PublicVersionedRouteResolver.VersionTuple version = resolver.extractVersionFromPath(path);
        
        assertNull(version, "Malformed version should return null");
    }
}
