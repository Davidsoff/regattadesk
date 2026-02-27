import { describe, expect, it, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'
import Unauthorized from '../views/Unauthorized.vue'
import NotFound from '../views/NotFound.vue'

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/',
        component: { template: '<div>Home</div>' }
      },
      {
        path: '/unauthorized',
        name: 'unauthorized',
        component: Unauthorized
      },
      {
        path: '/:pathMatch(.*)*',
        name: 'not-found',
        component: NotFound
      }
    ]
  })
}

function createTestI18n() {
  return createI18n({
    legacy: false,
    locale: 'en',
    messages: {
      en: {
        common: { go_home: 'Go to Home' },
        errors: {
          unauthorized: {
            title: 'Unauthorized',
            description: 'You do not have permission.'
          },
          not_found: {
            title: 'Not Found',
            description: 'Page not found.'
          }
        }
      }
    }
  })
}

describe('Error View Components', () => {
  describe('Unauthorized', () => {
    let router
    let i18n

    beforeEach(() => {
      router = createTestRouter()
      i18n = createTestI18n()
    })

    it('renders unauthorized page', async () => {
      await router.push('/unauthorized')
      await router.isReady()

      const wrapper = mount(Unauthorized, {
        global: {
          plugins: [router, i18n]
        }
      })

      expect(wrapper.find('.unauthorized').exists()).toBe(true)
    })

    it('displays unauthorized title', async () => {
      await router.push('/unauthorized')
      await router.isReady()

      const wrapper = mount(Unauthorized, {
        global: {
          plugins: [router, i18n]
        }
      })

      expect(wrapper.find('h2').text()).toBe('Unauthorized')
    })

    it('displays unauthorized description', async () => {
      await router.push('/unauthorized')
      await router.isReady()

      const wrapper = mount(Unauthorized, {
        global: {
          plugins: [router, i18n]
        }
      })

      expect(wrapper.text()).toContain('You do not have permission.')
    })

    it('includes go home button', async () => {
      await router.push('/unauthorized')
      await router.isReady()

      const wrapper = mount(Unauthorized, {
        global: {
          plugins: [router, i18n]
        }
      })

      const button = wrapper.find('button')
      expect(button.exists()).toBe(true)
      expect(button.text()).toBe('Go to Home')
    })

    it('navigates to home when button is clicked', async () => {
      await router.push('/unauthorized')
      await router.isReady()

      const wrapper = mount(Unauthorized, {
        global: {
          plugins: [router, i18n]
        }
      })

      const button = wrapper.find('button')
      const clickPromise = button.trigger('click')
      await clickPromise
      await new Promise(resolve => setTimeout(resolve, 10))
      
      expect(router.currentRoute.value.path).toBe('/')
    })
  })

  describe('NotFound', () => {
    let router
    let i18n

    beforeEach(() => {
      router = createTestRouter()
      i18n = createTestI18n()
    })

    it('renders not found page', async () => {
      await router.push('/does-not-exist')
      await router.isReady()

      const wrapper = mount(NotFound, {
        global: {
          plugins: [router, i18n]
        }
      })

      expect(wrapper.find('.not-found').exists()).toBe(true)
    })

    it('displays not found title', async () => {
      await router.push('/invalid-path')
      await router.isReady()

      const wrapper = mount(NotFound, {
        global: {
          plugins: [router, i18n]
        }
      })

      expect(wrapper.find('h2').text()).toBe('Not Found')
    })

    it('displays not found description', async () => {
      await router.push('/missing')
      await router.isReady()

      const wrapper = mount(NotFound, {
        global: {
          plugins: [router, i18n]
        }
      })

      expect(wrapper.text()).toContain('Page not found.')
    })

    it('includes go home button', async () => {
      await router.push('/404')
      await router.isReady()

      const wrapper = mount(NotFound, {
        global: {
          plugins: [router, i18n]
        }
      })

      const button = wrapper.find('button')
      expect(button.exists()).toBe(true)
      expect(button.text()).toBe('Go to Home')
    })

    it('navigates to home when button is clicked', async () => {
      await router.push('/not-here')
      await router.isReady()

      const wrapper = mount(NotFound, {
        global: {
          plugins: [router, i18n]
        }
      })

      const button = wrapper.find('button')
      const clickPromise = button.trigger('click')
      await clickPromise
      await new Promise(resolve => setTimeout(resolve, 10))
      
      expect(router.currentRoute.value.path).toBe('/')
    })
  })

  describe('Error page consistency', () => {
    let router
    let i18n

    beforeEach(() => {
      router = createTestRouter()
      i18n = createTestI18n()
    })

    it('both error pages have similar structure', async () => {
      await router.push('/unauthorized')
      await router.isReady()
      const unauthorizedWrapper = mount(Unauthorized, {
        global: { plugins: [router, i18n] }
      })

      await router.push('/not-found')
      await router.isReady()
      const notFoundWrapper = mount(NotFound, {
        global: { plugins: [router, i18n] }
      })

      expect(unauthorizedWrapper.find('h2').exists()).toBe(true)
      expect(notFoundWrapper.find('h2').exists()).toBe(true)

      expect(unauthorizedWrapper.find('p').exists()).toBe(true)
      expect(notFoundWrapper.find('p').exists()).toBe(true)

      expect(unauthorizedWrapper.find('button').exists()).toBe(true)
      expect(notFoundWrapper.find('button').exists()).toBe(true)
    })

    it('both error pages center content', async () => {
      await router.push('/unauthorized')
      await router.isReady()
      const unauthorizedWrapper = mount(Unauthorized, {
        global: { plugins: [router, i18n] }
      })

      await router.push('/not-found')
      await router.isReady()
      const notFoundWrapper = mount(NotFound, {
        global: { plugins: [router, i18n] }
      })

      // Both should have centered container classes
      expect(unauthorizedWrapper.find('.unauthorized').exists()).toBe(true)
      expect(notFoundWrapper.find('.not-found').exists()).toBe(true)
    })
  })
})
