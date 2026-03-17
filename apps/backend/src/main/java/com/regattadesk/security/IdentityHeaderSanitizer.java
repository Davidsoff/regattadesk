package com.regattadesk.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Security filter that strips identity headers from requests on untrusted paths.
 * 
 * This filter enforces the trust boundary between edge authentication (Traefik/Authelia)
 * and the backend. It prevents clients from forging identity headers by stripping them
 * from public routes where ForwardAuth is not applied.
 * 
 * This filter runs BEFORE IdentityRequestFilter to ensure headers are sanitized before
 * identity extraction occurs.
 * 
 * Trust model:
 * - Trusted paths: Routes protected by Traefik ForwardAuth middleware (e.g., /api/v1/staff,
 *   /api/v1/regattas/{id}/operator/*, and explicit protected staff regatta routes under /api/v1/regattas/{id})
 * - Untrusted paths: Public routes without ForwardAuth (e.g., /api/health, /api/v1/public, /q/health, /api/v1/regattas/{id}/events)
 * 
 * On untrusted paths, identity headers are stripped to prevent forgery attacks.
 * 
 * @see <a href="../../../../../docs/IDENTITY_FORWARDING.md">Identity Forwarding Contract</a>
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 100) // Run before IdentityRequestFilter
public class IdentityHeaderSanitizer implements ContainerRequestFilter {
    
    private static final Logger LOG = Logger.getLogger(IdentityHeaderSanitizer.class);
    
    /**
     * Paths that are protected by Traefik ForwardAuth middleware.
     * Identity headers on these paths are trusted.
     */
    private static final List<String> TRUSTED_PATH_PREFIXES = List.of(
        "api/v1/staff",
        "api/v1/athletes",
        "api/v1/rulesets",
        "api/v1/jobs",
        "test/auth"  // Test endpoints (only present in test environment)
    );
    
    /**
     * Operator paths pattern matching /api/v1/regattas/{id}/operator/*.
     * This pattern precisely matches the Traefik ForwardAuth route configuration.
     * Only these specific paths under /api/v1/regattas should trust identity headers.
     */
    private static final Pattern OPERATOR_PATH_PATTERN = Pattern.compile(
        "^api/v1/regattas/[^/]+/operator(/.*)?$"
    );

    /**
     * Staff regatta-scoped protected paths under /api/v1/regattas/{id}.
     * These specific route families are covered by Traefik ForwardAuth in front of the backend,
     * so forwarded identity headers remain trusted even though the paths do not live under
     * /api/v1/staff.
     */
    private static final Pattern STAFF_REGATTA_EDGE_PROTECTED_PATH_PATTERN = Pattern.compile(
        "^api/v1/regattas/[^/]+/(finance/(entries|clubs)|entries/[^/]+/payment_status|clubs/[^/]+/payment_status|payments/mark_bulk|invoices(/.*)?|export/printables|adjudication(/.*)?)$"
    );

    /**
     * Staff draw-management paths under /api/v1/regattas/{id}.
     */
    private static final Pattern STAFF_DRAW_PATH_PATTERN = Pattern.compile(
        "^api/v1/regattas/[^/]+($|/blocks(/.*)?|/bib_pools(/.*)?|/draw(/.*)?)$"
    );

    /**
     * Staff setup paths under /api/v1/regattas/{id}.
     * These endpoints are backend role-protected regatta admin/info desk surfaces.
     */
    private static final Pattern STAFF_SETUP_PATH_PATTERN = Pattern.compile(
        "^api/v1/regattas/[^/]+/(event-groups(?:/[0-9a-fA-F-]{36})?|events(?:/[0-9a-fA-F-]{36})?|crews(?:/[0-9a-fA-F-]{36})?|entries(?:/[0-9a-fA-F-]{36}(?:/(withdraw|reinstate))?)?)$"
    );
    
    /**
     * Identity header names that must be stripped from untrusted requests.
     */
    private static final List<String> IDENTITY_HEADERS = List.of(
        IdentityHeaderExtractor.HEADER_REMOTE_USER,
        IdentityHeaderExtractor.HEADER_REMOTE_NAME,
        IdentityHeaderExtractor.HEADER_REMOTE_EMAIL,
        IdentityHeaderExtractor.HEADER_REMOTE_GROUPS
    );
    
    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        
        // Check if this is a trusted path (protected by ForwardAuth)
        boolean isTrustedPath = TRUSTED_PATH_PREFIXES.stream()
            .anyMatch(normalizedPath::startsWith)
            || OPERATOR_PATH_PATTERN.matcher(normalizedPath).matches();
        if (!isTrustedPath) {
            isTrustedPath = STAFF_REGATTA_EDGE_PROTECTED_PATH_PATTERN.matcher(normalizedPath).matches();
        }
        if (!isTrustedPath) {
            isTrustedPath = STAFF_DRAW_PATH_PATTERN.matcher(normalizedPath).matches();
        }
        if (!isTrustedPath) {
            isTrustedPath = STAFF_SETUP_PATH_PATTERN.matcher(normalizedPath).matches();
        }
        
        if (!isTrustedPath) {
            // Untrusted path - strip identity headers to prevent forgery
            boolean headersStripped = false;
            
            for (String header : IDENTITY_HEADERS) {
                if (requestContext.getHeaderString(header) != null) {
                    requestContext.getHeaders().remove(header);
                    headersStripped = true;
                }
            }
            
            if (headersStripped) {
                LOG.warnf("Stripped identity headers from untrusted path: %s - " +
                         "Identity headers are only trusted on ForwardAuth-protected routes", path);
            }
        }
    }
}
