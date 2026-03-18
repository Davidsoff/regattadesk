import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import LineScanCapture from '../components/operator/LineScanCapture.vue'

vi.mock('../composables/useOperatorTheme', async () => {
  const { ref } = await import('vue')
  return {
    useOperatorTheme: vi.fn(() => ({
      isHighContrast: ref(true),
      toggleContrast: vi.fn()
    }))
  }
})

function createTestI18n() {
  return createI18n({
    legacy: false,
    locale: 'en',
    messages: {
      en: {
        operator: {
          regatta: {
            sync_synced: 'Synced',
            sync_pending: 'Sync pending ({reason})',
            sync_pending_default: 'awaiting upload',
            sync_attention: 'Attention required'
          },
          capture: {
            title: 'Line Scan Capture',
            overview: 'Overview',
            detail: 'Detail View',
            markers: 'Markers',
            unlinked_markers: 'Unlinked Markers',
            linked_markers: 'Linked Markers',
            create_marker: 'Create Marker',
            delete_marker: 'Delete Marker',
            link_to_bib: 'Link to Bib',
            unlink: 'Unlink',
            approve: 'Approve',
            locked: 'Locked',
            bib_number: 'Bib',
            frame_offset: 'Frame',
            timestamp: 'Time',
            no_markers: 'No markers yet',
            marker_approved: 'Cannot modify approved marker',
            undo: 'Undo',
            high_contrast_on: 'High Contrast: On',
            high_contrast_off: 'High Contrast: Off',
            session_status: 'Session Status',
            session_status_refresh: 'Refresh',
            session_status_loading: 'Loading...',
            tile_status: 'Tile Status',
            tile_status_synced: 'Synced',
            tile_status_pending: 'Sync pending',
            conflicts_pending_title: 'Conflicts Requiring Resolution',
            conflict_marker: 'Conflict for marker {id}',
            conflict_keep_mine: 'Keep Mine',
            conflict_use_server: 'Use Server',
            conflict_detected: 'A conflict was detected. See the conflict panel below.',
            errors: {
              capture_session_required: 'Capture session is required',
              failed_load_markers: 'Failed to load markers',
              failed_load_session_status: 'Failed to load session status',
              failed_create_marker: 'Failed to create marker',
              failed_delete_marker: 'Failed to delete marker',
              marker_approved: 'Cannot modify approved marker',
              entry_id_required: 'Entry ID is required',
              entry_id_invalid: 'Entry ID format is invalid',
              failed_update_marker: 'Failed to update marker',
              failed_link_marker: 'Failed to link marker',
              failed_unlink_marker: 'Failed to unlink marker',
              failed_undo: 'Failed to undo'
            }
          }
        }
      }
    }
  })
}

function jsonResponse(status, body) {
  return new Response(body === null ? '' : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

function getRequest(fetchMock, callIndex = 0) {
  return fetchMock.mock.calls[callIndex][0]
}

function buildMarker(id = 'marker-1', overrides = {}) {
  return {
    id,
    capture_session_id: 'session-1',
    entry_id: null,
    frame_offset: 1000,
    timestamp_ms: 1609459200000,
    is_linked: false,
    is_approved: false,
    tile_id: 'tile-1',
    tile_x: 5,
    tile_y: 3,
    ...overrides
  }
}

function buildCaptureSession(id = 'session-1', overrides = {}) {
  return {
    id,
    regatta_id: 'regatta-97',
    block_id: 'block-1',
    station: 'finish-line',
    device_id: 'device-1',
    session_type: 'finish',
    state: 'open',
    server_time_at_start: '2026-01-01T10:00:00Z',
    fps: 30,
    is_synced: true,
    drift_exceeded_threshold: false,
    unsynced_reason: '',
    ...overrides
  }
}

async function mountLineScanCapture(props = {}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/operator/regattas/:regattaId/capture',
        name: 'operator-capture',
        component: LineScanCapture
      }
    ]
  })

  await router.push('/operator/regattas/regatta-97/capture')
  await router.isReady()

  return mount(LineScanCapture, {
    props: {
      captureSessionId: 'session-1',
      regattaId: 'regatta-97',
      ...props
    },
    global: {
      plugins: [router, createTestI18n()]
    }
  })
}

