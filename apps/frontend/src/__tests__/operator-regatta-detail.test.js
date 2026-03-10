import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import RegattaDetail from '../views/operator/RegattaDetail.vue'

const SELECTED_CAPTURE_SESSIONS_STORAGE_KEY = 'rd_operator_selected_capture_sessions'

function installStorage() {
  const values = new Map()
  const storage = {
    getItem(key) {
      return values.has(key) ? values.get(key) : null
    },
    setItem(key, value) {
      values.set(key, String(value))
    },
    removeItem(key) {
      values.delete(key)
    },
    clear() {
      values.clear()
    }
  }

  if (globalThis.window) {
    Object.defineProperty(globalThis.window, 'localStorage', {
      value: storage,
      configurable: true
    })
  }

  Object.defineProperty(globalThis, 'localStorage', {
    value: storage,
    configurable: true
  })

  return storage
}

const listCaptureSessions = vi.fn()
const createCaptureSession = vi.fn()
const closeCaptureSession = vi.fn()

vi.mock('../api', () => ({
  createApiClient: vi.fn(() => ({})),
  createOperatorApi: vi.fn(() => ({
    listCaptureSessions,
    createCaptureSession,
    closeCaptureSession
  }))
}))

function createTestI18n() {
  return createI18n({
    legacy: false,
    locale: 'en',
    messages: {
      en: {
        common: {
          operator: 'Operator'
        },
        navigation: {
          line_scan: 'Line Scan'
        },
        operator: {
          regatta: {
            title: 'Regatta',
            id: 'Regatta ID',
            token_status: 'Operator token: {token}',
            no_token: 'Unavailable',
            station_context: 'Station: {station}',
            create_session: 'Create Capture Session',
            loading_sessions: 'Loading capture sessions...',
            open_session: 'Open Session',
            close_session: 'Close Session',
            session_summary: '{station} · {session_type} · {state}',
            sync_summary: 'Pending {pending_operations}, failed {failed_operations}'
          }
        }
      }
    }
  })
}

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/operator/regattas/:regattaId',
        name: 'operator-regatta-home',
        component: RegattaDetail
      },
      {
        path: '/operator/regattas/:regattaId/sessions',
        name: 'operator-regatta-sessions',
        component: { template: '<div>Sessions</div>' }
      },
      {
        path: '/operator/regattas/:regattaId/sessions/:captureSessionId/line-scan',
        name: 'operator-session-line-scan',
        component: { template: '<div>Workspace</div>' }
      }
    ]
  })
}

async function mountAtRegattaHome() {
  const router = createTestRouter()
  await router.push('/operator/regattas/regatta-138')
  await router.isReady()

  const wrapper = mount(RegattaDetail, {
    global: {
      plugins: [router, createTestI18n()]
    }
  })

  await flushPromises()

  return { router, wrapper }
}

describe('Operator regatta home for issue #138', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    const storage = installStorage()
    storage.clear()
    globalThis.__REGATTADESK_AUTH__ = {
      operatorToken: 'token-138',
      operatorStation: 'finish-line'
    }

    listCaptureSessions.mockResolvedValue([
      {
        id: 'session-138-a',
        station: 'finish-line',
        session_type: 'finish',
        state: 'open',
        sync_state: {
          pending_operations: 2,
          failed_operations: 0,
          last_synced_at: '2026-03-10T11:45:00Z'
        }
      },
      {
        id: 'session-138-b',
        station: 'finish-line',
        session_type: 'finish',
        state: 'closed',
        sync_state: {
          pending_operations: 0,
          failed_operations: 1,
          last_synced_at: '2026-03-10T10:00:00Z'
        }
      }
    ])
    createCaptureSession.mockResolvedValue({
      id: 'session-138-c',
      station: 'finish-line',
      session_type: 'finish',
      state: 'open',
      sync_state: {
        pending_operations: 0,
        failed_operations: 0,
        last_synced_at: '2026-03-10T12:00:00Z'
      }
    })
    closeCaptureSession.mockResolvedValue({
      id: 'session-138-a',
      station: 'finish-line',
      session_type: 'finish',
      state: 'closed',
      sync_state: {
        pending_operations: 0,
        failed_operations: 0,
        last_synced_at: '2026-03-10T12:05:00Z'
      }
    })
  })

  it('loads capture sessions and renders token status, station context, sync-state summary, and lifecycle controls', async () => {
    const { wrapper } = await mountAtRegattaHome()

    expect(listCaptureSessions).toHaveBeenCalledWith('regatta-138')
    expect(wrapper.find('[data-testid="operator-token-status"]').text()).toContain('token-138')
    expect(wrapper.find('[data-testid="operator-station-context"]').text()).toContain('finish-line')
    expect(wrapper.find('[data-testid="capture-session-list"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="capture-session-sync-summary-session-138-a"]').text()).toContain('2')
    expect(wrapper.find('[data-testid="capture-session-create"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="capture-session-select-session-138-a"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="capture-session-close-session-138-a"]').exists()).toBe(true)
  })

  it('selects a capture session through normal navigation and persists the selected session by regatta', async () => {
    const { router, wrapper } = await mountAtRegattaHome()

    await wrapper.find('[data-testid="capture-session-select-session-138-a"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe(
      '/operator/regattas/regatta-138/sessions/session-138-a/line-scan'
    )
    expect(JSON.parse(globalThis.localStorage.getItem(SELECTED_CAPTURE_SESSIONS_STORAGE_KEY))).toEqual({
      'regatta-138': 'session-138-a'
    })
  })

  it('creates a capture session and routes into the canonical session workspace', async () => {
    const { router, wrapper } = await mountAtRegattaHome()

    await wrapper.find('[data-testid="capture-session-create"]').trigger('click')
    await flushPromises()

    expect(createCaptureSession).toHaveBeenCalledWith(
      'regatta-138',
      expect.objectContaining({
        station: 'finish-line'
      })
    )
    expect(router.currentRoute.value.fullPath).toBe(
      '/operator/regattas/regatta-138/sessions/session-138-c/line-scan'
    )
  })

  it('closes the selected capture session and returns to the sessions list', async () => {
    globalThis.localStorage.setItem(
      SELECTED_CAPTURE_SESSIONS_STORAGE_KEY,
      JSON.stringify({
        'regatta-138': 'session-138-a'
      })
    )

    const { router, wrapper } = await mountAtRegattaHome()

    await wrapper.find('[data-testid="capture-session-close-session-138-a"]').trigger('click')
    await flushPromises()

    expect(closeCaptureSession).toHaveBeenCalledWith(
      'regatta-138',
      'session-138-a',
      expect.any(Object)
    )
    expect(router.currentRoute.value.fullPath).toBe('/operator/regattas/regatta-138/sessions')
  })
})
