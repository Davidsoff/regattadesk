import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { nextTick } from 'vue'
import ResultsView from '../views/public/Results.vue'

function createTestI18n() {
  return createI18n({
    legacy: false,
    locale: 'en',
    messages: {
      en: {
        live: {
          live: 'Live',
          offline: 'Offline',
          stale_data_message: 'Showing cached results. Reconnecting for latest updates.',
        },
        status: {
          entered: 'Entered',
          official: 'Official',
        },
        public: {
          results: {
            title: 'Results',
            description: 'Live race results',
            empty: 'No results published for this revision yet.',
            version_banner: 'Draw v{drawRevision}, Results v{resultsRevision}',
            version_link: 'Canonical results link',
            fields: {
              rank: 'Rank',
              crew: 'Crew',
              club: 'Club',
              time: 'Time',
              delta: 'Delta',
              status: 'Status',
            },
            penalties: {
              label: 'Penalty',
              seconds: '+{seconds}s',
            },
            recovery: {
              missing_regatta: 'Missing regatta context. Re-open the published results link for this regatta.',
              bootstrap_failed: 'Unable to refresh public results right now.',
              retry: 'Retry loading results',
            },
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

async function mountResults(routePath = '/public/v1-2/results?regatta_id=11111111-1111-1111-1111-111111111111') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/public/v:drawRevision-:resultsRevision/results',
        name: 'public-results',
        component: ResultsView,
      },
    ],
  })

  await router.push(routePath)
  await router.isReady()

  return mount(ResultsView, {
    global: {
      plugins: [router, createTestI18n()],
    },
  })
}

function jsonResponse(body, { status = 200, contentType = 'application/json' } = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => contentType },
    json: async () => body,
  }
}

function queueBootstrapSequence(fetchMock, {
  drawRevision = 1,
  resultsRevision = 2,
  resultData = [],
  requiresSessionBootstrap = false,
} = {}) {
  if (requiresSessionBootstrap) {
    fetchMock.mockResolvedValueOnce(jsonResponse(
      { error: { code: 'UNAUTHORIZED', message: 'Missing or invalid public session' } },
      { status: 401 },
    ))
    fetchMock.mockResolvedValueOnce(jsonResponse({}, { status: 204, contentType: '' }))
  }

  fetchMock.mockResolvedValueOnce(jsonResponse({ draw_revision: drawRevision, results_revision: resultsRevision }))
  fetchMock.mockResolvedValueOnce(jsonResponse({
    draw_revision: drawRevision,
    results_revision: resultsRevision,
    data: resultData,
  }))
}

function createSseHarness() {
  const listeners = {}
  const mockEventSource = {
    addEventListener: vi.fn((event, handler) => {
      listeners[event] = handler
    }),
    close: vi.fn(),
  }
  return { listeners, mockEventSource }
}