async function runLinkFlow(wrapper, markerTestId, entryId = 'entry-99') {
  await wrapper.find(`[data-testid="link-marker-${markerTestId}"]`).trigger('click')
  await wrapper.find('[data-testid="link-entry-input"]').setValue(entryId)
  await wrapper.find('[data-testid="link-entry-submit"]').trigger('click')
  await flushPromises()
}

async function mountWithFetch(fetchMock, props = {}) {
  vi.stubGlobal('fetch', fetchMock)
  const wrapper = await mountLineScanCapture(props)
  await flushPromises()
  return wrapper
}

function makeLinkConflictMock({
  marker = buildMarker('marker-1', { is_linked: false }),
  code = 'DATA_CONFLICT',
  message = 'Conflict detected',
  afterConflict = []
} = {}) {
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(jsonResponse(200, [marker]))
    .mockResolvedValueOnce(jsonResponse(409, { error: { code, message } }))

  for (const response of afterConflict) {
    fetchMock.mockResolvedValueOnce(response)
  }

  return fetchMock
}

async function mockThemeAndMount(isHighContrast, toggleContrast = vi.fn()) {
  const { ref } = await import('vue')
  const { useOperatorTheme } = await import('../composables/useOperatorTheme')
  vi.mocked(useOperatorTheme).mockReturnValue({
    isHighContrast: ref(isHighContrast),
    toggleContrast
  })
  const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, []))
  const wrapper = await mountWithFetch(fetchMock)
  return { wrapper, toggleContrast }
}

async function updateMarkerFrame(wrapper, frameOffset) {
  await wrapper.find('[data-testid="edit-marker-marker-1"]').trigger('click')
  await wrapper.find('[data-testid="marker-frame-input"]').setValue(String(frameOffset))
  await wrapper.find('[data-testid="update-marker-marker-1"]').trigger('click')
  await flushPromises()
}

async function expectMarkerPatch(fetchMock, callIndex, frameOffset) {
  const request = getRequest(fetchMock, callIndex)
  expect(request.url).toContain('/api/v1/regattas/regatta-97/operator/markers/marker-1')
  expect(request.method).toBe('PATCH')
  await expect(request.json()).resolves.toEqual({ frame_offset: frameOffset })
}

beforeEach(() => {
  vi.restoreAllMocks()
  vi.stubGlobal('__REGATTADESK_AUTH__', {
    operatorAuth: 'token-97'
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('LineScanCapture component - Marker CRUD', () => {
  it('renders empty state when no markers exist', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, []))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    expect(wrapper.find('[data-testid="capture-markers-list"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('No markers yet')
  })

  it('loads and displays markers on mount', async () => {
    const markers = [buildMarker('marker-1'), buildMarker('marker-2', { frame_offset: 2000 })]
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, markers))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const request = getRequest(fetchMock, 0)
    expect(request.url).toContain('/api/v1/regattas/regatta-97/operator/markers')
    expect(request.url).toContain('capture_session_id=session-1')
    expect(request.headers.get('x_operator_token')).toBe('token-97')
    expect(wrapper.findAll('[data-testid^="marker-item-"]')).toHaveLength(2)
  })

  it('creates a new marker when create button is clicked', async () => {
    const newMarker = buildMarker('marker-new', { frame_offset: 3000 })
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, []))
      .mockResolvedValueOnce(jsonResponse(201, newMarker))

    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    await wrapper.find('[data-testid="create-marker-button"]').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    const request = getRequest(fetchMock, 1)
    expect(request.url).toContain('/api/v1/regattas/regatta-97/operator/markers')
    expect(request.method).toBe('POST')
    expect(request.headers.get('x_operator_token')).toBe('token-97')
    expect(wrapper.findAll('[data-testid^="marker-item-"]')).toHaveLength(1)
  })

  it('deletes a marker when delete button is clicked', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, [buildMarker('marker-1')]))
      .mockResolvedValueOnce(jsonResponse(204, null))

    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    await wrapper.find('[data-testid="delete-marker-marker-1"]').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    const request = getRequest(fetchMock, 1)
    expect(request.url).toContain('/api/v1/regattas/regatta-97/operator/markers/marker-1')
    expect(request.method).toBe('DELETE')
    expect(wrapper.findAll('[data-testid^="marker-item-"]')).toHaveLength(0)
  })

  it('prevents deletion of approved markers', async () => {
    const approvedMarker = buildMarker('marker-approved', {
      is_linked: true,
      is_approved: true,
      entry_id: 'entry-1'
    })
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, [approvedMarker]))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    const deleteButton = wrapper.find('[data-testid="delete-marker-marker-approved"]')
    expect(deleteButton.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Locked')
  })
})

