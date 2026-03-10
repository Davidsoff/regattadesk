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
            create_failed: 'Failed to create capture session.',
            close_failed: 'Failed to close capture session.',
            errors: {
              load_sessions_failed: 'Failed to load capture sessions.',
              create_failed: 'Failed to create capture session.',
              close_failed: 'Failed to close capture session.'
            },
            missing_block_scope: 'Operator token must include a block scope before starting a capture session.',
            open_session: 'Open Session',
            close_session: 'Close Session',
            session_summary: '{station} · {session_type} · {state}',
            sync_summary: 'Pending {pending_operations}, failed {failed_operations}',
            sync_synced: 'Sync status: synced',
            sync_pending: 'Sync status: pending ({reason})',
            sync_pending_default: 'awaiting upload',
            sync_attention: 'Sync status: attention required'
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
      operatorStation: 'finish-line',
      operatorBlockId: 'block-138'
    }

    listCaptureSessions.mockResolvedValue({
      capture_sessions: [
        {
          capture_session_id: 'session-138-a',
          block_id: 'block-138',
          station: 'finish-line',
          session_type: 'finish',
          state: 'open',
          is_synced: false,
          unsynced_reason: 'awaiting upload'
        },
        {
          capture_session_id: 'session-138-b',
          block_id: 'block-138',
          station: 'finish-line',
          session_type: 'finish',
          state: 'closed',
          is_synced: true
        }
      ]
    })
    createCaptureSession.mockResolvedValue({
      capture_session_id: 'session-138-c',
      block_id: 'block-138',
      station: 'finish-line',
      session_type: 'finish',
      state: 'open',
      is_synced: true
    })
    closeCaptureSession.mockResolvedValue({
      capture_session_id: 'session-138-a',
      block_id: 'block-138',
      station: 'finish-line',
      session_type: 'finish',
      state: 'closed',
      is_synced: true
    })
  })

  it('loads capture sessions and renders token status, station context, sync-state summary, and lifecycle controls', async () => {
    const { wrapper } = await mountAtRegattaHome()

    expect(listCaptureSessions).toHaveBeenCalledWith('regatta-138')
    expect(wrapper.find('[data-testid="operator-token-status"]').text()).toContain('token-138')
    expect(wrapper.find('[data-testid="operator-station-context"]').text()).toContain('finish-line')
    expect(wrapper.find('[data-testid="capture-session-list"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="capture-session-sync-summary-session-138-a"]').text()).toContain(
      'awaiting upload'
    )
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
        block_id: 'block-138',
        station: 'finish-line',
        fps: 25
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

  it('reloads capture sessions when the route regatta changes on the same component instance', async () => {
    listCaptureSessions
      .mockResolvedValueOnce({
        capture_sessions: [
          {
            capture_session_id: 'session-138-a',
            block_id: 'block-138',
            station: 'finish-line',
            session_type: 'finish',
            state: 'open',
            is_synced: true
          }
        ]
      })
      .mockResolvedValueOnce({
        capture_sessions: [
          {
            capture_session_id: 'session-139-a',
            block_id: 'block-139',
            station: 'finish-line',
            session_type: 'finish',
            state: 'open',
            is_synced: true
          }
        ]
      })

    const { router, wrapper } = await mountAtRegattaHome()

    await router.push('/operator/regattas/regatta-139')
    await flushPromises()

    expect(listCaptureSessions).toHaveBeenNthCalledWith(1, 'regatta-138')
    expect(listCaptureSessions).toHaveBeenNthCalledWith(2, 'regatta-139')
    expect(wrapper.text()).toContain('session-139-a')
    expect(wrapper.text()).not.toContain('session-138-a')
  })
})
