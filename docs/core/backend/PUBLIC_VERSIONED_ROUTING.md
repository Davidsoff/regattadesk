# Public API Versioned Routing

## Overview

The RegattaDesk backend implements immutable versioned public routing to enable safe long-lived CDN caching and deterministic rollback behavior. This document describes the implementation of BC05-002.

## Architecture

### Versioned URL Pattern

All public content is served under immutable versioned paths:

```
/public/v{draw}-{results}/...
```

Where:
- `{draw}` is the draw revision number (0-based integer)
- `{results}` is the results revision number (0-based integer)

**Examples:**
```
/public/v0-0/regattas/550e8400-e29b-41d4-a716-446655440000/schedule
/public/v2-3/regattas/550e8400-e29b-41d4-a716-446655440000/results
/public/v5-7/regattas/550e8400-e29b-41d4-a716-446655440000/schedule
```

### Cache Policy

The backend applies different cache policies based on route patterns:

| Route Pattern | Cache-Control Header | Purpose |
|--------------|---------------------|---------|
| `/public/session` | `no-store` | Session creation - must never be cached |
| `/public/regattas/{id}/versions` | `no-store, must-revalidate` | Version discovery - always fetch latest |
| `/public/v{draw}-{results}/...` | `public, max-age=31536000, immutable` | Versioned content - safe for long-term CDN caching (1 year) |

## Components

### PublicVersionedRouteResolver

**Package:** `com.regattadesk.public_api`

Utility class for building and parsing versioned public routes.

**Key Methods:**

```java
// Build a versioned path
String path = resolver.buildVersionedPath(2, 3, "/regattas/{id}/schedule");
// Returns: "/public/v2-3/regattas/{id}/schedule"

// Get version prefix
String prefix = resolver.getVersionPrefix(2, 3);
// Returns: "v2-3"

// Extract version from path
VersionTuple version = resolver.extractVersionFromPath("/public/v2-3/schedule");
// Returns: VersionTuple(drawRevision=2, resultsRevision=3)
```

### PublicCacheControlFilter

**Package:** `com.regattadesk.public_api`

JAX-RS response filter that automatically applies cache control headers based on route patterns.

**Behavior:**
- Runs at `HEADER_DECORATOR` priority
- Only applies headers if not already set by the resource
- Pattern-matches against request path
- Applies appropriate cache policy per BC05-002 requirements

**Implementation Note:** The filter respects resource-level cache headers. If a resource explicitly sets `Cache-Control`, the filter will not override it.

### RegattaVersionRepository

**Package:** `com.regattadesk.public_api`

Application-scoped repository for fetching regatta version information.

**Purpose:**
- Centralizes database queries for regatta draw/results revisions
- Reduces code duplication across versioned resources
- Provides consistent error handling

**Usage:**

```java
@Inject
RegattaVersionRepository versionRepository;

public Response getResource(UUID regattaId) {
    RegattaVersionRepository.VersionInfo versionInfo = 
        versionRepository.fetchVersionInfo(regattaId);
    
    if (versionInfo == null) {
        return Response.status(404).entity("Regatta not found").build();
    }
    
    // Use versionInfo.drawRevision() and versionInfo.resultsRevision()
}
```

## Sample Resources

### PublicVersionedScheduleResource

Demonstrates versioned routing for schedule data:

```
GET /public/v{draw}-{results}/regattas/{regatta_id}/schedule
```

### PublicVersionedResultsResource

Demonstrates versioned routing for results data:

```
GET /public/v{draw}-{results}/regattas/{regatta_id}/results
```

**Note:** These are sample resources demonstrating the pattern. In the full implementation, they would serve actual schedule/results data based on the requested version tuple.

## Client Bootstrap Flow

Public clients follow this bootstrap flow:

1. **Attempt to fetch versions:**
   ```
   GET /public/regattas/{id}/versions
   ```

2. **On 401 Unauthorized, create session:**
   ```
   POST /public/session
   ```
   - Server returns `Set-Cookie` with JWT session token

3. **Retry versions request with session cookie:**
   ```
   GET /public/regattas/{id}/versions
   Cookie: regattadesk_public_session={token}
   ```
   - Returns: `{"draw_revision": 2, "results_revision": 3}`

4. **Access versioned content:**
   ```
   GET /public/v2-3/regattas/{id}/schedule
   Cookie: regattadesk_public_session={token}
   ```

## Version Change Behavior

When draw or results are published:
1. Regatta's `draw_revision` or `results_revision` is incremented
2. New version tuple produces new immutable URL
3. Conceptually, old versioned URLs remain valid and cacheable as long as their referenced data is retained
4. Clients poll `/public/regattas/{id}/versions` to detect changes
5. On version change, clients switch to new versioned URLs

> **Note:** In the current implementation, only the current `{draw_revision, results_revision}` tuple is accessible. Requests for older versioned URLs (where either revision does not match the current value) return `404 Not Found`. Historical version storage and serving are planned for future implementation.

**Example (intended behavior):**
- Initial version: `/public/v2-3/regattas/{id}/schedule`
- After results publication: `/public/v2-4/regattas/{id}/schedule`
- Both URLs are valid and immutable

## Testing

### Unit Tests

**PublicVersionedRouteResolverTest** (16 tests)
- Path building with various revision numbers
- Path parsing and version extraction
- Edge cases (zero revisions, large numbers, malformed paths)
- Input validation (negative revisions, null paths)

### Integration Tests

**PublicVersionedRoutesTest** (10 tests)
- Cache headers on versioned routes
- Version changes result in different URLs
- Cache directives (public, max-age, immutable)
- Non-versioned routes have correct cache policies

**PublicCacheControlFilterTest** (2 tests)
- Filter applies correct headers per route pattern
- Existing cache headers are respected

## Performance Considerations

### CDN Caching

With `max-age=31536000, immutable`:
- CDN can cache versioned content for 1 year
- No revalidation required
- Near-instant response for cached content
- Reduces backend load during high-traffic events

### Cache Invalidation

No cache invalidation needed:
- Version changes produce new URLs
- Old URLs remain valid and cached
- No cache purge API required
- Simplified deployment and rollback

## Security Considerations

### Cache Header Security

1. **Session endpoint:** `no-store` prevents session tokens from being cached
2. **Versions endpoint:** `no-store, must-revalidate` ensures clients get latest versions
3. **Versioned content:** `public` allows CDN caching but requires valid session cookie

### Path Validation

- Route resolver validates revision numbers are non-negative
- Path parsing safely handles malformed inputs
- Filter pattern matching prevents unauthorized access to non-public routes

## Future Enhancements

1. **Version-specific data storage:** Currently, resources verify regatta exists but serve current data. Future implementation should store and serve version-specific snapshots.

2. **Conditional requests:** Support `If-None-Match` / `ETag` for efficient revalidation even with `no-store` policies.

3. **Compression:** Enable gzip/brotli compression for versioned text content.

4. **CDN integration:** Add `Vary` headers and `s-maxage` directives for shared cache optimization.

5. **Monitoring:** Add metrics for cache hit rates and version transition tracking.

## References

- PDD: `.mindspec/docs/specs/pdd-v0.1/implementation/bc05-public-experience-and-delivery.md`
- Issue: BC05-002
- Tests: `apps/backend/src/test/java/com/regattadesk/public_api/PublicVersioned*Test.java`