describe('LineScanCapture component - Marker Linking', () => {
  it('shows unlinked markers first in the list', async () => {
    const markers = [
      buildMarker('marker-linked', { is_linked: true, entry_id: 'entry-1' }),
      buildMarker('marker-unlinked-1', { is_linked: false }),
      buildMarker('marker-unlinked-2', { is_linked: false })
    ]

    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, markers))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    const markerItems = wrapper.findAll('[data-testid^="marker-item-"]')
    expect(markerItems).toHaveLength(3)
    expect(markerItems[0].attributes('data-testid')).toContain('marker-unlinked')
    expect(markerItems[1].attributes('data-testid')).toContain('marker-unlinked')
    expect(markerItems[2].attributes('data-testid')).toContain('marker-linked')
  })

  it('links a marker to an entry when link button is clicked', async () => {
    const unlinkedMarker = buildMarker('marker-1', { is_linked: false })
    const linkedMarker = buildMarker('marker-1', { is_linked: true, entry_id: 'entry-99' })

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, [unlinkedMarker]))
      .mockResolvedValueOnce(jsonResponse(200, linkedMarker))

    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    await runLinkFlow(wrapper, 'marker-1')

    expect(fetchMock).toHaveBeenCalledTimes(2)
    const request = getRequest(fetchMock, 1)
    expect(request.url).toContain('/api/v1/regattas/regatta-97/operator/markers/marker-1/link')
    expect(request.method).toBe('POST')
    await expect(request.json()).resolves.toEqual({ entry_id: 'entry-99' })
  })

  it('unlinks a marker when unlink button is clicked', async () => {
    const linkedMarker = buildMarker('marker-1', { is_linked: true, entry_id: 'entry-1' })
    const unlinkedMarker = buildMarker('marker-1', { is_linked: false, entry_id: null })

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, [linkedMarker]))
      .mockResolvedValueOnce(jsonResponse(200, unlinkedMarker))

    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    await wrapper.find('[data-testid="unlink-marker-marker-1"]').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    const request = getRequest(fetchMock, 1)
    expect(request.url).toContain('/api/v1/regattas/regatta-97/operator/markers/marker-1/unlink')
    expect(request.method).toBe('POST')
  })

  it('prevents unlinking of approved markers', async () => {
    const approvedMarker = buildMarker('marker-approved', {
      is_linked: true,
      is_approved: true,
      entry_id: 'entry-1'
    })

    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, [approvedMarker]))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    expect(wrapper.find('[data-testid="unlink-marker-marker-approved"]').exists()).toBe(false)
  })
})