describe('Public Results bootstrap and live state (Issue #19)', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    vi.stubGlobal('EventSource', vi.fn(function MockEventSource() {
      return {
        addEventListener: vi.fn(),
        close: vi.fn(),
      }
    }))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('executes bootstrap fallback sequence: /versions -> /public/session -> /versions', async () => {
    queueBootstrapSequence(globalThis.fetch, { requiresSessionBootstrap: true })

    await mountResults()
    await flushPromises()

    expect(globalThis.fetch).toHaveBeenCalledTimes(4)
    expect(globalThis.fetch.mock.calls[0][0]).toContain('/versions')
    expect(globalThis.fetch.mock.calls[1][0]).toContain('/public/session')
    expect(globalThis.fetch.mock.calls[2][0]).toContain('/versions')
    expect(globalThis.fetch.mock.calls[3][0]).toContain('/public/v1-2/regattas')
  })

  it('does not call /public/session when /versions succeeds on first attempt', async () => {
    queueBootstrapSequence(globalThis.fetch, { drawRevision: 4, resultsRevision: 9 })

    await mountResults()
    await flushPromises()

    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
    expect(globalThis.fetch.mock.calls[0][0]).toContain('/versions')
    expect(globalThis.fetch.mock.calls.some(([url]) => String(url).includes('/public/session'))).toBe(false)
  })

  it('renders a live/offline indicator driven by SSE connectivity', async () => {
    const { listeners, mockEventSource } = createSseHarness()
    globalThis.EventSource.mockImplementation(function MockEventSource() {
      return mockEventSource
    })

    const wrapper = await mountResults()
    await nextTick()

    const indicator = wrapper.find('[data-testid="public-live-indicator"]')
    expect(indicator.exists()).toBe(true)
    expect(indicator.text()).toContain('Offline')

    listeners.open?.()
    await nextTick()
    expect(indicator.text()).toContain('Live')

    listeners.error?.()
    await nextTick()
    expect(indicator.text()).toContain('Offline')
    expect(wrapper.find('[data-testid="public-stale-data-banner"]').exists()).toBe(true)

    listeners.open?.()
    await nextTick()
    expect(wrapper.find('[data-testid="public-stale-data-banner"]').exists()).toBe(false)
  })

  it('updates visible revision and refetches results when a results_revision SSE event is received', async () => {
    const { listeners, mockEventSource } = createSseHarness()
    globalThis.EventSource.mockImplementation(function MockEventSource() {
      return mockEventSource
    })

    queueBootstrapSequence(globalThis.fetch, {
      drawRevision: 1,
      resultsRevision: 2,
      resultData: [{ entry_id: 'e1', crew_name: 'Crew A', status: 'entered', rank: 1 }],
    })
    queueBootstrapSequence(globalThis.fetch, {
      drawRevision: 1,
      resultsRevision: 2,
      resultData: [{ entry_id: 'e1', crew_name: 'Crew A', status: 'entered', rank: 1 }],
    })
    globalThis.fetch.mockResolvedValueOnce(jsonResponse({
      draw_revision: 1,
      results_revision: 3,
      data: [{ entry_id: 'e2', crew_name: 'Crew B', status: 'entered', rank: 1 }],
    }))

    const wrapper = await mountResults('/public/v1-2/results?regatta_id=22222222-2222-2222-2222-222222222222')
    await nextTick()

    listeners.open?.()
    await flushPromises()
    listeners.results_revision?.({
      data: JSON.stringify({
        draw_revision: 1,
        results_revision: 3,
      }),
      lastEventId: '22222222-2222-2222-2222-222222222222:1:3:7',
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Results Revision: 3')
    expect(globalThis.fetch.mock.calls.some(([url]) => String(url).includes('/public/v1-3/regattas/'))).toBe(true)
  })

  it('ignores stale SSE fetch results when a newer revision fetch completes first', async () => {
    const listeners = {}
    const mockEventSource = {
      addEventListener: vi.fn((event, handler) => {
        listeners[event] = handler
      }),
      close: vi.fn(),
    }
    globalThis.EventSource.mockImplementation(function MockEventSource() {
      return mockEventSource
    })

    let resolveSlowerResults
    const slowerResultsPromise = new Promise((resolve) => {
      resolveSlowerResults = resolve
    })

    globalThis.fetch
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: { get: () => 'application/json' },
        json: async () => ({ draw_revision: 1, results_revision: 2 }),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: { get: () => 'application/json' },
        json: async () => ({ draw_revision: 1, results_revision: 2, data: [{ entry_id: 'e1', crew_name: 'Crew A', status: 'entered', rank: 1 }] }),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: { get: () => 'application/json' },
        json: async () => slowerResultsPromise,
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: { get: () => 'application/json' },
        json: async () => ({ draw_revision: 1, results_revision: 4, data: [{ entry_id: 'e3', crew_name: 'Crew C', status: 'entered', rank: 1 }] }),
      })

    const wrapper = await mountResults('/public/v1-2/results?regatta_id=44444444-4444-4444-4444-444444444444')
    await flushPromises()

    listeners.results_revision?.({
      data: JSON.stringify({
        draw_revision: 1,
        results_revision: 3,
      }),
      lastEventId: '44444444-4444-4444-4444-444444444444:1:3:9',
    })
    listeners.results_revision?.({
      data: JSON.stringify({
        draw_revision: 1,
        results_revision: 4,
      }),
      lastEventId: '44444444-4444-4444-4444-444444444444:1:4:10',
    })
    await flushPromises()

    resolveSlowerResults({
      draw_revision: 1,
      results_revision: 3,
      data: [{ entry_id: 'e2', crew_name: 'Crew B', status: 'entered', rank: 1 }],
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Results Revision: 4')
    expect(wrapper.text()).toContain('Crew C')
    expect(wrapper.text()).not.toContain('Crew B')
  })

  it('ignores stale failed fetches when a newer revision has already loaded', async () => {
    const listeners = {}
    const mockEventSource = {
      addEventListener: vi.fn((event, handler) => {
        listeners[event] = handler
      }),
      close: vi.fn(),
    }
    globalThis.EventSource.mockImplementation(function MockEventSource() {
      return mockEventSource
    })

    let rejectOlderResults
    const olderResultsPromise = new Promise((_, reject) => {
      rejectOlderResults = reject
    })

    globalThis.fetch
      .mockResolvedValueOnce(jsonResponse({ draw_revision: 1, results_revision: 2 }))
      .mockResolvedValueOnce(jsonResponse({
        draw_revision: 1,
        results_revision: 2,
        data: [{ entry_id: 'e1', crew_name: 'Crew A', status: 'entered', rank: 1 }],
      }))
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: { get: () => 'application/json' },
        json: async () => olderResultsPromise,
      })
      .mockResolvedValueOnce(jsonResponse({
        draw_revision: 1,
        results_revision: 4,
        data: [{ entry_id: 'e4', crew_name: 'Crew D', status: 'entered', rank: 1 }],
      }))

    const wrapper = await mountResults('/public/v1-2/results?regatta_id=45454545-4545-4545-4545-454545454545')
    await flushPromises()

    listeners.results_revision?.({
      data: JSON.stringify({
        draw_revision: 1,
        results_revision: 3,
      }),
    })
    listeners.results_revision?.({
      data: JSON.stringify({
        draw_revision: 1,
        results_revision: 4,
      }),
    })
    await flushPromises()

    rejectOlderResults(new Error('stale request failed'))
    await flushPromises()

    expect(wrapper.text()).toContain('Results Revision: 4')
    expect(wrapper.text()).toContain('Crew D')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('applies snapshot and draw_revision SSE events to refresh revisions', async () => {
    const { listeners, mockEventSource } = createSseHarness()
    globalThis.EventSource.mockImplementation(function MockEventSource() {
      return mockEventSource
    })

    const wrapper = await mountResults('/public/v1-2/results?regatta_id=33333333-3333-3333-3333-333333333333')
    await nextTick()

    listeners.snapshot?.({
      data: JSON.stringify({
        draw_revision: 2,
        results_revision: 4,
      }),
    })
    await nextTick()
    expect(wrapper.text()).toContain('Draw Revision: 2')
    expect(wrapper.text()).toContain('Results Revision: 4')

    listeners.draw_revision?.({
      data: JSON.stringify({
        draw_revision: 5,
        results_revision: 4,
      }),
    })
    await nextTick()
    expect(wrapper.text()).toContain('Draw Revision: 5')
    expect(wrapper.text()).toContain('Results Revision: 4')
  })

  it('defaults invalid route revisions to 0 instead of NaN', async () => {
    queueBootstrapSequence(globalThis.fetch)

    const wrapper = await mountResults('/public/vfoo-bar/results?regatta_id=44444444-4444-4444-4444-444444444444')
    await flushPromises()

    expect(wrapper.text()).toContain('Draw Revision: 1')
    expect(wrapper.text()).toContain('Results Revision: 2')
    expect(globalThis.fetch.mock.calls.some(([url]) => String(url).includes('/public/v1-2/regattas/'))).toBe(true)
    expect(globalThis.fetch.mock.calls.some(([url]) => String(url).includes('NaN'))).toBe(false)
  })

  it('renders a safe fallback when result status is null', async () => {
    queueBootstrapSequence(globalThis.fetch, {
      resultData: [{ entry_id: 'e3', crew_name: 'Crew C', status: null, rank: 7 }],
    })

    const wrapper = await mountResults('/public/v1-2/results?regatta_id=55555555-5555-5555-5555-555555555555')
    await flushPromises()

    expect(wrapper.text()).toContain('Crew C')
    expect(wrapper.text()).toContain('Status')
    expect(wrapper.text()).toContain('-')
    expect(wrapper.text()).not.toContain('status.null')
  })

  it('shows a recovery alert instead of silently failing when regatta context is missing', async () => {
    const sessionStorageMock = {
      getItem: vi.fn(() => null),
      setItem: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn(),
      key: vi.fn(),
      length: 0,
    }
    vi.stubGlobal('sessionStorage', sessionStorageMock)

    const wrapper = await mountResults('/public/v1-2/results')
    await flushPromises()

    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('Missing regatta context. Re-open the published results link for this regatta.')
    expect(globalThis.fetch).not.toHaveBeenCalled()
  })

  it('shows a retryable bootstrap recovery message when versions loading fails', async () => {
    globalThis.fetch
      .mockResolvedValueOnce(jsonResponse({ error: { code: 'UNAVAILABLE' } }, { status: 503 }))
      .mockResolvedValueOnce(jsonResponse({ draw_revision: 3, results_revision: 8 }))
      .mockResolvedValueOnce(jsonResponse({
        draw_revision: 3,
        results_revision: 8,
        data: [],
      }))

    const wrapper = await mountResults()
    await flushPromises()

    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('Unable to refresh public results right now.')

    await wrapper.get('[data-testid="public-results-retry"]').trigger('click')
    await flushPromises()

    expect(globalThis.fetch).toHaveBeenCalledTimes(3)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('does not show the empty state before a successful results fetch completes', async () => {
    let resolveResults
    globalThis.fetch
      .mockResolvedValueOnce(jsonResponse({ draw_revision: 3, results_revision: 8 }))
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: { get: () => 'application/json' },
        json: async () => new Promise((resolve) => {
          resolveResults = resolve
        }),
      })

    const wrapper = await mountResults()
    await flushPromises()

    expect(wrapper.find('.results-empty').exists()).toBe(false)
    expect(wrapper.find('[data-testid="public-results-list"]').exists()).toBe(false)

    resolveResults({ draw_revision: 3, results_revision: 8, data: [] })
    await flushPromises()

    expect(wrapper.find('.results-empty').exists()).toBe(true)
  })

  it('renders mobile-first result cards with club, times, labels, and penalties when available', async () => {
    queueBootstrapSequence(globalThis.fetch, {
      resultData: [
        {
          entry_id: 'e5',
          crew_name: 'Crew E',
          club_name: 'River Club',
          status: 'entered',
          result_label: 'official',
          rank: 2,
          elapsed_time_ms: 94567,
          delta_time_ms: 2100,
          penalty_seconds: 5,
          penalty_reason: 'Lane infringement',
        },
      ],
    })

    const wrapper = await mountResults('/public/v1-2/results?regatta_id=66666666-6666-6666-6666-666666666666')
    await flushPromises()

    expect(wrapper.find('[data-testid="public-results-version-banner"]').text()).toContain('Draw v1, Results v2')
    expect(wrapper.find('[data-testid="public-results-version-link"]').attributes('href')).toBe('/public/v1-2/results?regatta_id=66666666-6666-6666-6666-666666666666')

    const cards = wrapper.findAll('[data-testid="public-results-card"]')
    expect(cards).toHaveLength(1)
    expect(cards[0].text()).toContain('Rank')
    expect(cards[0].text()).toContain('Crew')
    expect(cards[0].text()).toContain('Club')
    expect(cards[0].text()).toContain('Time')
    expect(cards[0].text()).toContain('Delta')
    expect(cards[0].text()).toContain('Status')
    expect(cards[0].text()).toContain('Crew E')
    expect(cards[0].text()).toContain('River Club')
    expect(cards[0].text()).toContain('1:34.567')
    expect(cards[0].text()).toContain('+0:02.100')
    expect(cards[0].text()).toContain('Official')
    expect(cards[0].text()).toContain('Penalty')
    expect(cards[0].text()).toContain('+5s')
    expect(cards[0].text()).toContain('Lane infringement')
  })

  it('does not render a penalty row when penalty seconds are zero without a reason', async () => {
    queueBootstrapSequence(globalThis.fetch, {
      resultData: [
        {
          entry_id: 'e6',
          crew_name: 'Crew F',
          club_name: 'Canal Club',
          status: 'entered',
          rank: 3,
          penalty_seconds: 0,
        },
      ],
    })

    const wrapper = await mountResults('/public/v1-2/results?regatta_id=67676767-6767-6767-6767-676767676767')
    await flushPromises()

    expect(wrapper.find('[data-testid="public-results-card"]').text()).not.toContain('Penalty')
    expect(wrapper.find('.results-card__penalty').exists()).toBe(false)
  })
})
