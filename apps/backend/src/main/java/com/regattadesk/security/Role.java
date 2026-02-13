package com.regattadesk.security;

/**
 * Role enumeration matching the identity forwarding contract from Authelia/Traefik edge.
 * 
 * Roles are forwarded via the Remote-Groups header and define authorization boundaries
 * for staff, operators, and administrators.
 * 
 * @see <a href="../../../../../docs/IDENTITY_FORWARDING.md">Identity Forwarding Contract</a>
 */
public enum Role {
    /**
     * Global administrative authority - access to all regattas and system-wide operations.
     */
    SUPER_ADMIN("super_admin"),
    
    /**
     * Full access within a specific regatta scope - manage configuration, events, entries,
     * perform draw, publish results.
     */
    REGATTA_ADMIN("regatta_admin"),
    
    /**
     * Jury and adjudication authority - approve entries, close investigations,
     * assign penalties/exclusions/DSQs, approve final results.
     */
    HEAD_OF_JURY("head_of_jury"),
    
    /**
     * Information desk operations - crew mutations, missing/changed bibs,
     * withdrawals (DNS, DNF status updates).
     */
    INFO_DESK("info_desk"),
    
    /**
     * Financial operations - mark entries as paid/unpaid, bulk payment updates,
     * generate invoices.
     */
    FINANCIAL_MANAGER("financial_manager"),
    
    /**
     * Line-scan camera operators - access operator PWA interface, create and link
     * timing markers, offline-capable capture workflows.
     */
    OPERATOR("operator");
    
    private final String groupName;
    
    Role(String groupName) {
        this.groupName = groupName;
    }
    
    /**
     * Returns the group name as it appears in the Remote-Groups header.
     */
    public String getGroupName() {
        return groupName;
    }
    
    /**
     * Parse a role from its group name.
     * 
     * @param groupName the group name from the Remote-Groups header
     * @return the matching Role, or null if no match found
     */
    public static Role fromGroupName(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return null;
        }
        
        for (Role role : values()) {
            if (role.groupName.equals(groupName)) {
                return role;
            }
        }
        
        return null;
    }
}
