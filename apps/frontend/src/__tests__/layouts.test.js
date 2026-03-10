import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import OperatorLayout from '../layouts/OperatorLayout.vue'
import PublicLayout from '../layouts/PublicLayout.vue'
import StaffLayout from '../layouts/StaffLayout.vue'

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json',
    },
  })
}

function createTestRouter(extraRoutes = []) {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/staff/regattas', name: 'staff-regattas', component: { template: '<div>Staff Regattas</div>' } },
      { path: '/staff/rulesets', name: 'staff-rulesets', component: { template: '<div>Staff Rulesets</div>' } },
      {
        path: '/staff/regattas/:regattaId',
        name: 'staff-regatta-detail',
        component: { template: '<div>Staff Regatta Detail</div>' },
      },
      {
        path: '/staff/regattas/:regattaId/draw',
        name: 'staff-regatta-draw',
        component: { template: '<div>Staff Draw</div>' },
      },
      {
        path: '/staff/regattas/:regattaId/finance',
        name: 'staff-regatta-finance',
        component: { template: '<div>Staff Finance</div>' },
      },
      {
        path: '/staff/regattas/:regattaId/blocks',
        name: 'staff-blocks-management',
        component: { template: '<div>Staff Blocks</div>' },
      },
      { path: '/operator/regattas', name: 'operator-regattas', component: { template: '<div>Operator Regattas</div>' } },
      {
        path: '/operator/regattas/:regattaId',
        name: 'operator-regatta-home',
        meta: { breadcrumb: ['operator-regattas', 'operator-regatta-home'] },
        component: { template: '<div>Operator Regatta Detail</div>' },
      },
      {
        path: '/operator/regattas/:regattaId/sessions',
        name: 'operator-regatta-sessions',
        meta: { breadcrumb: ['operator-regattas', 'operator-regatta-home', 'operator-regatta-sessions'] },
        component: { template: '<div>Operator Sessions</div>' },
      },
      {
        path: '/operator/regattas/:regattaId/sessions/:captureSessionId/line-scan',
        name: 'operator-session-line-scan',
        meta: {
          breadcrumb: [
            'operator-regattas',
            'operator-regatta-home',
            'operator-regatta-sessions',
            'operator-session-line-scan'
          ]
        },
        component: { template: '<div>Operator Line Scan</div>' },
      },
      {
        path: '/public/v:drawRevision-:resultsRevision/schedule',
        name: 'public-schedule',
        component: { template: '<div>Public Schedule</div>' },
      },
      {
        path: '/public/v:drawRevision-:resultsRevision/results',
        name: 'public-results',
        component: { template: '<div>Public Results</div>' },
      },
      ...extraRoutes,
    ],
  })
}

function createTestI18n() {
  return createI18n({
    legacy: false,
    locale: 'en',
    messages: {
      en: {
        common: {
          skip_to_content: 'Skip to main content',
          staff: 'Staff',
          operator: 'Operator',
          powered_by_regattadesk: 'Powered by RegattaDesk',
        },
        navigation: {
          regattas: 'Regattas',
          sessions: 'Sessions',
          rulesets: 'Rulesets',
          setup: 'Setup',
          draw: 'Draw',
          finance: 'Finance',
          blocks: 'Blocks',
          line_scan: 'Line Scan',
          schedule: 'Schedule',
          results: 'Results',
        },
        operator: {
          regatta: {
            station_context: 'Station: {station}',
            session_label: 'Session: {id}',
            title: 'Regatta',
            sync_synced: 'Sync status: synced',
            sync_pending: 'Sync status: pending ({reason})',
            sync_pending_default: 'awaiting upload',
            sync_attention: 'Sync status: attention required'
          },
        },
      },
    },
  })
}

async function mountAtRoute(router, route, component) {
  await router.push(route)
  await router.isReady()
  return mount(component, {
    global: {
      plugins: [router, createTestI18n()],
    },
  })
}

