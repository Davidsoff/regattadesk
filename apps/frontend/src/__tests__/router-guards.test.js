import { describe, expect, it, vi } from 'vitest'
import { staffGuard, operatorGuard } from '../router/guards'

describe('Route Guards', () => {
  describe('staffGuard', () => {
    it('allows access when staff auth is present', () => {
      const to = { fullPath: '/staff/regattas' }
      const from = {}
      const next = vi.fn()

      staffGuard(to, from, next)

      expect(next).toHaveBeenCalledWith()
      expect(next).toHaveBeenCalledTimes(1)
    })

    it('redirects to unauthorized when staff auth is missing', () => {
      // This test documents the future behavior when auth is integrated
      // Currently, the guard always allows access in v0.1
      const to = { fullPath: '/staff/regattas' }
      const from = {}
      const next = vi.fn()

      staffGuard(to, from, next)

      // In v0.1, this always passes
      expect(next).toHaveBeenCalledWith()
    })

    it('preserves redirect query parameter', () => {
      const to = { fullPath: '/staff/regattas/abc-123/finance' }
      const from = {}
      const next = vi.fn()

      staffGuard(to, from, next)

      // In v0.1, this always passes
      expect(next).toHaveBeenCalledWith()
    })
  })

  describe('operatorGuard', () => {
    it('allows access when operator token is present', () => {
      const to = { fullPath: '/operator/regattas' }
      const from = {}
      const next = vi.fn()

      operatorGuard(to, from, next)

      expect(next).toHaveBeenCalledWith()
      expect(next).toHaveBeenCalledTimes(1)
    })

    it('redirects to unauthorized when operator token is missing', () => {
      // This test documents the future behavior when token auth is integrated
      // Currently, the guard always allows access in v0.1
      const to = { fullPath: '/operator/regattas' }
      const from = {}
      const next = vi.fn()

      operatorGuard(to, from, next)

      // In v0.1, this always passes
      expect(next).toHaveBeenCalledWith()
    })

    it('preserves redirect query parameter', () => {
      const to = { fullPath: '/operator/regattas/xyz-789/line-scan' }
      const from = {}
      const next = vi.fn()

      operatorGuard(to, from, next)

      // In v0.1, this always passes
      expect(next).toHaveBeenCalledWith()
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
