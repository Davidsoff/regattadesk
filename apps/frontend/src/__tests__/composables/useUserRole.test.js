import { describe, it, expect, beforeEach } from 'vitest'
import { useUserRole } from '../../composables/useUserRole'

describe('useUserRole', () => {
  beforeEach(() => {
    // Reset any global state between tests
    delete globalThis.__REGATTADESK_AUTH__
  })

  describe('role loading', () => {
    it('returns null role initially', () => {
      const { role } = useUserRole()
      expect(role.value).toBeNull()
    })

    it('loads role from global auth context', async () => {
      globalThis.__REGATTADESK_AUTH__ = {
        user: {
          role: 'staff'
        }
      }

      const { role, loadRole } = useUserRole()
      await loadRole()
      
      expect(role.value).toBe('staff')
    })

    it('loads super_admin role correctly', async () => {
      globalThis.__REGATTADESK_AUTH__ = {
        user: {
          role: 'super_admin'
        }
      }

      const { role, loadRole } = useUserRole()
      await loadRole()
      
      expect(role.value).toBe('super_admin')
    })

    it('handles missing auth context gracefully', async () => {
      const { role, loadRole } = useUserRole()
      await loadRole()
      
      expect(role.value).toBeNull()
    })

    it('handles missing user object gracefully', async () => {
      globalThis.__REGATTADESK_AUTH__ = {}

      const { role, loadRole } = useUserRole()
      await loadRole()
      
      expect(role.value).toBeNull()
    })
  })

  describe('isSuperAdmin computed', () => {
    it('returns false when role is null', () => {
      const { isSuperAdmin } = useUserRole()
      expect(isSuperAdmin.value).toBe(false)
    })

    it('returns false for staff role', async () => {
      globalThis.__REGATTADESK_AUTH__ = {
        user: {
          role: 'staff'
        }
      }

      const { isSuperAdmin, loadRole } = useUserRole()
      await loadRole()
      
      expect(isSuperAdmin.value).toBe(false)
    })

    it('returns true for super_admin role', async () => {
      globalThis.__REGATTADESK_AUTH__ = {
        user: {
          role: 'super_admin'
        }
      }

      const { isSuperAdmin, loadRole } = useUserRole()
      await loadRole()
      
      expect(isSuperAdmin.value).toBe(true)
    })

    it('updates reactively when role changes', async () => {
      const { role, isSuperAdmin, loadRole } = useUserRole()
      
      expect(isSuperAdmin.value).toBe(false)
      
      globalThis.__REGATTADESK_AUTH__ = {
        user: {
          role: 'super_admin'
        }
      }
      
      await loadRole()
      expect(isSuperAdmin.value).toBe(true)
      
      // Simulate role change
      role.value = 'staff'
      expect(isSuperAdmin.value).toBe(false)
    })
  })

  describe('isStaff computed', () => {
    it('returns false when role is null', () => {
      const { isStaff } = useUserRole()
      expect(isStaff.value).toBe(false)
    })

    it('returns true for staff role', async () => {
      globalThis.__REGATTADESK_AUTH__ = {
        user: {
          role: 'staff'
        }
      }

      const { isStaff, loadRole } = useUserRole()
      await loadRole()
      
      expect(isStaff.value).toBe(true)
    })

    it('returns true for super_admin role', async () => {
      globalThis.__REGATTADESK_AUTH__ = {
        user: {
          role: 'super_admin'
        }
      }

      const { isStaff, loadRole } = useUserRole()
      await loadRole()
      
      // super_admin should have staff capabilities
      expect(isStaff.value).toBe(true)
    })

    it('returns true for regatta_admin role', async () => {
      globalThis.__REGATTADESK_AUTH__ = {
        user: {
          role: 'regatta_admin'
        }
      }

      const { isStaff, isRegattaAdmin, loadRole } = useUserRole()
      await loadRole()

      expect(isStaff.value).toBe(true)
      expect(isRegattaAdmin.value).toBe(true)
    })

    it('returns true for other staff-scoped roles', async () => {
      globalThis.__REGATTADESK_AUTH__ = {
        user: {
          role: 'info_desk'
        }
      }

      const { isStaff, isRegattaAdmin, loadRole } = useUserRole()
      await loadRole()

      expect(isStaff.value).toBe(true)
      expect(isRegattaAdmin.value).toBe(false)
    })
  })
})
