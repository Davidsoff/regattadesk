import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { operatorGuard, staffGuard } from '../router/guards'

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', redirect: '/staff/regattas' },
      {
        path: '/staff',
        component: { template: '<div>Staff Layout</div>' },
        beforeEnter: staffGuard,
        children: [
          { path: '', redirect: '/staff/regattas' },
          { path: 'regattas', name: 'staff-regattas', component: { template: '<div>Staff Regattas</div>' } },
          {
            path: 'regattas/:regattaId',
            name: 'staff-regatta-detail',
            component: { template: '<div>Staff Regatta Detail</div>' },
          },
          {
            path: 'regattas/:regattaId/operator-access',
            name: 'staff-regatta-operator-access',
            component: { template: '<div>Staff Operator Access</div>' },
          },
          {
            path: 'regattas/:regattaId/finance',
            name: 'staff-regatta-finance',
            component: { template: '<div>Staff Finance</div>' },
          },
          {
            path: 'regattas/:regattaId/printables',
            name: 'staff-regatta-printables',
            component: { template: '<div>Staff Printables</div>' },
          },
        ],
      },
      {
        path: '/operator',
        component: { template: '<div>Operator Layout</div>' },
        beforeEnter: operatorGuard,
        children: [
          { path: '', redirect: '/operator/regattas' },
          { path: 'regattas', name: 'operator-regattas', component: { template: '<div>Operator Regattas</div>' } },
          {
            path: 'regattas/:regattaId',
            name: 'operator-regatta-home',
            component: { template: '<div>Operator Regatta Detail</div>' },
          },
          {
            path: 'regattas/:regattaId/sessions',
            name: 'operator-regatta-sessions',
            component: { template: '<div>Operator Sessions</div>' },
          },
          {
            path: 'regattas/:regattaId/sessions/:captureSessionId/line-scan',
            name: 'operator-session-line-scan',
            component: { template: '<div>Operator Line Scan</div>' },
          },
        ],
      },
      {
        path: '/public/v:drawRevision-:resultsRevision',
        component: { template: '<div>Public Layout</div>' },
        children: [
          { path: '', redirect: (to) => `/public/v${to.params.drawRevision}-${to.params.resultsRevision}/schedule` },
          { path: 'schedule', name: 'public-schedule', component: { template: '<div>Public Schedule</div>' } },
          { path: 'results', name: 'public-results', component: { template: '<div>Public Results</div>' } },
        ],
      },
      { path: '/unauthorized', name: 'unauthorized', component: { template: '<div>Unauthorized</div>' } },
      { path: '/:pathMatch(.*)*', name: 'not-found', component: { template: '<div>Not Found</div>' } },
    ],
  })
}

async function navigate(router, path) {
  await router.push(path)
  await router.isReady()
  return router.currentRoute.value
}

describe('Router Configuration', () => {
  let router

  beforeEach(() => {
    globalThis.__REGATTADESK_AUTH__ = {
      staffAuthenticated: true,
      operatorAuth: 'operator-token',
    }
    router = createTestRouter()
  })

  it('redirects / to /staff/regattas', async () => {
    const route = await navigate(router, '/')

    expect(route.path).toBe('/staff/regattas')
    expect(route.name).toBe('staff-regattas')
  })

  const routeCases = [
    { path: '/staff/regattas', name: 'staff-regattas' },
    { path: '/staff', redirectPath: '/staff/regattas' },
    { path: '/operator/regattas', name: 'operator-regattas' },
    { path: '/operator', redirectPath: '/operator/regattas' },
    { path: '/public/v1-0/schedule', name: 'public-schedule' },
    { path: '/public/v5-12/results', name: 'public-results' },
    { path: '/public/v2-3', redirectPath: '/public/v2-3/schedule', name: 'public-schedule' },
    { path: '/unauthorized', name: 'unauthorized' },
    { path: '/totally-missing', name: 'not-found' },
    { path: '/staff/invalid/unknown/path', name: 'not-found' },
  ]

  routeCases.forEach(({ path, name, redirectPath }) => {
    it(`resolves ${path}`, async () => {
      const route = await navigate(router, path)

      expect(route.path).toBe(redirectPath ?? path)
      if (name) {
        expect(route.name).toBe(name)
      }
    })
  })

  const paramCases = [
    {
      path: '/staff/regattas/abc-123-def-456',
      name: 'staff-regatta-detail',
      param: 'regattaId',
      value: 'abc-123-def-456',
    },
    {
      path: '/staff/regattas/test-uuid-123/finance',
      name: 'staff-regatta-finance',
      param: 'regattaId',
      value: 'test-uuid-123',
    },
    {
      path: '/staff/regattas/test-uuid-123/operator-access',
      name: 'staff-regatta-operator-access',
      param: 'regattaId',
      value: 'test-uuid-123',
    },
    {
      path: '/staff/regattas/test-uuid-123/printables',
      name: 'staff-regatta-printables',
      param: 'regattaId',
      value: 'test-uuid-123',
    },
    {
      path: '/operator/regattas/operator-test-id',
      name: 'operator-regatta-home',
      param: 'regattaId',
      value: 'operator-test-id',
    },
    {
      path: '/operator/regattas/scan-test-id/sessions/session-test-id/line-scan',
      name: 'operator-session-line-scan',
      param: 'regattaId',
      value: 'scan-test-id',
      param2: 'captureSessionId',
      value2: 'session-test-id',
    },
    {
      path: '/public/v999-1234/results',
      name: 'public-results',
      param: 'drawRevision',
      value: '999',
      param2: 'resultsRevision',
      value2: '1234',
    },
  ]

  paramCases.forEach(({ path, name, param, value, param2, value2 }) => {
    it(`parses params for ${path}`, async () => {
      const route = await navigate(router, path)

      expect(route.name).toBe(name)
      expect(route.params[param]).toBe(value)
      if (param2) {
        expect(route.params[param2]).toBe(value2)
      }
    })
  })

  it('keeps naming prefixes for staff/operator/public routes', () => {
    const namedRoutes = router.getRoutes().filter((route) => route.name)

    const hasPrefix = (prefix) =>
      namedRoutes
        .filter((route) => String(route.name).startsWith(prefix))
        .every((route) => String(route.name).startsWith(prefix))

    expect(hasPrefix('staff-')).toBe(true)
    expect(hasPrefix('operator-')).toBe(true)
    expect(hasPrefix('public-')).toBe(true)
  })

  it('supports direct navigation across protected and public surfaces', async () => {
    const staff = await navigate(router, '/staff/regattas/direct-test/printables')
    const operator = await navigate(router, '/operator/regattas/direct-test/sessions/session-direct/line-scan')
    const publicRoute = await navigate(router, '/public/v42-99/results')

    expect(staff.name).toBe('staff-regatta-printables')
    expect(operator.name).toBe('operator-session-line-scan')
    expect(publicRoute.name).toBe('public-results')
  })

  it('navigates between staff, operator, and public surfaces', async () => {
    const paths = ['/staff/regattas', '/operator/regattas', '/public/v1-0/schedule', '/staff/regattas']

    for (const path of paths) {
      const route = await navigate(router, path)
      expect(route.path).toBe(path)
    }
  })
})
