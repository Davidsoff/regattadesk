import { describe, expect, it, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'
import StaffLayout from '../layouts/StaffLayout.vue'
import OperatorLayout from '../layouts/OperatorLayout.vue'
import PublicLayout from '../layouts/PublicLayout.vue'

/**
 * Create a minimal router for layout testing
 */
function createTestRouter(routes = []) {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/staff/regattas',
        name: 'staff-regattas',
        component: { template: '<div>Staff Regattas</div>' }
      },
      {
        path: '/operator/regattas',
        name: 'operator-regattas',
        component: { template: '<div>Operator Regattas</div>' }
      },
      {
        path: '/public/v:drawRevision-:resultsRevision/schedule',
        name: 'public-schedule',
        component: { template: '<div>Public Schedule</div>' }
      },
      {
        path: '/public/v:drawRevision-:resultsRevision/results',
        name: 'public-results',
        component: { template: '<div>Public Results</div>' }
      },
      ...routes
    ]
  })
}

/**
 * Create test i18n instance
 */
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
          powered_by_regattadesk: 'Powered by RegattaDesk'
        },
        navigation: {
          regattas: 'Regattas',
          schedule: 'Schedule',
          results: 'Results'
        }
      }
    }
  })
}

describe('Layout Components', () => {
  describe('StaffLayout', () => {
    let router

    beforeEach(() => {
      router = createTestRouter()
    })

    it('renders staff layout structure', async () => {
      await router.push('/staff/regattas')
      await router.isReady()

      const wrapper = mount(StaffLayout, {
        global: {
          plugins: [router, createTestI18n()],
        }
      })

      expect(wrapper.find('.staff-layout').exists()).toBe(true)
      expect(wrapper.find('.staff-layout__header').exists()).toBe(true)
      expect(wrapper.find('.staff-layout__main').exists()).toBe(true)
    })

    it('includes skip-to-content link', async () => {
      await router.push('/staff/regattas')
      await router.isReady()

      const wrapper = mount(StaffLayout, {
        global: {
          plugins: [router, createTestI18n()],
        }
      })

      const skipLink = wrapper.find('.skip-link')
      expect(skipLink.exists()).toBe(true)
      expect(skipLink.attributes('href')).toBe('#main-content')
      expect(skipLink.text()).toBe('Skip to main content')
    })

    it('displays brand and surface label', async () => {
      await router.push('/staff/regattas')
      await router.isReady()

      const wrapper = mount(StaffLayout, {
        global: {
          plugins: [router, createTestI18n()],
        }
      })

      expect(wrapper.find('.staff-layout__brand h1').text()).toBe('RegattaDesk')
      expect(wrapper.find('.staff-layout__surface-label').text()).toBe('Staff')
    })

    it('includes navigation with regattas link', async () => {
      await router.push('/staff/regattas')
      await router.isReady()

      const wrapper = mount(StaffLayout, {
        global: {
          plugins: [router, createTestI18n()],
        }
      })

      const nav = wrapper.find('.staff-layout__nav')
      expect(nav.exists()).toBe(true)
      expect(nav.attributes('aria-label')).toBe('Primary navigation')

      const navLink = wrapper.find('.staff-layout__nav-item')
      expect(navLink.exists()).toBe(true)
      // router-link's :to prop is rendered as href
      expect(navLink.attributes('href')).toBe('/staff/regattas')
    })

    it('renders router-view for content', async () => {
      await router.push('/staff/regattas')
      await router.isReady()

      const wrapper = mount(StaffLayout, {
        global: {
          plugins: [router, createTestI18n()],
        }
      })

      expect(wrapper.find('#main-content').exists()).toBe(true)
    })

    it('has proper landmark structure', async () => {
      await router.push('/staff/regattas')
      await router.isReady()

      const wrapper = mount(StaffLayout, {
        global: {
          plugins: [router, createTestI18n()],
        }
      })

      expect(wrapper.find('header').exists()).toBe(true)
      expect(wrapper.find('main').exists()).toBe(true)
      expect(wrapper.find('main').attributes('id')).toBe('main-content')
    })
  })

  describe('OperatorLayout', () => {
    let router

    beforeEach(() => {
      router = createTestRouter()
    })

    it('renders operator layout structure', async () => {
      await router.push('/operator/regattas')
      await router.isReady()

      const wrapper = mount(OperatorLayout, {
        global: {
          plugins: [router, createTestI18n()],
        }
      })

      expect(wrapper.find('.operator-layout').exists()).toBe(true)
      expect(wrapper.find('.operator-layout__header').exists()).toBe(true)
      expect(wrapper.find('.operator-layout__main').exists()).toBe(true)
    })

    it('applies high contrast mode by default', async () => {
      await router.push('/operator/regattas')
      await router.isReady()

      const wrapper = mount(OperatorLayout, {
        global: {
          plugins: [router, createTestI18n()],
        }
      })

      expect(wrapper.find('.operator-layout').attributes('data-contrast')).toBe('high')
    })

    it('includes skip-to-content link with larger touch target', async () => {
      await router.push('/operator/regattas')
      await router.isReady()

      const wrapper = mount(OperatorLayout, {
        global: {
          plugins: [router, createTestI18n()],
        }
      })

      const skipLink = wrapper.find('.skip-link')
      expect(skipLink.exists()).toBe(true)
      expect(skipLink.attributes('href')).toBe('#main-content')
    })

    it('displays brand and surface label', async () => {
      await router.push('/operator/regattas')
      await router.isReady()

      const wrapper = mount(OperatorLayout, {
        global: {
          plugins: [router, createTestI18n()],
        }
      })

      expect(wrapper.find('.operator-layout__brand h1').text()).toBe('RegattaDesk')
      expect(wrapper.find('.operator-layout__surface-label').text()).toBe('Operator')
    })

    it('has proper landmark structure', async () => {
      await router.push('/operator/regattas')
      await router.isReady()

      const wrapper = mount(OperatorLayout, {
        global: {
          plugins: [router, createTestI18n()],
        }
      })

      expect(wrapper.find('header').exists()).toBe(true)
      expect(wrapper.find('main').exists()).toBe(true)
      expect(wrapper.find('main').attributes('id')).toBe('main-content')
    })
  })

  describe('PublicLayout', () => {
    let router

    beforeEach(() => {
      router = createTestRouter()
    })

    it('renders public layout structure', async () => {
      await router.push('/public/v1-0/schedule')
      await router.isReady()

      const wrapper = mount(PublicLayout, {
        global: {
          plugins: [router, createTestI18n()],
        }
      })

      expect(wrapper.find('.public-layout').exists()).toBe(true)
      expect(wrapper.find('.public-layout__header').exists()).toBe(true)
      expect(wrapper.find('.public-layout__main').exists()).toBe(true)
      expect(wrapper.find('.public-layout__footer').exists()).toBe(true)
    })

    it('includes skip-to-content link', async () => {
      await router.push('/public/v1-0/schedule')
      await router.isReady()

      const wrapper = mount(PublicLayout, {
        global: {
          plugins: [router, createTestI18n()],
        }
      })

      const skipLink = wrapper.find('.skip-link')
      expect(skipLink.exists()).toBe(true)
      expect(skipLink.attributes('href')).toBe('#main-content')
    })

    it('displays brand in header', async () => {
      await router.push('/public/v1-0/schedule')
      await router.isReady()

      const wrapper = mount(PublicLayout, {
        global: {
          plugins: [router, createTestI18n()],
        }
      })

      expect(wrapper.find('.public-layout__brand h1').text()).toBe('RegattaDesk')
    })

    it('includes navigation with versioned links', async () => {
      await router.push('/public/v2-5/schedule')
      await router.isReady()

      const wrapper = mount(PublicLayout, {
        global: {
          plugins: [router, createTestI18n()],
        }
      })

      const nav = wrapper.find('.public-layout__nav')
      expect(nav.exists()).toBe(true)
      expect(nav.attributes('aria-label')).toBe('Primary navigation')

      const navLinks = wrapper.findAll('.public-layout__nav-item')
      expect(navLinks.length).toBe(2)
      // router-link's :to prop is rendered as href
      expect(navLinks[0].attributes('href')).toBe('/public/v2-5/schedule')
      expect(navLinks[1].attributes('href')).toBe('/public/v2-5/results')
    })

    it('displays footer with branding', async () => {
      await router.push('/public/v1-0/results')
      await router.isReady()

      const wrapper = mount(PublicLayout, {
        global: {
          plugins: [router, createTestI18n()],
        }
      })

      const footer = wrapper.find('.public-layout__footer')
      expect(footer.exists()).toBe(true)
      expect(footer.text()).toContain('Powered by RegattaDesk')
    })

    it('has proper landmark structure', async () => {
      await router.push('/public/v1-0/schedule')
      await router.isReady()

      const wrapper = mount(PublicLayout, {
        global: {
          plugins: [router, createTestI18n()],
        }
      })

      expect(wrapper.find('header').exists()).toBe(true)
      expect(wrapper.find('main').exists()).toBe(true)
      expect(wrapper.find('footer').exists()).toBe(true)
      expect(wrapper.find('main').attributes('id')).toBe('main-content')
    })
  })

  describe('Cross-layout consistency', () => {
    let router

    beforeEach(() => {
      router = createTestRouter()
    })

    it('all layouts have skip-to-content links', async () => {
      await router.push('/staff/regattas')
      await router.isReady()
      const staffWrapper = mount(StaffLayout, {
        global: { plugins: [router, createTestI18n()] }
      })

      await router.push('/operator/regattas')
      await router.isReady()
      const operatorWrapper = mount(OperatorLayout, {
        global: { plugins: [router, createTestI18n()] }
      })

      await router.push('/public/v1-0/schedule')
      await router.isReady()
      const publicWrapper = mount(PublicLayout, {
        global: { plugins: [router, createTestI18n()] }
      })

      expect(staffWrapper.find('.skip-link').exists()).toBe(true)
      expect(operatorWrapper.find('.skip-link').exists()).toBe(true)
      expect(publicWrapper.find('.skip-link').exists()).toBe(true)
    })

    it('all layouts have main content area with id', async () => {
      await router.push('/staff/regattas')
      await router.isReady()
      const staffWrapper = mount(StaffLayout, {
        global: { plugins: [router, createTestI18n()] }
      })

      await router.push('/operator/regattas')
      await router.isReady()
      const operatorWrapper = mount(OperatorLayout, {
        global: { plugins: [router, createTestI18n()] }
      })

      await router.push('/public/v1-0/schedule')
      await router.isReady()
      const publicWrapper = mount(PublicLayout, {
        global: { plugins: [router, createTestI18n()] }
      })

      expect(staffWrapper.find('#main-content').exists()).toBe(true)
      expect(operatorWrapper.find('#main-content').exists()).toBe(true)
      expect(publicWrapper.find('#main-content').exists()).toBe(true)
    })

    it('all layouts display RegattaDesk brand', async () => {
      await router.push('/staff/regattas')
      await router.isReady()
      const staffWrapper = mount(StaffLayout, {
        global: { plugins: [router, createTestI18n()] }
      })

      await router.push('/operator/regattas')
      await router.isReady()
      const operatorWrapper = mount(OperatorLayout, {
        global: { plugins: [router, createTestI18n()] }
      })

      await router.push('/public/v1-0/schedule')
      await router.isReady()
      const publicWrapper = mount(PublicLayout, {
        global: { plugins: [router, createTestI18n()] }
      })

      expect(staffWrapper.text()).toContain('RegattaDesk')
      expect(operatorWrapper.text()).toContain('RegattaDesk')
      expect(publicWrapper.text()).toContain('RegattaDesk')
    })
  })
})
