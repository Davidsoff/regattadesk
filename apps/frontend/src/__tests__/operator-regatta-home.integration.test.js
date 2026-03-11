import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  SELECTED_CAPTURE_SESSIONS_STORAGE_KEY,
  installStorage,
  mountOperatorRegattaHome
} from './operatorTestUtils'
import RegattaDetail from '../views/operator/RegattaDetail.vue'
import { jsonResponse } from './utils/testHelpers.js'


describe('Operator regatta home integration for issue #138', () => {
  beforeEach(() => {
    installStorage().clear()
    vi.stubGlobal('fetch', vi.fn())
    globalThis.__REGATTADESK_AUTH__ = {
      operatorAuth: 'token-138',
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

    const { router, wrapper } = await mountOperatorRegattaHome(RegattaDetail)

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

    const { router, wrapper } = await mountOperatorRegattaHome(RegattaDetail)

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
