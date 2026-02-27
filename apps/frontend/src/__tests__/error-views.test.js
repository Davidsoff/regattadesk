import { beforeEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import NotFound from '../views/NotFound.vue'
import Unauthorized from '../views/Unauthorized.vue'

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div>Home</div>' } },
      { path: '/unauthorized', name: 'unauthorized', component: Unauthorized },
      { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFound },
    ],
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
          unauthorized: { title: 'Unauthorized', description: 'You do not have permission.' },
          not_found: { title: 'Not Found', description: 'Page not found.' },
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

describe('Error View Components', () => {
  let router

  beforeEach(() => {
    router = createTestRouter()
  })

  const scenarios = [
    {
      name: 'Unauthorized',
      component: Unauthorized,
      route: '/unauthorized',
      rootClass: '.unauthorized',
      title: 'Unauthorized',
      description: 'You do not have permission.',
    },
    {
      name: 'NotFound',
      component: NotFound,
      route: '/missing-page',
      rootClass: '.not-found',
      title: 'Not Found',
      description: 'Page not found.',
    },
  ]

  scenarios.forEach(({ name, component, route, rootClass, title, description }) => {
    describe(name, () => {
      it('renders page shell and translated content', async () => {
        const wrapper = await mountAtRoute(router, route, component)

        expect(wrapper.find(rootClass).exists()).toBe(true)
        expect(wrapper.find('h2').text()).toBe(title)
        expect(wrapper.text()).toContain(description)
      })

      it('shows go-home button', async () => {
        const wrapper = await mountAtRoute(router, route, component)

        const button = wrapper.find('button')
        expect(button.exists()).toBe(true)
        expect(button.text()).toBe('Go to Home')
      })

      it('navigates to home on go-home click', async () => {
        const wrapper = await mountAtRoute(router, route, component)

        await wrapper.find('button').trigger('click')
        await flushPromises()

        expect(router.currentRoute.value.path).toBe('/')
      })
    })
  })

  it('keeps a consistent structure across error pages', async () => {
    const unauthorizedWrapper = await mountAtRoute(router, '/unauthorized', Unauthorized)
    const notFoundWrapper = await mountAtRoute(router, '/missing', NotFound)

    ;[unauthorizedWrapper, notFoundWrapper].forEach((wrapper) => {
      expect(wrapper.find('h2').exists()).toBe(true)
      expect(wrapper.find('p').exists()).toBe(true)
      expect(wrapper.find('button').exists()).toBe(true)
    })
  })
})
