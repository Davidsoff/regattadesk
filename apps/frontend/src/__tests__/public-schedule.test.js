import { describe, expect, it, vi, afterEach, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import ScheduleView from '../views/public/Schedule.vue'
import en from '../i18n/locales/en.json'
import nl from '../i18n/locales/nl.json'

const SCHEDULE_ROUTE_PATH = '/public/v:drawRevision-:resultsRevision/schedule'
const TABLE_WRAPPER_SELECTOR = '.rd-table-wrapper'
const REGATTA_ID_STORAGE_KEY = 'regattadesk_public_regatta_id'
const { apiGetMock } = vi.hoisted(() => ({
  apiGetMock: vi.fn(),
}))

vi.mock('../api', () => ({
  createApiClient: () => ({
    get: apiGetMock,
  }),
}))

function createTestI18n() {
  return createI18n({
    legacy: false,
    locale: 'en',
    messages: { en, nl },
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

  const i18n = createTestI18n()

  const wrapper = mount(ScheduleView, {
    global: {
      plugins: [router, i18n],
    },
  })

  return { wrapper, router, i18n }
}

afterEach(() => {
  apiGetMock.mockReset()
  vi.unstubAllGlobals()
})

describe('Public Schedule View', () => {
  beforeEach(() => {
    vi.stubGlobal('sessionStorage', {
      getItem: vi.fn(() => null),
      setItem: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn(),
      key: vi.fn(),
      length: 0,
    })
  })

  it('renders schedule details with locale-aware date formatting and mobile labels', async () => {
    apiGetMock.mockResolvedValue({
      draw_revision: 2,
      results_revision: 3,
      data: [
        {
          entry_id: '6db46289-6849-45df-8d85-c7d25dcaea84',
          event_id: 'M2x Heat 1',
          scheduled_start_time: '2026-05-10T09:15:00Z',
          crew_name: 'Crew Alpha',
          club_name: 'Riverside RC',
          bib: 14,
          lane: 4,
          status: 'entered',
        },
      ],
    })

    const { wrapper, i18n } = await mountScheduleAt(
      '/public/v2-3/schedule?regatta_id=94dce241-f82a-48c5-aa84-cd9f649cd878&timezone=Europe/Amsterdam'
    )
    await flushPromises()

    expect(wrapper.find(TABLE_WRAPPER_SELECTOR).exists()).toBe(true)
    expect(wrapper.find('table').exists()).toBe(true)
    expect(wrapper.findAll('th').length).toBe(6)
    expect(wrapper.text()).toContain('Start')
    expect(wrapper.text()).toContain('Event')
    expect(wrapper.text()).toContain('Crew')
    expect(wrapper.text()).toContain('Club')
    expect(wrapper.text()).toContain('Bib')
    expect(wrapper.text()).toContain('Crew Alpha')
    expect(wrapper.text()).toContain('Riverside RC')
    expect(wrapper.text()).toContain('14')
    expect(wrapper.text()).toContain('Status')
    expect(wrapper.text()).toContain('4')
    expect(wrapper.text()).toContain('2026-05-10 11:15')
    expect(wrapper.findAll('tbody td').every((node) => node.attributes('data-label'))).toBe(true)
    expect(apiGetMock).toHaveBeenCalledWith(
      '/public/v2-3/regattas/94dce241-f82a-48c5-aa84-cd9f649cd878/schedule',
    )

    i18n.global.locale.value = 'nl'
    await flushPromises()

    expect(wrapper.text()).toContain('10-05-2026 11:15')
    expect(wrapper.text()).toContain('Ingeschreven')
    expect(wrapper.text()).toContain('Startnr / Baan')
  })

  it('recovers missing regatta context from saved public state', async () => {
    apiGetMock.mockResolvedValue({
      draw_revision: 2,
      results_revision: 3,
      data: [],
    })

    sessionStorage.getItem.mockImplementation((key) => (
      key === REGATTA_ID_STORAGE_KEY ? 'saved-regatta-42' : null
    ))

    const { wrapper } = await mountScheduleAt('/public/v2-3/schedule')
    await flushPromises()

    expect(wrapper.text()).toContain('Use saved regatta')
    expect(wrapper.find('.recovery-banner__action').attributes('aria-label')).toBe('Use saved regatta')
    expect(apiGetMock).toHaveBeenCalledWith(
      '/public/v2-3/regattas/saved-regatta-42/schedule',
    )
  })

  it('ignores private browsing storage failures when recovering or persisting regatta context', async () => {
    const getItem = vi.fn(() => {
      throw new Error('storage denied')
    })
    const setItem = vi.fn(() => {
      throw new Error('storage denied')
    })

    vi.stubGlobal('sessionStorage', {
      getItem,
      setItem,
      removeItem: vi.fn(),
      clear: vi.fn(),
      key: vi.fn(),
      length: 0,
    })

    apiGetMock.mockResolvedValue({
      draw_revision: 2,
      results_revision: 3,
      data: [],
    })

    const { wrapper } = await mountScheduleAt(
      '/public/v2-3/schedule?regatta_id=94dce241-f82a-48c5-aa84-cd9f649cd878',
    )
    await flushPromises()

    expect(getItem).not.toHaveBeenCalled()
    expect(setItem).toHaveBeenCalledWith(
      'regattadesk_public_regatta_id',
      '94dce241-f82a-48c5-aa84-cd9f649cd878',
    )
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(apiGetMock).toHaveBeenCalledWith(
      '/public/v2-3/regattas/94dce241-f82a-48c5-aa84-cd9f649cd878/schedule',
    )
  })
})
