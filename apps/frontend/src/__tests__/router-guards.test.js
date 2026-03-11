import { describe, expect, it, vi } from 'vitest'
import { staffGuard, operatorGuard } from '../router/guards'

function clearAuthState() {
  globalThis.__REGATTADESK_AUTH__ = {}
  try {
    globalThis.localStorage?.removeItem('rd_operator_token')
    globalThis.localStorage?.removeItem('rd_staff_authenticated')
  } catch (err) {
    // localStorage may not be available in all test environments (e.g., jsdom restrictions)
    void err;
  }
}

describe('Route Guards', () => {
  clearAuthState()

  describe('staffGuard', () => {
    it('allows access when staff auth is present', () => {
      clearAuthState()
      globalThis.__REGATTADESK_AUTH__.staffAuthenticated = true
      const to = { fullPath: '/staff/regattas' }
      const from = {}
      const next = vi.fn()

      staffGuard(to, from, next)

      expect(next).toHaveBeenCalledWith()
      expect(next).toHaveBeenCalledTimes(1)
    })

    it('redirects to unauthorized when staff auth is missing', () => {
      clearAuthState()
      const to = { fullPath: '/staff/regattas' }
      const from = {}
      const next = vi.fn()

      staffGuard(to, from, next)

      expect(next).toHaveBeenCalledWith({
        name: 'unauthorized',
        query: { redirect: '/staff/regattas' },
      })
    })

    it('preserves redirect query parameter', () => {
      clearAuthState()
      const to = { fullPath: '/staff/regattas/abc-123/finance' }
      const from = {}
      const next = vi.fn()

      staffGuard(to, from, next)

      expect(next).toHaveBeenCalledWith({
        name: 'unauthorized',
        query: { redirect: '/staff/regattas/abc-123/finance' },
      })
    })
  })

  describe('operatorGuard', () => {
    it('allows access when operator token is present', () => {
      clearAuthState()
      globalThis.__REGATTADESK_AUTH__.operatorToken = 'token-123'
      const to = { fullPath: '/operator/regattas' }
      const from = {}
      const next = vi.fn()

      operatorGuard(to, from, next)

      expect(next).toHaveBeenCalledWith()
      expect(next).toHaveBeenCalledTimes(1)
    })

    it('allows access and persists token from query parameter', () => {
      clearAuthState()
      const to = { fullPath: '/operator/regattas', query: { token: 'query-token' } }
      const from = {}
      const next = vi.fn()

      operatorGuard(to, from, next)

      expect(next).toHaveBeenCalledWith()
      expect(globalThis.__REGATTADESK_AUTH__.operatorToken).toBe('query-token')
    })

    it('redirects to unauthorized when operator token is missing', () => {
      clearAuthState()
      const to = { fullPath: '/operator/regattas' }
      const from = {}
      const next = vi.fn()

      operatorGuard(to, from, next)

      expect(next).toHaveBeenCalledWith({
        name: 'unauthorized',
        query: { redirect: '/operator/regattas' },
      })
    })

    it('preserves redirect query parameter', () => {
      clearAuthState()
      const to = { fullPath: '/operator/regattas/xyz-789/line-scan' }
      const from = {}
      const next = vi.fn()

      operatorGuard(to, from, next)

      expect(next).toHaveBeenCalledWith({
        name: 'unauthorized',
        query: { redirect: '/operator/regattas/xyz-789/line-scan' },
      })
    })
  })

  describe('Guard behavior consistency', () => {
    it('both guards have same signature', () => {
      expect(staffGuard).toBeInstanceOf(Function)
      expect(operatorGuard).toBeInstanceOf(Function)
      expect(staffGuard.length).toBe(3)
      expect(operatorGuard.length).toBe(3)
    })

    it('both guards call next function', () => {
      const to = { fullPath: '/test' }
      const from = {}
      const staffNext = vi.fn()
      const operatorNext = vi.fn()

      staffGuard(to, from, staffNext)
      operatorGuard(to, from, operatorNext)

      expect(staffNext).toHaveBeenCalled()
      expect(operatorNext).toHaveBeenCalled()
    })
  })
})
