package com.regattadesk.security;

import jakarta.enterprise.context.RequestScoped;

/**
 * Holds the security context for the current request.
 * 
 * This is a request-scoped bean that stores the authenticated Principal
 * for the duration of a single HTTP request. It is populated by the
 * IdentityRequestFilter from forwarded identity headers.
 * 
 * Thread-safe within the scope of a single request as it is request-scoped.
 */
@RequestScoped
public class SecurityContext {
    
    private Principal principal;
    
    /**
     * Returns the authenticated principal for the current request.
     * 
     * @return the principal, or null if no authentication is present
     */
    public Principal getPrincipal() {
        return principal;
    }
    
    /**
     * Sets the authenticated principal for the current request.
     * 
     * @param principal the principal to set
     */
    public void setPrincipal(Principal principal) {
        this.principal = principal;
    }
    
    /**
     * Checks if the current request is authenticated.
     * 
     * @return true if a principal is present, false otherwise
     */
    public boolean isAuthenticated() {
        return principal != null;
    }
    
    /**
     * Checks if the current principal has the specified role.
     * 
     * @param role the role to check
     * @return true if authenticated and has the role, false otherwise
     */
    public boolean hasRole(Role role) {
        return principal != null && principal.hasRole(role);
    }
    
    /**
     * Checks if the current principal has any of the specified roles.
     * 
     * @param roles the roles to check
     * @return true if authenticated and has at least one role, false otherwise
     */
    public boolean hasAnyRole(Role... roles) {
        return principal != null && principal.hasAnyRole(roles);
    }
}
