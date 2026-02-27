import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import ScheduleView from '../views/public/Schedule.vue'

const SCHEDULE_ROUTE_PATH = '/public/v:drawRevision-:resultsRevision/schedule'
const TABLE_WRAPPER_SELECTOR = '.rd-table-wrapper'

function createTestI18n() {
  return createI18n({
    legacy: false,
    locale: 'en',
    messages: {
      en: {
        public: {
          schedule: {
            title: 'Schedule',
            description: 'View the race schedule',
          },
          version: {
            draw: 'Draw Revision',
            results: 'Results Revision',
          },
        },
      },
    },
  })
}

async function mountScheduleAt(routePath) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: SCHEDULE_ROUTE_PATH,
        name: 'public-schedule',
        component: ScheduleView,
      },
    ],
  })

  await router.push(routePath)
  await router.isReady()

  return mount(ScheduleView, {
    global: {
      plugins: [router, createTestI18n()],
    },
  })
}

describe('Public Schedule View', () => {
  it('renders schedule in a tabular layout for desktop/mobile workflows', async () => {
    const wrapper = await mountScheduleAt('/public/v2-3/schedule')

    expect(wrapper.find(TABLE_WRAPPER_SELECTOR).exists()).toBe(true)
    expect(wrapper.find('table').exists()).toBe(true)
    expect(wrapper.findAll('th').length).toBeGreaterThan(0)
  })
})
