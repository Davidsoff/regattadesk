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
        },
        public: {
          results: {
            title: 'Results',
            description: 'Live race results',
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
    globalThis.fetch
      .mockResolvedValueOnce({
        ok: false,
        status: 401,
        headers: { get: () => 'application/json' },
        json: async () => ({ error: { code: 'UNAUTHORIZED', message: 'Missing or invalid public session' } }),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 204,
        headers: { get: () => '' },
      })
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
        json: async () => ({ draw_revision: 1, results_revision: 2, data: [] }),
      })

    await mountResults()
    await flushPromises()

    expect(globalThis.fetch).toHaveBeenCalledTimes(4)
    expect(globalThis.fetch.mock.calls[0][0]).toContain('/versions')
    expect(globalThis.fetch.mock.calls[1][0]).toContain('/public/session')
    expect(globalThis.fetch.mock.calls[2][0]).toContain('/versions')
    expect(globalThis.fetch.mock.calls[3][0]).toContain('/public/v1-2/regattas')
  })

  it('does not call /public/session when /versions succeeds on first attempt', async () => {
    globalThis.fetch
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: { get: () => 'application/json' },
        json: async () => ({ draw_revision: 4, results_revision: 9 }),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: { get: () => 'application/json' },
        json: async () => ({ draw_revision: 4, results_revision: 9, data: [] }),
      })

    await mountResults()
    await flushPromises()

    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
    expect(globalThis.fetch.mock.calls[0][0]).toContain('/versions')
    expect(globalThis.fetch.mock.calls.some(([url]) => String(url).includes('/public/session'))).toBe(false)
  })

  it('renders a live/offline indicator driven by SSE connectivity', async () => {
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
        json: async () => ({ draw_revision: 1, results_revision: 3, data: [{ entry_id: 'e2', crew_name: 'Crew B', status: 'entered', rank: 1 }] }),
      })

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

  it('applies snapshot and draw_revision SSE events to refresh revisions', async () => {
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
        json: async () => ({ draw_revision: 1, results_revision: 2, data: [] }),
      })

    const wrapper = await mountResults('/public/vfoo-bar/results?regatta_id=44444444-4444-4444-4444-444444444444')
    await flushPromises()

    expect(wrapper.text()).toContain('Draw Revision: 1')
    expect(wrapper.text()).toContain('Results Revision: 2')
    expect(globalThis.fetch.mock.calls.some(([url]) => String(url).includes('/public/v1-2/regattas/'))).toBe(true)
    expect(globalThis.fetch.mock.calls.some(([url]) => String(url).includes('NaN'))).toBe(false)
  })

  it('renders a safe fallback when result status is null', async () => {
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
        json: async () => ({ draw_revision: 1, results_revision: 2, data: [{ entry_id: 'e3', crew_name: 'Crew C', status: null, rank: 7 }] }),
      })

    const wrapper = await mountResults('/public/v1-2/results?regatta_id=55555555-5555-5555-5555-555555555555')
    await flushPromises()

    expect(wrapper.text()).toContain('7. Crew C (-)')
    expect(wrapper.text()).not.toContain('status.null')
  })
})