describe('LineScanCapture component - Approved Marker Lock States', () => {
  it('displays locked indicator for approved markers', async () => {
    const approvedMarker = buildMarker('marker-approved', {
      is_linked: true,
      is_approved: true,
      entry_id: 'entry-1'
    })

    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, [approvedMarker]))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    expect(wrapper.find('[data-testid="marker-locked-marker-approved"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Locked')
  })

  it('prevents editing frame offset of approved markers', async () => {
    const approvedMarker = buildMarker('marker-approved', {
      is_linked: true,
      is_approved: true,
      entry_id: 'entry-1'
    })

    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, [approvedMarker]))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    expect(wrapper.find('[data-testid="update-marker-marker-approved"]').exists()).toBe(false)
  })

  it('prevents linking approved markers that are still unlinked', async () => {
    const approvedMarker = buildMarker('marker-approved', {
      is_linked: false,
      is_approved: true,
      entry_id: null
    })

    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, [approvedMarker]))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    expect(wrapper.find('[data-testid="link-marker-marker-approved"]').exists()).toBe(false)
  })

  it('handles approved marker guard when attempting direct modification', async () => {
    const approvedMarker = buildMarker('marker-approved', {
      is_linked: true,
      is_approved: true,
      entry_id: 'entry-1'
    })

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, [approvedMarker]))
      .mockResolvedValueOnce(
        jsonResponse(409, {
          error: {
            code: 'MARKER_APPROVED',
            message: 'Cannot modify approved marker'
          }
        })
      )

    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    await wrapper.vm.deleteMarker('marker-approved')
    await flushPromises()

    expect(wrapper.find('[data-testid="error-message"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Cannot modify approved marker')
  })
})

describe('LineScanCapture component - Marker State Transitions', () => {
  it('updates marker frame offset', async () => {
    const marker = buildMarker('marker-1', { frame_offset: 1000 })
    const updatedMarker = buildMarker('marker-1', { frame_offset: 1500 })

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, [marker]))
      .mockResolvedValueOnce(jsonResponse(200, updatedMarker))

    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    await updateMarkerFrame(wrapper, 1500)

    expect(fetchMock).toHaveBeenCalledTimes(2)
    await expectMarkerPatch(fetchMock, 1, 1500)
  })

  it('supports undo of marker position changes', async () => {
    const marker = buildMarker('marker-1', { frame_offset: 1000 })
    const updatedMarker = buildMarker('marker-1', { frame_offset: 1500 })
    const undoneMarker = buildMarker('marker-1', { frame_offset: 1000 })

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, [marker]))
      .mockResolvedValueOnce(jsonResponse(200, updatedMarker))
      .mockResolvedValueOnce(jsonResponse(200, undoneMarker))

    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    await updateMarkerFrame(wrapper, 1500)
    await wrapper.find('[data-testid="undo-last-change"]').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledTimes(3)
    await expectMarkerPatch(fetchMock, 2, 1000)
  })
})

describe('LineScanCapture component - Conflict Resolution UI', () => {
  it('shows conflict panel when a data conflict is detected on link', async () => {
    const fetchMock = makeLinkConflictMock()
    const wrapper = await mountWithFetch(fetchMock)

    await runLinkFlow(wrapper, 'marker-1')

    expect(wrapper.find('[data-testid="conflict-resolution-pane"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid^="conflict-keep-mine-"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid^="conflict-use-server-"]').exists()).toBe(true)
  })

  it('resolving conflict with keep-mine removes it from the panel', async () => {
    const fetchMock = makeLinkConflictMock()
    const wrapper = await mountWithFetch(fetchMock)

    await runLinkFlow(wrapper, 'marker-1')

    expect(wrapper.find('[data-testid="conflict-resolution-pane"]').exists()).toBe(true)

    await wrapper.find('[data-testid^="conflict-keep-mine-"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="conflict-resolution-pane"]').exists()).toBe(false)
  })

  it('resolving conflict with use-server removes conflict and reloads markers', async () => {
    const reloadedMarkers = [buildMarker('marker-1', { is_linked: true, entry_id: 'entry-server' })]
    const fetchMock = makeLinkConflictMock({
      afterConflict: [jsonResponse(200, reloadedMarkers)]
    })
    const wrapper = await mountWithFetch(fetchMock)

    await runLinkFlow(wrapper, 'marker-1')

    await wrapper.find('[data-testid^="conflict-use-server-"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="conflict-resolution-pane"]').exists()).toBe(false)
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it('does not show conflict panel for MARKER_APPROVED 409 responses', async () => {
    const fetchMock = makeLinkConflictMock({
      marker: buildMarker('marker-approved', { is_linked: false, is_approved: false }),
      code: 'MARKER_APPROVED',
      message: 'Cannot modify approved marker'
    })
    const wrapper = await mountWithFetch(fetchMock)

    await runLinkFlow(wrapper, 'marker-approved')

    expect(wrapper.find('[data-testid="conflict-resolution-pane"]').exists()).toBe(false)
  })
})

