import { describe, expect, it, beforeEach } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { staffGuard, operatorGuard } from '../router/guards'

/**
 * Create a test router with the same route structure as the main router
 * but using in-memory history for testing
 */
function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/',
        redirect: '/staff/regattas'
      },
      {
        path: '/staff',
        component: { template: '<div>Staff Layout</div>' },
        beforeEnter: staffGuard,
        children: [
          {
            path: '',
            redirect: '/staff/regattas'
          },
          {
            path: 'regattas',
            name: 'staff-regattas',
            component: { template: '<div>Staff Regattas</div>' }
          },
          {
            path: 'regattas/:regattaId',
            name: 'staff-regatta-detail',
            component: { template: '<div>Staff Regatta Detail</div>' }
          },
          {
            path: 'regattas/:regattaId/finance',
            name: 'staff-regatta-finance',
            component: { template: '<div>Staff Finance</div>' }
          }
        ]
      },
      {
        path: '/operator',
        component: { template: '<div>Operator Layout</div>' },
        beforeEnter: operatorGuard,
        children: [
          {
            path: '',
            redirect: '/operator/regattas'
          },
          {
            path: 'regattas',
            name: 'operator-regattas',
            component: { template: '<div>Operator Regattas</div>' }
          },
          {
            path: 'regattas/:regattaId',
            name: 'operator-regatta-detail',
            component: { template: '<div>Operator Regatta Detail</div>' }
          },
          {
            path: 'regattas/:regattaId/line-scan',
            name: 'operator-line-scan',
            component: { template: '<div>Operator Line Scan</div>' }
          }
        ]
      },
      {
        path: '/public/v:drawRevision-:resultsRevision',
        component: { template: '<div>Public Layout</div>' },
        children: [
          {
            path: '',
            redirect: (to) => `/public/v${to.params.drawRevision}-${to.params.resultsRevision}/schedule`
          },
          {
            path: 'schedule',
            name: 'public-schedule',
            component: { template: '<div>Public Schedule</div>' }
          },
          {
            path: 'results',
            name: 'public-results',
            component: { template: '<div>Public Results</div>' }
          }
        ]
      },
      {
        path: '/unauthorized',
        name: 'unauthorized',
        component: { template: '<div>Unauthorized</div>' }
      },
      {
        path: '/:pathMatch(.*)*',
        name: 'not-found',
        component: { template: '<div>Not Found</div>' }
      }
    ]
  })
}

