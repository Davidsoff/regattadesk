package com.regattadesk.security;

import java.util.HashSet;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Extracts and validates identity information from forwarded headers.
 * 
 * This service parses headers forwarded by Authelia/Traefik edge authentication
 * and constructs a Principal object representing the authenticated user.
 * 
 * Header Contract:
 * - Remote-User: username/login identifier
 * - Remote-Name: display name
 * - Remote-Email: email address
 * - Remote-Groups: comma-separated list of roles
 * 
 * @see <a href="../../../../../docs/IDENTITY_FORWARDING.md">Identity Forwarding Contract</a>
 */
@ApplicationScoped
public class IdentityHeaderExtractor {
    
    /** Header name for username from Authelia */
    public static final String HEADER_REMOTE_USER = "Remote-User";
    
    /** Header name for display name from Authelia */
    public static final String HEADER_REMOTE_NAME = "Remote-Name";
    
    /** Header name for email from Authelia */
    public static final String HEADER_REMOTE_EMAIL = "Remote-Email";
    
    /** Header name for groups/roles from Authelia */
    public static final String HEADER_REMOTE_GROUPS = "Remote-Groups";
    
    /**
     * Extracts a Principal from the provided headers.
     * 
     * @param remoteUser the Remote-User header value (required)
     * @param remoteName the Remote-Name header value (optional)
     * @param remoteEmail the Remote-Email header value (optional)
     * @param remoteGroups the Remote-Groups header value (optional, comma-separated)
     * @return a Principal representing the authenticated user
     * @throws InvalidIdentityHeaderException if Remote-User is missing or invalid
     */
    public Principal extractPrincipal(String remoteUser, String remoteName, 
                                     String remoteEmail, String remoteGroups) {
        // Validate required header
        if (remoteUser == null || remoteUser.isBlank()) {
            throw new InvalidIdentityHeaderException(
                "Missing or invalid Remote-User header - authentication required"
            );
        }
        
        // Parse roles from Remote-Groups
        Set<Role> roles = parseRoles(remoteGroups);
        
        // Create and return principal
        return new Principal(
            remoteUser.trim(),
            remoteName != null ? remoteName.trim() : null,
            remoteEmail != null ? remoteEmail.trim() : null,
            roles
        );
    }
    
    /**
     * Parses roles from a comma-separated groups string.
     * 
     * @param remoteGroups comma-separated list of group names
     * @return set of parsed roles (empty set if input is null or empty)
     */
    private Set<Role> parseRoles(String remoteGroups) {
        Set<Role> roles = new HashSet<>();
        
        if (remoteGroups == null || remoteGroups.isBlank()) {
            return roles;
        }
        
        // Split by comma and parse each group
        String[] groups = remoteGroups.split(",");
        for (String group : groups) {
            String trimmedGroup = group.trim();
            if (!trimmedGroup.isEmpty()) {
                Role role = Role.fromGroupName(trimmedGroup);
                if (role != null) {
                    roles.add(role);
                }
                // Silently ignore unrecognized groups - allows for future role additions
                // or groups used by other systems without breaking existing deployments
            }
        }
        
        return roles;
    }
    
    /**
     * Exception thrown when identity headers are missing or invalid.
     */
    public static class InvalidIdentityHeaderException extends RuntimeException {
        public InvalidIdentityHeaderException(String message) {
            super(message);
        }
    }
}
