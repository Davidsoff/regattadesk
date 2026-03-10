import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  SELECTED_CAPTURE_SESSIONS_STORAGE_KEY,
  installStorage,
  mountOperatorRegattaHome
} from './operatorTestUtils'
import RegattaDetail from '../views/operator/RegattaDetail.vue'

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
    const { wrapper } = await mountOperatorRegattaHome(RegattaDetail)

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
    const { router, wrapper } = await mountOperatorRegattaHome(RegattaDetail)

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
    const { router, wrapper } = await mountOperatorRegattaHome(RegattaDetail)

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

    const { router, wrapper } = await mountOperatorRegattaHome(RegattaDetail)

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

    const { router, wrapper } = await mountOperatorRegattaHome(RegattaDetail)

    await router.push('/operator/regattas/regatta-139')
    await flushPromises()

    expect(listCaptureSessions).toHaveBeenNthCalledWith(1, 'regatta-138')
    expect(listCaptureSessions).toHaveBeenNthCalledWith(2, 'regatta-139')
    expect(wrapper.text()).toContain('session-139-a')
    expect(wrapper.text()).not.toContain('session-138-a')
  })
})