describe('Router Configuration', () => {
  let router

  beforeEach(() => {
    window.__REGATTADESK_AUTH__ = {
      staffAuthenticated: true,
      operatorToken: 'operator-token',
    }
    router = createTestRouter()
  })

  describe('Root redirect', () => {
    it('redirects / to /staff/regattas', async () => {
      await router.push('/')
      await router.isReady()
      
      expect(router.currentRoute.value.path).toBe('/staff/regattas')
      expect(router.currentRoute.value.name).toBe('staff-regattas')
    })
  })

  describe('Staff routes', () => {
    it('resolves /staff/regattas route', async () => {
      await router.push('/staff/regattas')
      await router.isReady()
      
      expect(router.currentRoute.value.path).toBe('/staff/regattas')
      expect(router.currentRoute.value.name).toBe('staff-regattas')
    })

    it('resolves /staff/regattas/:regattaId route with params', async () => {
      const regattaId = 'abc-123-def-456'
      await router.push(`/staff/regattas/${regattaId}`)
      await router.isReady()
      
      expect(router.currentRoute.value.path).toBe(`/staff/regattas/${regattaId}`)
      expect(router.currentRoute.value.name).toBe('staff-regatta-detail')
      expect(router.currentRoute.value.params.regattaId).toBe(regattaId)
    })

    it('resolves /staff/regattas/:regattaId/finance route', async () => {
      const regattaId = 'test-uuid-123'
      await router.push(`/staff/regattas/${regattaId}/finance`)
      await router.isReady()
      
      expect(router.currentRoute.value.path).toBe(`/staff/regattas/${regattaId}/finance`)
      expect(router.currentRoute.value.name).toBe('staff-regatta-finance')
      expect(router.currentRoute.value.params.regattaId).toBe(regattaId)
    })

    it('redirects /staff to /staff/regattas', async () => {
      await router.push('/staff')
      await router.isReady()
      
      expect(router.currentRoute.value.path).toBe('/staff/regattas')
    })
  })

  describe('Operator routes', () => {
    it('resolves /operator/regattas route', async () => {
      await router.push('/operator/regattas')
      await router.isReady()
      
      expect(router.currentRoute.value.path).toBe('/operator/regattas')
      expect(router.currentRoute.value.name).toBe('operator-regattas')
    })

    it('resolves /operator/regattas/:regattaId route with params', async () => {
      const regattaId = 'operator-test-id'
      await router.push(`/operator/regattas/${regattaId}`)
      await router.isReady()
      
      expect(router.currentRoute.value.path).toBe(`/operator/regattas/${regattaId}`)
      expect(router.currentRoute.value.name).toBe('operator-regatta-detail')
      expect(router.currentRoute.value.params.regattaId).toBe(regattaId)
    })

    it('resolves /operator/regattas/:regattaId/line-scan route', async () => {
      const regattaId = 'scan-test-id'
      await router.push(`/operator/regattas/${regattaId}/line-scan`)
      await router.isReady()
      
      expect(router.currentRoute.value.path).toBe(`/operator/regattas/${regattaId}/line-scan`)
      expect(router.currentRoute.value.name).toBe('operator-line-scan')
      expect(router.currentRoute.value.params.regattaId).toBe(regattaId)
    })

    it('redirects /operator to /operator/regattas', async () => {
      await router.push('/operator')
      await router.isReady()
      
      expect(router.currentRoute.value.path).toBe('/operator/regattas')
    })
  })

  describe('Public routes with versioning', () => {
    it('resolves /public/v1-0/schedule route', async () => {
      await router.push('/public/v1-0/schedule')
      await router.isReady()
      
      expect(router.currentRoute.value.path).toBe('/public/v1-0/schedule')
      expect(router.currentRoute.value.name).toBe('public-schedule')
      expect(router.currentRoute.value.params.drawRevision).toBe('1')
      expect(router.currentRoute.value.params.resultsRevision).toBe('0')
    })

    it('resolves /public/v5-12/results route', async () => {
      await router.push('/public/v5-12/results')
      await router.isReady()
      
      expect(router.currentRoute.value.path).toBe('/public/v5-12/results')
      expect(router.currentRoute.value.name).toBe('public-results')
      expect(router.currentRoute.value.params.drawRevision).toBe('5')
      expect(router.currentRoute.value.params.resultsRevision).toBe('12')
    })

    it('redirects /public/v2-3 to /public/v2-3/schedule', async () => {
      await router.push('/public/v2-3')
      await router.isReady()
      
      expect(router.currentRoute.value.path).toBe('/public/v2-3/schedule')
      expect(router.currentRoute.value.name).toBe('public-schedule')
    })

    it('handles multi-digit revision numbers', async () => {
      await router.push('/public/v999-1234/results')
      await router.isReady()
      
      expect(router.currentRoute.value.params.drawRevision).toBe('999')
      expect(router.currentRoute.value.params.resultsRevision).toBe('1234')
    })
  })

  describe('Error routes', () => {
    it('resolves /unauthorized route', async () => {
      await router.push('/unauthorized')
      await router.isReady()
      
      expect(router.currentRoute.value.path).toBe('/unauthorized')
      expect(router.currentRoute.value.name).toBe('unauthorized')
    })

    it('catches non-existent routes as not-found', async () => {
      await router.push('/this-does-not-exist')
      await router.isReady()
      
      expect(router.currentRoute.value.name).toBe('not-found')
    })

    it('catches nested non-existent routes as not-found', async () => {
      await router.push('/staff/invalid/nested/path')
      await router.isReady()
      
      expect(router.currentRoute.value.name).toBe('not-found')
    })
  })

  describe('Route naming consistency', () => {
    it('all staff routes have staff- prefix', () => {
      const staffRoutes = router.getRoutes().filter(r => r.path.startsWith('/staff/'))
      const namedStaffRoutes = staffRoutes.filter(r => r.name)
      
      namedStaffRoutes.forEach(route => {
        expect(route.name).toMatch(/^staff-/)
      })
    })

    it('all operator routes have operator- prefix', () => {
      const operatorRoutes = router.getRoutes().filter(r => r.path.startsWith('/operator/'))
      const namedOperatorRoutes = operatorRoutes.filter(r => r.name)
      
      namedOperatorRoutes.forEach(route => {
        expect(route.name).toMatch(/^operator-/)
      })
    })

    it('all public routes have public- prefix', () => {
      const publicRoutes = router.getRoutes().filter(r => r.path.includes('/public/'))
      const namedPublicRoutes = publicRoutes.filter(r => r.name)
      
      namedPublicRoutes.forEach(route => {
        expect(route.name).toMatch(/^public-/)
      })
    })
  })

  describe('Deep linking', () => {
    it('supports direct navigation to staff finance page', async () => {
      const regattaId = 'direct-link-test'
      await router.push(`/staff/regattas/${regattaId}/finance`)
      await router.isReady()
      
      expect(router.currentRoute.value.name).toBe('staff-regatta-finance')
      expect(router.currentRoute.value.params.regattaId).toBe(regattaId)
    })

    it('supports direct navigation to operator line-scan', async () => {
      const regattaId = 'scan-direct-link'
      await router.push(`/operator/regattas/${regattaId}/line-scan`)
      await router.isReady()
      
      expect(router.currentRoute.value.name).toBe('operator-line-scan')
      expect(router.currentRoute.value.params.regattaId).toBe(regattaId)
    })

    it('supports direct navigation to versioned public results', async () => {
      await router.push('/public/v10-25/results')
      await router.isReady()
      
      expect(router.currentRoute.value.name).toBe('public-results')
      expect(router.currentRoute.value.params.drawRevision).toBe('10')
      expect(router.currentRoute.value.params.resultsRevision).toBe('25')
    })
  })

  describe('Navigation between surfaces', () => {
    it('can navigate from staff to operator surface', async () => {
      await router.push('/staff/regattas')
      await router.isReady()
      expect(router.currentRoute.value.name).toBe('staff-regattas')
      
      await router.push('/operator/regattas')
      await router.isReady()
      expect(router.currentRoute.value.name).toBe('operator-regattas')
    })

    it('can navigate from operator to public surface', async () => {
      await router.push('/operator/regattas/test-id')
      await router.isReady()
      expect(router.currentRoute.value.name).toBe('operator-regatta-detail')
      
      await router.push('/public/v1-0/schedule')
      await router.isReady()
      expect(router.currentRoute.value.name).toBe('public-schedule')
    })

    it('can navigate from public to staff surface', async () => {
      await router.push('/public/v2-5/results')
      await router.isReady()
      expect(router.currentRoute.value.name).toBe('public-results')
      
      await router.push('/staff/regattas')
      await router.isReady()
      expect(router.currentRoute.value.name).toBe('staff-regattas')
    })
  })
})
