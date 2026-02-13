package com.regattadesk.security;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PrincipalTest {
    
    @Test
    void testConstruction_ValidPrincipal() {
        Set<Role> roles = Set.of(Role.REGATTA_ADMIN, Role.INFO_DESK);
        Principal principal = new Principal(
            "testuser",
            "Test User",
            "test@example.com",
            roles
        );
        
        assertEquals("testuser", principal.getUsername());
        assertEquals("Test User", principal.getDisplayName());
        assertEquals("test@example.com", principal.getEmail());
        assertEquals(2, principal.getRoles().size());
        assertTrue(principal.getRoles().contains(Role.REGATTA_ADMIN));
        assertTrue(principal.getRoles().contains(Role.INFO_DESK));
    }
    
    @Test
    void testConstruction_NullUsername() {
        assertThrows(IllegalArgumentException.class, () -> 
            new Principal(null, "Test", "test@example.com", Set.of())
        );
    }
    
    @Test
    void testConstruction_BlankUsername() {
        assertThrows(IllegalArgumentException.class, () -> 
            new Principal("   ", "Test", "test@example.com", Set.of())
        );
    }
    
    @Test
    void testConstruction_NullRoles() {
        Principal principal = new Principal("testuser", "Test", "test@example.com", null);
        assertNotNull(principal.getRoles());
        assertTrue(principal.getRoles().isEmpty());
    }
    
    @Test
    void testConstruction_OptionalFieldsNull() {
        Principal principal = new Principal("testuser", null, null, Set.of());
        assertEquals("testuser", principal.getUsername());
        assertNull(principal.getDisplayName());
        assertNull(principal.getEmail());
    }
    
    @Test
    void testHasRole_SingleRole() {
        Principal principal = new Principal(
            "testuser", "Test", "test@example.com",
            Set.of(Role.REGATTA_ADMIN)
        );
        
        assertTrue(principal.hasRole(Role.REGATTA_ADMIN));
        assertFalse(principal.hasRole(Role.SUPER_ADMIN));
        assertFalse(principal.hasRole(Role.OPERATOR));
    }
    
    @Test
    void testHasAnyRole_MultipleRoles() {
        Principal principal = new Principal(
            "testuser", "Test", "test@example.com",
            Set.of(Role.REGATTA_ADMIN, Role.INFO_DESK)
        );
        
        assertTrue(principal.hasAnyRole(Role.REGATTA_ADMIN));
        assertTrue(principal.hasAnyRole(Role.INFO_DESK));
        assertTrue(principal.hasAnyRole(Role.REGATTA_ADMIN, Role.OPERATOR));
        assertTrue(principal.hasAnyRole(Role.OPERATOR, Role.INFO_DESK));
        assertFalse(principal.hasAnyRole(Role.SUPER_ADMIN, Role.OPERATOR));
    }
    
    @Test
    void testHasAnyRole_EmptyArray() {
        Principal principal = new Principal(
            "testuser", "Test", "test@example.com",
            Set.of(Role.REGATTA_ADMIN)
        );
        
        assertTrue(principal.hasAnyRole());
    }
    
    @Test
    void testHasAllRoles_Success() {
        Principal principal = new Principal(
            "testuser", "Test", "test@example.com",
            Set.of(Role.REGATTA_ADMIN, Role.INFO_DESK, Role.OPERATOR)
        );
        
        assertTrue(principal.hasAllRoles(Role.REGATTA_ADMIN));
        assertTrue(principal.hasAllRoles(Role.REGATTA_ADMIN, Role.INFO_DESK));
        assertTrue(principal.hasAllRoles(Role.INFO_DESK, Role.OPERATOR));
    }
    
    @Test
    void testHasAllRoles_Failure() {
        Principal principal = new Principal(
            "testuser", "Test", "test@example.com",
            Set.of(Role.REGATTA_ADMIN)
        );
        
        assertFalse(principal.hasAllRoles(Role.REGATTA_ADMIN, Role.OPERATOR));
        assertFalse(principal.hasAllRoles(Role.SUPER_ADMIN));
    }
    
    @Test
    void testHasAllRoles_EmptyArray() {
        Principal principal = new Principal(
            "testuser", "Test", "test@example.com",
            Set.of(Role.REGATTA_ADMIN)
        );
        
        assertTrue(principal.hasAllRoles());
    }
    
    @Test
    void testImmutability() {
        Set<Role> originalRoles = Set.of(Role.REGATTA_ADMIN);
        Principal principal = new Principal("testuser", "Test", "test@example.com", originalRoles);
        
        // Attempt to modify returned set should fail
        assertThrows(UnsupportedOperationException.class, () -> 
            principal.getRoles().add(Role.SUPER_ADMIN)
        );
    }
    
    @Test
    void testEquals() {
        Principal p1 = new Principal("testuser", "Test", "test@example.com", 
                                    Set.of(Role.REGATTA_ADMIN));
        Principal p2 = new Principal("testuser", "Test", "test@example.com", 
                                    Set.of(Role.REGATTA_ADMIN));
        Principal p3 = new Principal("otheruser", "Test", "test@example.com", 
                                    Set.of(Role.REGATTA_ADMIN));
        
        assertEquals(p1, p2);
        assertNotEquals(p1, p3);
    }
    
    @Test
    void testHashCode() {
        Principal p1 = new Principal("testuser", "Test", "test@example.com", 
                                    Set.of(Role.REGATTA_ADMIN));
        Principal p2 = new Principal("testuser", "Test", "test@example.com", 
                                    Set.of(Role.REGATTA_ADMIN));
        
        assertEquals(p1.hashCode(), p2.hashCode());
    }
}
