import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import OperatorLayout from '../layouts/OperatorLayout.vue'
import PublicLayout from '../layouts/PublicLayout.vue'
import StaffLayout from '../layouts/StaffLayout.vue'

function createTestRouter(extraRoutes = []) {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/staff/regattas', name: 'staff-regattas', component: { template: '<div>Staff Regattas</div>' } },
      { path: '/operator/regattas', name: 'operator-regattas', component: { template: '<div>Operator Regattas</div>' } },
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
          schedule: 'Schedule',
          results: 'Results',
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
})
