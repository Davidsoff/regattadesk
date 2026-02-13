package com.regattadesk.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoleTest {
    
    @Test
    void testFromGroupName_ValidRoles() {
        assertEquals(Role.SUPER_ADMIN, Role.fromGroupName("super_admin"));
        assertEquals(Role.REGATTA_ADMIN, Role.fromGroupName("regatta_admin"));
        assertEquals(Role.HEAD_OF_JURY, Role.fromGroupName("head_of_jury"));
        assertEquals(Role.INFO_DESK, Role.fromGroupName("info_desk"));
        assertEquals(Role.FINANCIAL_MANAGER, Role.fromGroupName("financial_manager"));
        assertEquals(Role.OPERATOR, Role.fromGroupName("operator"));
    }
    
    @Test
    void testFromGroupName_UnknownGroup() {
        assertNull(Role.fromGroupName("unknown_role"));
        assertNull(Role.fromGroupName("admin"));
        assertNull(Role.fromGroupName("user"));
    }
    
    @Test
    void testFromGroupName_InvalidInput() {
        assertNull(Role.fromGroupName(null));
        assertNull(Role.fromGroupName(""));
        assertNull(Role.fromGroupName("   "));
    }
    
    @Test
    void testGetGroupName() {
        assertEquals("super_admin", Role.SUPER_ADMIN.getGroupName());
        assertEquals("regatta_admin", Role.REGATTA_ADMIN.getGroupName());
        assertEquals("head_of_jury", Role.HEAD_OF_JURY.getGroupName());
        assertEquals("info_desk", Role.INFO_DESK.getGroupName());
        assertEquals("financial_manager", Role.FINANCIAL_MANAGER.getGroupName());
        assertEquals("operator", Role.OPERATOR.getGroupName());
    }
}
