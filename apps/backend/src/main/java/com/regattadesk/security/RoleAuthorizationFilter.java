package com.regattadesk.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.lang.reflect.Method;

/**
 * JAX-RS request filter that enforces role-based authorization for endpoints
 * annotated with @RequireRole.
 * 
 * This filter runs after authentication (AUTHORIZATION priority) and checks
 * if the authenticated principal has one of the required roles. If not,
 * a 403 Forbidden response is returned.
 * 
 * The filter checks both method-level and class-level @RequireRole annotations,
 * with method-level taking precedence.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class RoleAuthorizationFilter implements ContainerRequestFilter {
    
    private static final Logger LOG = Logger.getLogger(RoleAuthorizationFilter.class);
    
    @Context
    ResourceInfo resourceInfo;
    
    @Inject
    SecurityContext securityContext;
    
    @Override
    public void filter(ContainerRequestContext requestContext) {
        // Get the resource method being invoked
        Method method = resourceInfo.getResourceMethod();
        Class<?> resourceClass = resourceInfo.getResourceClass();
        
        // Check method-level annotation first
        RequireRole methodAnnotation = method.getAnnotation(RequireRole.class);
        RequireRole classAnnotation = resourceClass.getAnnotation(RequireRole.class);
        
        // Method annotation takes precedence over class annotation
        RequireRole annotation = methodAnnotation != null ? methodAnnotation : classAnnotation;
        
        // If no @RequireRole annotation, no authorization check needed
        if (annotation == null) {
            return;
        }
        
        // Get required roles
        Role[] requiredRoles = annotation.value();
        
        // Check if principal is authenticated
        if (!securityContext.isAuthenticated()) {
            LOG.warnf("Unauthenticated access attempt to protected endpoint: %s.%s",
                     resourceClass.getSimpleName(), method.getName());
            throw new ForbiddenException("Authentication required");
        }
        
        // Check if principal has any of the required roles
        Principal principal = securityContext.getPrincipal();
        if (!principal.hasAnyRole(requiredRoles)) {
            LOG.warnf("Unauthorized access attempt by user %s (roles: %s) to endpoint requiring roles: %s",
                     principal.getUsername(), principal.getRoles(), 
                     java.util.Arrays.toString(requiredRoles));
            throw new ForbiddenException("Insufficient permissions");
        }
        
        LOG.debugf("Authorized access for user %s to %s.%s",
                  principal.getUsername(), resourceClass.getSimpleName(), method.getName());
    }
}
