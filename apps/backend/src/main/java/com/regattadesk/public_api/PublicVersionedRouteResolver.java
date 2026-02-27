package com.regattadesk.public_api;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for building and parsing versioned public routes.
 * 
 * All public content is served under immutable versioned paths:
 * /public/v{draw}-{results}/...
 * 
 * This enables safe long-lived CDN caching and deterministic rollback behavior.
 */
@ApplicationScoped
public class PublicVersionedRouteResolver {
    
    private static final String PUBLIC_PREFIX = "/public/";
    private static final Pattern VERSION_PATTERN = Pattern.compile("^/?public/v(\\d+)-(\\d+)/");
    
    /**
     * Builds a versioned public path.
     * 
     * @param drawRevision the draw revision number (must be non-negative)
     * @param resultsRevision the results revision number (must be non-negative)
     * @param basePath the base path to append (e.g., "/schedule", "/regattas/{id}/results")
     * @return the fully qualified versioned path
     * @throws IllegalArgumentException if revisions are negative
     * @throws NullPointerException if basePath is null
     */
    public String buildVersionedPath(int drawRevision, int resultsRevision, String basePath) {
        if (drawRevision < 0) {
            throw new IllegalArgumentException("Draw revision must be non-negative, got: " + drawRevision);
        }
        if (resultsRevision < 0) {
            throw new IllegalArgumentException("Results revision must be non-negative, got: " + resultsRevision);
        }
        if (basePath == null) {
            throw new NullPointerException("Base path must not be null");
        }
        
        // Ensure base path starts with /
        String normalizedBasePath = basePath.startsWith("/") ? basePath : "/" + basePath;
        
        return String.format("%sv%d-%d%s", PUBLIC_PREFIX, drawRevision, resultsRevision, normalizedBasePath);
    }
    
    /**
     * Gets the version prefix for a draw/results revision tuple.
     * 
     * @param drawRevision the draw revision number
     * @param resultsRevision the results revision number
     * @return the version prefix (e.g., "v3-5")
     */
    public String getVersionPrefix(int drawRevision, int resultsRevision) {
        return String.format("v%d-%d", drawRevision, resultsRevision);
    }
    
    /**
     * Extracts version information from a versioned path.
     * 
     * @param path the path to parse
     * @return the version tuple, or null if path is not versioned or malformed
     */
    public VersionTuple extractVersionFromPath(String path) {
        if (path == null) {
            return null;
        }
        
        Matcher matcher = VERSION_PATTERN.matcher(path);
        if (!matcher.find()) {
            return null;
        }
        
        try {
            int drawRevision = Integer.parseInt(matcher.group(1));
            int resultsRevision = Integer.parseInt(matcher.group(2));
            return new VersionTuple(drawRevision, resultsRevision);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Represents a draw/results revision tuple.
     */
    public record VersionTuple(int drawRevision, int resultsRevision) {}
}
