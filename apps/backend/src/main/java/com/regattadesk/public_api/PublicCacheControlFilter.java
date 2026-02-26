package com.regattadesk.public_api;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Response filter that applies cache control headers to public endpoints.
 * 
 * BC05-002 cache policy:
 * - /public/session: Cache-Control: no-store
 * - /public/regattas/{regatta_id}/versions: Cache-Control: no-store, must-revalidate
 * - /public/v{draw}-{results}/...: Cache-Control: public, max-age=31536000, immutable
 * 
 * This filter only applies headers if they haven't been explicitly set by the resource.
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class PublicCacheControlFilter implements ContainerResponseFilter {
    
    private static final Logger LOG = Logger.getLogger(PublicCacheControlFilter.class);
    
    private static final String CACHE_CONTROL_HEADER = "Cache-Control";
    
    // Cache policies
    private static final String CACHE_NO_STORE = "no-store";
    private static final String CACHE_NO_STORE_MUST_REVALIDATE = "no-store, must-revalidate";
    private static final String CACHE_IMMUTABLE = "public, max-age=31536000, immutable";
    
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        String path = requestContext.getUriInfo().getPath();
        
        // Skip if Cache-Control header already set by the resource
        if (responseContext.getHeaders().containsKey(CACHE_CONTROL_HEADER)) {
            LOG.debugf("Cache-Control already set for path: %s", path);
            return;
        }
        
        String cacheControl = determineCacheControl(path);
        
        if (cacheControl != null) {
            responseContext.getHeaders().putSingle(CACHE_CONTROL_HEADER, cacheControl);
            LOG.debugf("Applied Cache-Control: %s for path: %s", cacheControl, path);
        }
    }
    
    /**
     * Determines the appropriate cache control value for a given path.
     * 
     * @param path the request path (without leading slash in JAX-RS)
     * @return the cache control header value, or null if no policy applies
     */
    private String determineCacheControl(String path) {
        // Normalize path - handle both with and without leading slash
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        
        // Match public/session
        if (normalizedPath.equals("public/session")) {
            return CACHE_NO_STORE;
        }
        
        // Match public/regattas/{id}/versions
        if (normalizedPath.matches("^public/regattas/[^/]+/versions$")) {
            return CACHE_NO_STORE_MUST_REVALIDATE;
        }
        
        // Match public/v{draw}-{results}/...
        if (normalizedPath.matches("^public/v\\d+-\\d+/.*")) {
            return CACHE_IMMUTABLE;
        }
        
        // No cache policy for this path
        return null;
    }
}