describe('LineScanCapture component - High-Contrast Controls', () => {
  it('shows high-contrast toggle button', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, []))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    expect(wrapper.find('[data-testid="toggle-high-contrast"]').exists()).toBe(true)
  })

  it.each([
    { enabled: true, expectedLabel: 'High Contrast: On' },
    { enabled: false, expectedLabel: 'High Contrast: Off' }
  ])('toggle button label reflects current high-contrast mode', async ({ enabled, expectedLabel }) => {
    const { wrapper } = await mockThemeAndMount(enabled)
    expect(wrapper.find('[data-testid="toggle-high-contrast"]').text()).toContain(expectedLabel)
  })

  it('clicking toggle button calls toggleContrast', async () => {
    const mockToggle = vi.fn()
    const { wrapper } = await mockThemeAndMount(true, mockToggle)

    await wrapper.find('[data-testid="toggle-high-contrast"]').trigger('click')

    expect(mockToggle).toHaveBeenCalledTimes(1)
  })
})

describe('LineScanCapture component - Session Status and Tile Pane', () => {
  it('shows session status refresh button', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, []))
    const wrapper = await mountWithFetch(fetchMock)

    expect(wrapper.find('[data-testid="load-session-status"]').exists()).toBe(true)
  })

  it('loads session status when refresh button is clicked and shows sync state', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, []))
      .mockResolvedValueOnce(jsonResponse(200, buildCaptureSession()))

    const wrapper = await mountWithFetch(fetchMock)

    await wrapper.find('[data-testid="load-session-status"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="session-status-detail"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="session-sync-indicator"]').text()).toContain('Synced')
  })

  it('shows unsynced reason when session status is pending', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, []))
      .mockResolvedValueOnce(
        jsonResponse(
          200,
          buildCaptureSession('session-1', {
            is_synced: false,
            unsynced_reason: 'Awaiting manifest upload'
          })
        )
      )

    const wrapper = await mountWithFetch(fetchMock)

    await wrapper.find('[data-testid="load-session-status"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="session-sync-indicator"]').text()).toContain('Sync pending')
    expect(wrapper.find('[data-testid="session-sync-reason"]').text()).toContain('Awaiting manifest upload')
  })

  it('shows session status error when session refresh fails', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, []))
      .mockRejectedValueOnce(new Error('network down'))

    const wrapper = await mountWithFetch(fetchMock)

    await wrapper.find('[data-testid="load-session-status"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="session-status-error"]').text()).toContain('Failed to load session status')
  })

  it('shows tile status pane when markers are present', async () => {
    const markers = [buildMarker('marker-1', { tile_id: 'tile-abc', tile_x: 10, tile_y: 20 })]
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, markers))
    const wrapper = await mountWithFetch(fetchMock)

    expect(wrapper.find('[data-testid="tile-status-pane"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="tile-item-marker-1"]').exists()).toBe(true)
  })

  it('does not show tile status pane when no markers exist', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, []))
    const wrapper = await mountWithFetch(fetchMock)

    expect(wrapper.find('[data-testid="tile-status-pane"]').exists()).toBe(false)
  })
})