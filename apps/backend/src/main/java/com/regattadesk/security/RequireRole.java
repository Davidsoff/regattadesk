package com.regattadesk.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declaratively require specific roles for endpoint access.
 * 
 * When applied to a resource method or class, the RoleAuthorizationFilter
 * will enforce that the authenticated principal has at least one of the
 * specified roles. If the principal is not authenticated or lacks the
 * required role, a 403 Forbidden response is returned.
 * 
 * Example usage:
 * <pre>
 * {@code
 * @GET
 * @Path("/entries")
 * @RequireRole({Role.REGATTA_ADMIN, Role.INFO_DESK})
 * public List<Entry> listEntries() {
 *     // Only accessible by regatta_admin or info_desk roles
 * }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    /**
     * The roles that are authorized to access the annotated endpoint.
     * If the principal has any one of these roles, access is granted.
     * 
     * @return array of required roles
     */
    Role[] value();
}
