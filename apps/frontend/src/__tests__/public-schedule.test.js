import { describe, expect, it, vi, afterEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
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
            empty: 'No schedule entries available for this revision.',
            headers: {
              lane: 'Lane',
              race: 'Race',
              status: 'Status',
            },
            errors: {
              missing_regatta: 'Missing regatta id in URL.',
              load_failed: 'Failed to load schedule data.',
            },
          },
          version: {
            draw: 'Draw Revision',
            results: 'Results Revision',
          },
        },
        status: {
          entered: 'Entered',
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

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Public Schedule View', () => {
  it('renders schedule from the API in a tabular layout for desktop/mobile workflows', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: {
        get: (header) => (header === 'content-type' ? 'application/json' : null),
      },
      json: async () => ({
        draw_revision: 2,
        results_revision: 3,
        data: [
          {
            entry_id: '6db46289-6849-45df-8d85-c7d25dcaea84',
            event_id: '2b458006-a032-4b52-b708-6af2eb5f675d',
            lane: 4,
            status: 'entered',
          },
        ],
      }),
    })
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountScheduleAt('/public/v2-3/schedule?regatta_id=94dce241-f82a-48c5-aa84-cd9f649cd878')
    await flushPromises()

    expect(wrapper.find(TABLE_WRAPPER_SELECTOR).exists()).toBe(true)
    expect(wrapper.find('table').exists()).toBe(true)
    expect(wrapper.findAll('th').length).toBe(3)
    expect(wrapper.text()).toContain('Lane')
    expect(wrapper.text()).toContain('Race')
    expect(wrapper.text()).toContain('Status')
    expect(wrapper.text()).toContain('4')
    expect(fetchMock).toHaveBeenCalledWith(
      '/public/v2-3/regattas/94dce241-f82a-48c5-aa84-cd9f649cd878/schedule',
      expect.objectContaining({ method: 'GET' })
    )
  })
})