describe('Layout Components', () => {
  let router

  beforeEach(() => {
    router = createTestRouter()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({
        capture_session_id: 'my-session-id',
        station: 'finish-line',
        is_synced: false,
        unsynced_reason: 'awaiting upload'
      })
    ))
    globalThis.__REGATTADESK_AUTH__ = {
      operatorToken: 'operator-token',
      operatorStation: 'finish-line'
    }
  })

  const layoutCases = [
    {
      name: 'StaffLayout',
      component: StaffLayout,
      route: '/staff/regattas',
      root: '.staff-layout',
      header: '.staff-layout__header',
      main: '.staff-layout__main',
      brandText: 'Staff',
      navLink: '/staff/regattas',
    },
    {
      name: 'OperatorLayout',
      component: OperatorLayout,
      route: '/operator/regattas',
      root: '.operator-layout',
      header: '.operator-layout__header',
      main: '.operator-layout__main',
      brandText: 'Operator',
      contrast: 'high',
    },
    {
      name: 'PublicLayout',
      component: PublicLayout,
      route: '/public/v2-5/schedule',
      root: '.public-layout',
      header: '.public-layout__header',
      main: '.public-layout__main',
      footer: '.public-layout__footer',
      navLinks: ['/public/v2-5/schedule', '/public/v2-5/results'],
    },
  ]

  layoutCases.forEach((testCase) => {
    describe(testCase.name, () => {
      it('renders shell and accessibility landmarks', async () => {
        const wrapper = await mountAtRoute(router, testCase.route, testCase.component)

        expect(wrapper.find(testCase.root).exists()).toBe(true)
        expect(wrapper.find(testCase.header).exists()).toBe(true)
        expect(wrapper.find(testCase.main).exists()).toBe(true)
        expect(wrapper.find('header').exists()).toBe(true)
        expect(wrapper.find('main').attributes('id')).toBe('main-content')
        expect(wrapper.find('.skip-link').attributes('href')).toBe('#main-content')

        if (testCase.footer) {
          expect(wrapper.find(testCase.footer).exists()).toBe(true)
          expect(wrapper.find('footer').exists()).toBe(true)
        }
      })

      it('shows expected brand/navigation content', async () => {
        const wrapper = await mountAtRoute(router, testCase.route, testCase.component)

        expect(wrapper.text()).toContain('RegattaDesk')

        if (testCase.brandText) {
          expect(wrapper.text()).toContain(testCase.brandText)
        }

        if (testCase.navLink) {
          expect(wrapper.find('.staff-layout__nav-item').attributes('href')).toBe(testCase.navLink)
        }

        if (testCase.navLinks) {
          const links = wrapper.findAll('.public-layout__nav-item').map((node) => node.attributes('href'))
          expect(links).toEqual(testCase.navLinks)
          expect(wrapper.find('.public-layout__footer').text()).toContain('Powered by RegattaDesk')
        }

        if (testCase.contrast) {
          expect(wrapper.find(testCase.root).attributes('data-contrast')).toBe(testCase.contrast)
        }
      })
    })
  })

  it('keeps cross-layout consistency for skip links and main target', async () => {
    const wrappers = await Promise.all([
      mountAtRoute(router, '/staff/regattas', StaffLayout),
      mountAtRoute(router, '/operator/regattas', OperatorLayout),
      mountAtRoute(router, '/public/v1-0/schedule', PublicLayout),
    ])

    wrappers.forEach((wrapper) => {
      expect(wrapper.find('.skip-link').exists()).toBe(true)
      expect(wrapper.find('#main-content').exists()).toBe(true)
      expect(wrapper.text()).toContain('RegattaDesk')
    })
  })

  describe('StaffLayout regatta-scoped secondary navigation', () => {
    it('does not render subnav when not inside a regatta', async () => {
      const wrapper = await mountAtRoute(router, '/staff/regattas', StaffLayout)
      expect(wrapper.find('.staff-layout__subnav').exists()).toBe(false)
    })

    it('renders subnav with draw, finance, blocks links inside a regatta', async () => {
      const wrapper = await mountAtRoute(router, '/staff/regattas/test-id-123/draw', StaffLayout)
      expect(wrapper.find('.staff-layout__subnav').exists()).toBe(true)
      const links = wrapper.findAll('.staff-layout__subnav-item').map((n) => n.attributes('href'))
      expect(links).toContain('/staff/regattas/test-id-123')
      expect(links).toContain('/staff/regattas/test-id-123/draw')
      expect(links).toContain('/staff/regattas/test-id-123/finance')
      expect(links).toContain('/staff/regattas/test-id-123/blocks')
    })

    it('subnav is labelled for accessibility', async () => {
      const wrapper = await mountAtRoute(router, '/staff/regattas/abc/finance', StaffLayout)
      const subnav = wrapper.find('.staff-layout__subnav')
      expect(subnav.attributes('aria-label')).toBeTruthy()
    })

    it('shows Rulesets in primary nav', async () => {
      const wrapper = await mountAtRoute(router, '/staff/regattas', StaffLayout)
      const navItems = wrapper.findAll('.staff-layout__nav-item').map((n) => n.attributes('href'))
      expect(navItems).toContain('/staff/rulesets')
    })

    it('sets aria-current on the active primary nav item', async () => {
      const regattasWrapper = await mountAtRoute(router, '/staff/regattas', StaffLayout)
      const regattasLink = regattasWrapper.findAll('.staff-layout__nav-item')[0]
      expect(regattasLink.attributes('aria-current')).toBe('page')

      const rulesetsWrapper = await mountAtRoute(router, '/staff/rulesets', StaffLayout)
      const rulesetsLink = rulesetsWrapper.findAll('.staff-layout__nav-item')[1]
      expect(rulesetsLink.attributes('aria-current')).toBe('page')
    })
  })

  describe('OperatorLayout regatta-scoped navigation', () => {
    it('shows Regattas link at all times', async () => {
      const wrapper = await mountAtRoute(router, '/operator/regattas', OperatorLayout)
      const links = wrapper.findAll('.operator-layout__nav-item').map((n) => n.attributes('href'))
      expect(links).toContain('/operator/regattas')
    })

    it('does not show Line Scan link when outside a regatta', async () => {
      const wrapper = await mountAtRoute(router, '/operator/regattas', OperatorLayout)
      const links = wrapper.findAll('.operator-layout__nav-item').map((n) => n.attributes('href'))
      expect(links.some((href) => href?.includes('line-scan'))).toBe(false)
    })

    it('shows Line Scan link when inside a regatta', async () => {
      const wrapper = await mountAtRoute(
        router,
        '/operator/regattas/my-regatta-id/sessions/my-session-id/line-scan',
        OperatorLayout
      )
      const links = wrapper.findAll('.operator-layout__nav-item').map((n) => n.attributes('href'))
      expect(links).toContain('/operator/regattas/my-regatta-id/sessions')
      expect(links).toContain('/operator/regattas/my-regatta-id/sessions/my-session-id/line-scan')
    })

    it('operator nav is labelled for accessibility', async () => {
      const wrapper = await mountAtRoute(router, '/operator/regattas', OperatorLayout)
      expect(wrapper.find('.operator-layout__nav').attributes('aria-label')).toBeTruthy()
    })

    it('sets aria-current on the active operator nav item', async () => {
      const regattasWrapper = await mountAtRoute(router, '/operator/regattas', OperatorLayout)
      const regattasLink = regattasWrapper.findAll('.operator-layout__nav-item')[0]
      expect(regattasLink.attributes('aria-current')).toBe('page')

      const lineScanWrapper = await mountAtRoute(
        router,
        '/operator/regattas/my-regatta-id/sessions/my-session-id/line-scan',
        OperatorLayout
      )
      const lineScanLink = lineScanWrapper.findAll('.operator-layout__nav-item')[2]
      expect(lineScanLink.attributes('aria-current')).toBe('page')
    })

    it('renders breadcrumbs and shell-level sync context on deep links', async () => {
      const wrapper = await mountAtRoute(
        router,
        '/operator/regattas/my-regatta-id/sessions/my-session-id/line-scan',
        OperatorLayout
      )
      await flushPromises()

      const breadcrumbLinks = wrapper
        .findAll('.operator-layout__breadcrumbs-link')
        .map((node) => node.text())

      expect(wrapper.find('[data-testid="operator-breadcrumbs"]').exists()).toBe(true)
      expect(breadcrumbLinks).toEqual(['Regattas', 'Regatta', 'Sessions'])
      expect(wrapper.find('[data-testid="operator-shell-station"]').text()).toContain('finish-line')
      expect(wrapper.find('[data-testid="operator-shell-session"]').text()).toContain('my-session-id')
      expect(wrapper.find('[data-testid="operator-shell-sync-summary"]').text()).toContain('awaiting upload')
    })
  })

  describe('PublicLayout locale switcher', () => {
    it('renders locale buttons', async () => {
      const wrapper = await mountAtRoute(router, '/public/v2-5/schedule', PublicLayout)
      const localeGroup = wrapper.find('.public-layout__locale')
      expect(localeGroup.exists()).toBe(true)
      const buttons = wrapper.findAll('.public-layout__locale-btn')
      expect(buttons.length).toBeGreaterThan(0)
    })

    it('locale control has accessible group role', async () => {
      const wrapper = await mountAtRoute(router, '/public/v2-5/schedule', PublicLayout)
      const localeGroup = wrapper.find('.public-layout__locale')
      expect(localeGroup.attributes('role')).toBe('group')
    })
  })
})
