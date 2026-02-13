package com.regattadesk.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentityHeaderExtractorTest {
    
    private IdentityHeaderExtractor extractor;
    
    @BeforeEach
    void setUp() {
        extractor = new IdentityHeaderExtractor();
    }
    
    @Test
    void testExtractPrincipal_AllHeaders() {
        Principal principal = extractor.extractPrincipal(
            "testuser",
            "Test User",
            "test@example.com",
            "regatta_admin,info_desk"
        );
        
        assertEquals("testuser", principal.getUsername());
        assertEquals("Test User", principal.getDisplayName());
        assertEquals("test@example.com", principal.getEmail());
        assertEquals(2, principal.getRoles().size());
        assertTrue(principal.hasRole(Role.REGATTA_ADMIN));
        assertTrue(principal.hasRole(Role.INFO_DESK));
    }
    
    @Test
    void testExtractPrincipal_MinimalHeaders() {
        Principal principal = extractor.extractPrincipal(
            "testuser",
            null,
            null,
            null
        );
        
        assertEquals("testuser", principal.getUsername());
        assertNull(principal.getDisplayName());
        assertNull(principal.getEmail());
        assertTrue(principal.getRoles().isEmpty());
    }
    
    @Test
    void testExtractPrincipal_MissingRemoteUser() {
        assertThrows(IdentityHeaderExtractor.InvalidIdentityHeaderException.class, () ->
            extractor.extractPrincipal(null, "Test", "test@example.com", "regatta_admin")
        );
    }
    
    @Test
    void testExtractPrincipal_BlankRemoteUser() {
        assertThrows(IdentityHeaderExtractor.InvalidIdentityHeaderException.class, () ->
            extractor.extractPrincipal("   ", "Test", "test@example.com", "regatta_admin")
        );
    }
    
    @Test
    void testExtractPrincipal_SingleRole() {
        Principal principal = extractor.extractPrincipal(
            "testuser",
            "Test User",
            "test@example.com",
            "super_admin"
        );
        
        assertEquals(1, principal.getRoles().size());
        assertTrue(principal.hasRole(Role.SUPER_ADMIN));
    }
    
    @Test
    void testExtractPrincipal_MultipleRoles() {
        Principal principal = extractor.extractPrincipal(
            "testuser",
            "Test User",
            "test@example.com",
            "regatta_admin,head_of_jury,info_desk,financial_manager"
        );
        
        assertEquals(4, principal.getRoles().size());
        assertTrue(principal.hasRole(Role.REGATTA_ADMIN));
        assertTrue(principal.hasRole(Role.HEAD_OF_JURY));
        assertTrue(principal.hasRole(Role.INFO_DESK));
        assertTrue(principal.hasRole(Role.FINANCIAL_MANAGER));
    }
    
    @Test
    void testExtractPrincipal_AllRoles() {
        Principal principal = extractor.extractPrincipal(
            "testuser",
            "Test User",
            "test@example.com",
            "super_admin,regatta_admin,head_of_jury,info_desk,financial_manager,operator"
        );
        
        assertEquals(6, principal.getRoles().size());
        assertTrue(principal.hasRole(Role.SUPER_ADMIN));
        assertTrue(principal.hasRole(Role.REGATTA_ADMIN));
        assertTrue(principal.hasRole(Role.HEAD_OF_JURY));
        assertTrue(principal.hasRole(Role.INFO_DESK));
        assertTrue(principal.hasRole(Role.FINANCIAL_MANAGER));
        assertTrue(principal.hasRole(Role.OPERATOR));
    }
    
    @Test
    void testExtractPrincipal_UnknownRolesIgnored() {
        Principal principal = extractor.extractPrincipal(
            "testuser",
            "Test User",
            "test@example.com",
            "regatta_admin,unknown_role,info_desk,another_unknown"
        );
        
        // Only known roles should be included
        assertEquals(2, principal.getRoles().size());
        assertTrue(principal.hasRole(Role.REGATTA_ADMIN));
        assertTrue(principal.hasRole(Role.INFO_DESK));
    }
    
    @Test
    void testExtractPrincipal_RolesWithWhitespace() {
        Principal principal = extractor.extractPrincipal(
            "testuser",
            "Test User",
            "test@example.com",
            " regatta_admin , info_desk , operator "
        );
        
        assertEquals(3, principal.getRoles().size());
        assertTrue(principal.hasRole(Role.REGATTA_ADMIN));
        assertTrue(principal.hasRole(Role.INFO_DESK));
        assertTrue(principal.hasRole(Role.OPERATOR));
    }
    
    @Test
    void testExtractPrincipal_EmptyRolesString() {
        Principal principal = extractor.extractPrincipal(
            "testuser",
            "Test User",
            "test@example.com",
            ""
        );
        
        assertTrue(principal.getRoles().isEmpty());
    }
    
    @Test
    void testExtractPrincipal_OnlyCommaSeparators() {
        Principal principal = extractor.extractPrincipal(
            "testuser",
            "Test User",
            "test@example.com",
            ",,,,"
        );
        
        assertTrue(principal.getRoles().isEmpty());
    }
    
    @Test
    void testExtractPrincipal_WhitespaceInFields() {
        Principal principal = extractor.extractPrincipal(
            "  testuser  ",
            "  Test User  ",
            "  test@example.com  ",
            "regatta_admin"
        );
        
        assertEquals("testuser", principal.getUsername());
        assertEquals("Test User", principal.getDisplayName());
        assertEquals("test@example.com", principal.getEmail());
    }
}
