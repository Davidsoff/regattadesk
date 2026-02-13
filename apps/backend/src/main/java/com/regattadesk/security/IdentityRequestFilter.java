package com.regattadesk.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * JAX-RS request filter that extracts identity from forwarded headers and injects
 * the authenticated principal into the request-scoped SecurityContext.
 * 
 * This filter runs early in the request processing chain (AUTHENTICATION priority)
 * to make the principal available to subsequent filters and resource methods.
 * 
 * For protected endpoints, the presence of identity headers indicates that edge
 * authentication (Authelia/Traefik) has successfully authenticated the request.
 * For public endpoints, headers will be absent and the SecurityContext will
 * remain unauthenticated.
 * 
 * @see <a href="../../../../../docs/IDENTITY_FORWARDING.md">Identity Forwarding Contract</a>
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class IdentityRequestFilter implements ContainerRequestFilter {
    
    private static final Logger LOG = Logger.getLogger(IdentityRequestFilter.class);
    
    @Inject
    IdentityHeaderExtractor headerExtractor;
    
    @Inject
    SecurityContext securityContext;
    
    @Override
    public void filter(ContainerRequestContext requestContext) {
        // Extract headers
        String remoteUser = requestContext.getHeaderString(IdentityHeaderExtractor.HEADER_REMOTE_USER);
        String remoteName = requestContext.getHeaderString(IdentityHeaderExtractor.HEADER_REMOTE_NAME);
        String remoteEmail = requestContext.getHeaderString(IdentityHeaderExtractor.HEADER_REMOTE_EMAIL);
        String remoteGroups = requestContext.getHeaderString(IdentityHeaderExtractor.HEADER_REMOTE_GROUPS);
        
        // If no identity headers present, this is an unauthenticated request
        // (either a public endpoint or a request that should have been blocked at the edge)
        if (remoteUser == null) {
            LOG.debug("No identity headers present - request is unauthenticated");
            return;
        }
        
        try {
            // Extract principal from headers
            Principal principal = headerExtractor.extractPrincipal(
                remoteUser, remoteName, remoteEmail, remoteGroups
            );
            
            // Store in request-scoped security context
            securityContext.setPrincipal(principal);
            
            LOG.debugf("Authenticated request for user: %s with roles: %s", 
                      principal.getUsername(), principal.getRoles());
            
        } catch (IdentityHeaderExtractor.InvalidIdentityHeaderException e) {
            // Log the error but don't fail the request here
            // Authorization filters will handle access control
            LOG.warnf("Failed to extract principal from identity headers: %s", e.getMessage());
        }
    }
}
