package com.regattadesk.security;

import java.util.Objects;
import java.util.Set;

/**
 * Represents an authenticated principal with identity claims and roles.
 * 
 * Principal is constructed from forwarded identity headers provided by the Authelia/Traefik
 * edge authentication layer. It encapsulates the user's identity, roles, and authorization
 * context for use in backend services.
 * 
 * This class is immutable and thread-safe.
 * 
 * @see <a href="../../../../../docs/IDENTITY_FORWARDING.md">Identity Forwarding Contract</a>
 */
public final class Principal {
    
    private final String username;
    private final String displayName;
    private final String email;
    private final Set<Role> roles;
    
    /**
     * Creates a new Principal with the specified identity claims and roles.
     * 
     * @param username the user's login identifier (from Remote-User header)
     * @param displayName the user's display name (from Remote-Name header)
     * @param email the user's email address (from Remote-Email header)
     * @param roles the set of roles assigned to the user (from Remote-Groups header)
     * @throws IllegalArgumentException if username is null or blank
     */
    public Principal(String username, String displayName, String email, Set<Role> roles) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }
        
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.roles = roles != null ? Set.copyOf(roles) : Set.of();
    }
    
    /**
     * Returns the user's login identifier.
     */
    public String getUsername() {
        return username;
    }
    
    /**
     * Returns the user's display name.
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Returns the user's email address.
     */
    public String getEmail() {
        return email;
    }
    
    /**
     * Returns an unmodifiable set of the user's roles.
     */
    public Set<Role> getRoles() {
        return roles;
    }
    
    /**
     * Checks if the principal has the specified role.
     * 
     * @param role the role to check
     * @return true if the principal has the role, false otherwise
     */
    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
    
    /**
     * Checks if the principal has any of the specified roles.
     * 
     * @param requiredRoles the roles to check
     * @return true if the principal has at least one of the roles, false otherwise
     */
    public boolean hasAnyRole(Role... requiredRoles) {
        if (requiredRoles == null || requiredRoles.length == 0) {
            return true;
        }
        
        for (Role role : requiredRoles) {
            if (hasRole(role)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if the principal has all of the specified roles.
     * 
     * @param requiredRoles the roles to check
     * @return true if the principal has all of the roles, false otherwise
     */
    public boolean hasAllRoles(Role... requiredRoles) {
        if (requiredRoles == null || requiredRoles.length == 0) {
            return true;
        }
        
        for (Role role : requiredRoles) {
            if (!hasRole(role)) {
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Principal principal = (Principal) o;
        return Objects.equals(username, principal.username) &&
               Objects.equals(displayName, principal.displayName) &&
               Objects.equals(email, principal.email) &&
               Objects.equals(roles, principal.roles);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(username, displayName, email, roles);
    }
    
    @Override
    public String toString() {
        return "Principal{" +
               "username='" + username + '\'' +
               ", displayName='" + displayName + '\'' +
               ", email='" + email + '\'' +
               ", roles=" + roles +
               '}';
    }
}
