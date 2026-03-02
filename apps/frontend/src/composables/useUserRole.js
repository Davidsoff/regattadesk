/**
 * User role composable for RegattaDesk
 * 
 * Provides access to current user's role and computed role checks.
 * Used for role-based visibility and authorization in UI components.
 * 
 * Usage:
 * const { role, isSuperAdmin, isStaff, loadRole } = useUserRole()
 * 
 * await loadRole() // Load role from auth context
 * 
 * if (isSuperAdmin.value) {
 *   // Show super_admin-only features
 * }
 */

import { ref, computed } from 'vue'

/**
 * Get user role composable
 * 
 * @returns {Object} Role state and computed checks
 * @returns {Ref<string|null>} role - Current user role
 * @returns {ComputedRef<boolean>} isSuperAdmin - True if user is super_admin
 * @returns {ComputedRef<boolean>} isStaff - True if user is staff or super_admin
 * @returns {Function} loadRole - Load role from auth context
 */
export function useUserRole() {
  const role = ref(null)

  /**
   * Load user role from global auth context
   * 
   * In production, this reads from __REGATTADESK_AUTH__ which is
   * injected by the authentication proxy (Authelia via Traefik).
   * 
   * @returns {Promise<void>}
   */
  async function loadRole() {
    try {
      // Read from global auth context injected by auth proxy
      if (
        typeof globalThis.__REGATTADESK_AUTH__ === 'object' &&
        globalThis.__REGATTADESK_AUTH__ !== null &&
        typeof globalThis.__REGATTADESK_AUTH__.user === 'object' &&
        globalThis.__REGATTADESK_AUTH__.user !== null
      ) {
        role.value = globalThis.__REGATTADESK_AUTH__.user.role || null
      } else {
        role.value = null
      }
    } catch (error) {
      console.warn('Failed to load user role:', error)
      role.value = null
    }
  }

  /**
   * Check if user is super_admin
   */
  const isSuperAdmin = computed(() => role.value === 'super_admin')

  /**
   * Check if user has staff capabilities (staff or super_admin)
   */
  const isStaff = computed(() => 
    role.value === 'staff' || role.value === 'super_admin'
  )

  return {
    role,
    isSuperAdmin,
    isStaff,
    loadRole
  }
}
