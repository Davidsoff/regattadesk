import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import RegattaDetail from '../views/operator/RegattaDetail.vue'

const SELECTED_CAPTURE_SESSIONS_STORAGE_KEY = 'rd_operator_selected_capture_sessions'

function jsonResponse(status, body) {
  return new Response(body === null ? '' : JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json',
    },
  })
}

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

function createTestI18n() {
  return createI18n({
    legacy: false,
    locale: 'en',
    messages: {
      en: {
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
            missing_block_scope: 'Operator token must include a block scope before starting a capture session.',
            create_session: 'Create Capture Session',
            loading_sessions: 'Loading capture sessions...',
            create_failed: 'Failed to create capture session.',
            close_failed: 'Failed to close capture session.',
            errors: {
              load_sessions_failed: 'Failed to load capture sessions.',
              create_failed: 'Failed to create capture session.',
              close_failed: 'Failed to close capture session.'
            },
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

describe('Operator regatta home integration for issue #138', () => {
  beforeEach(() => {
    installStorage().clear()
    vi.stubGlobal('fetch', vi.fn())
    globalThis.__REGATTADESK_AUTH__ = {
      operatorToken: 'token-138',
      operatorStation: 'finish-line',
      operatorBlockId: 'block-138'
    }
  })

  it('loads capture sessions through the real API layer and routes selection through the canonical workspace path', async () => {
    globalThis.fetch.mockResolvedValueOnce(
      jsonResponse(200, {
        capture_sessions: [
          {
            capture_session_id: 'session-138-a',
            block_id: 'block-138',
            station: 'finish-line',
            session_type: 'finish',
            state: 'open',
            is_synced: false,
            unsynced_reason: 'awaiting upload'
          }
        ]
      })
    )

    const { router, wrapper } = await mountAtRegattaHome()

    expect(globalThis.fetch).toHaveBeenCalledTimes(1)
    expect(globalThis.fetch.mock.calls[0][0].url).toBe(
      'http://localhost:3000/api/v1/regattas/regatta-138/operator/capture_sessions'
    )

    await wrapper.find('[data-testid="capture-session-select-session-138-a"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe(
      '/operator/regattas/regatta-138/sessions/session-138-a/line-scan'
    )
    expect(JSON.parse(globalThis.localStorage.getItem(SELECTED_CAPTURE_SESSIONS_STORAGE_KEY))).toEqual({
      'regatta-138': 'session-138-a'
    })
  })

  it('creates and closes capture sessions through the real API layer', async () => {
    globalThis.fetch
      .mockResolvedValueOnce(
        jsonResponse(200, {
          capture_sessions: [
            {
              capture_session_id: 'session-138-a',
              block_id: 'block-138',
              station: 'finish-line',
              session_type: 'finish',
              state: 'open',
              is_synced: false,
              unsynced_reason: 'awaiting upload'
            }
          ]
        })
      )
      .mockResolvedValueOnce(
        jsonResponse(201, {
          capture_session_id: 'session-138-b',
          block_id: 'block-138',
          station: 'finish-line',
          session_type: 'finish',
          state: 'open',
          is_synced: true
        })
      )
      .mockResolvedValueOnce(
        jsonResponse(200, {
          capture_session_id: 'session-138-a',
          block_id: 'block-138',
          station: 'finish-line',
          session_type: 'finish',
          state: 'closed',
          is_synced: true
        })
      )

    const { router, wrapper } = await mountAtRegattaHome()

    await wrapper.find('[data-testid="capture-session-create"]').trigger('click')
    await flushPromises()

    expect(globalThis.fetch.mock.calls[1][0].method).toBe('POST')
    expect(globalThis.fetch.mock.calls[1][0].url).toBe(
      'http://localhost:3000/api/v1/regattas/regatta-138/operator/capture_sessions'
    )
    const createBody = await globalThis.fetch.mock.calls[1][0].clone().text()
    expect(createBody).toContain('"block_id":"block-138"')
    expect(createBody).toContain('"fps":25')
    expect(router.currentRoute.value.fullPath).toBe(
      '/operator/regattas/regatta-138/sessions/session-138-b/line-scan'
    )

    globalThis.localStorage.setItem(
      SELECTED_CAPTURE_SESSIONS_STORAGE_KEY,
      JSON.stringify({ 'regatta-138': 'session-138-a' })
    )

    await router.push('/operator/regattas/regatta-138')
    await router.isReady()
    await flushPromises()

    await wrapper.find('[data-testid="capture-session-close-session-138-a"]').trigger('click')
    await flushPromises()

    expect(globalThis.fetch.mock.calls[2][0].method).toBe('POST')
    expect(globalThis.fetch.mock.calls[2][0].url).toBe(
      'http://localhost:3000/api/v1/regattas/regatta-138/operator/capture_sessions/session-138-a/close'
    )
    expect(router.currentRoute.value.fullPath).toBe('/operator/regattas/regatta-138/sessions')
  })
})
