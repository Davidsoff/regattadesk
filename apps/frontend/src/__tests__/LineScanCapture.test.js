import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import LineScanCapture from '../components/operator/LineScanCapture.vue'

// Mock the operator theme composable to avoid singleton state issues across tests
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
            marker_approved: 'This marker is approved and cannot be edited',
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
            conflict_detected: 'A conflict was detected. See the conflict panel below.'
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

describe('LineScanCapture component - Marker CRUD', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.stubGlobal('__REGATTADESK_AUTH__', {
      operatorToken: 'token-97'
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders empty state when no markers exist', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, []))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    expect(wrapper.find('[data-testid="capture-markers-list"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('No markers yet')
  })

  it('loads and displays markers on mount', async () => {
    const markers = [
      buildMarker('marker-1'),
      buildMarker('marker-2', { frame_offset: 2000 })
    ]
    
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
      .mockResolvedValueOnce(jsonResponse(200, [])) // initial load
      .mockResolvedValueOnce(jsonResponse(201, newMarker)) // create

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
    const markers = [buildMarker('marker-1')]
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, markers)) // initial load
      .mockResolvedValueOnce(jsonResponse(204, null)) // delete

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
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.stubGlobal('__REGATTADESK_AUTH__', {
      operatorToken: 'token-97'
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

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
    
    // First two should be unlinked
    expect(markerItems[0].attributes('data-testid')).toContain('marker-unlinked')
    expect(markerItems[1].attributes('data-testid')).toContain('marker-unlinked')
    // Last should be linked
    expect(markerItems[2].attributes('data-testid')).toContain('marker-linked')
  })

  it('links a marker to an entry when link button is clicked', async () => {
    const unlinkedMarker = buildMarker('marker-1', { is_linked: false })
    const linkedMarker = buildMarker('marker-1', { is_linked: true, entry_id: 'entry-99' })
    
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, [unlinkedMarker])) // initial load
      .mockResolvedValueOnce(jsonResponse(200, linkedMarker)) // link

    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    await wrapper.find('[data-testid="link-marker-marker-1"]').trigger('click')
    await wrapper.find('[data-testid="link-entry-input"]').setValue('entry-99')
    await wrapper.find('[data-testid="link-entry-submit"]').trigger('click')
    await flushPromises()

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
      .mockResolvedValueOnce(jsonResponse(200, [linkedMarker])) // initial load
      .mockResolvedValueOnce(jsonResponse(200, unlinkedMarker)) // unlink

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

    const unlinkButton = wrapper.find('[data-testid="unlink-marker-marker-approved"]')
    expect(unlinkButton.exists()).toBe(false)
  })
})

describe('LineScanCapture component - Approved Marker Lock States', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.stubGlobal('__REGATTADESK_AUTH__', {
      operatorToken: 'token-97'
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

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

    const updateButton = wrapper.find('[data-testid="update-marker-marker-approved"]')
    expect(updateButton.exists()).toBe(false)
  })

  it('handles 409 conflict when attempting to modify approved marker', async () => {
    const approvedMarker = buildMarker('marker-approved', {
      is_linked: true,
      is_approved: true,
      entry_id: 'entry-1'
    })
    
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, [approvedMarker])) // initial load
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

    // Try to delete via API call (shouldn't be possible in UI, but testing error handling)
    await wrapper.vm.deleteMarker('marker-approved')
    await flushPromises()

    expect(wrapper.find('[data-testid="error-message"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Cannot modify approved marker')
  })
})

describe('LineScanCapture component - Marker State Transitions', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.stubGlobal('__REGATTADESK_AUTH__', {
      operatorToken: 'token-97'
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('updates marker frame offset', async () => {
    const marker = buildMarker('marker-1', { frame_offset: 1000 })
    const updatedMarker = buildMarker('marker-1', { frame_offset: 1500 })
    
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, [marker])) // initial load
      .mockResolvedValueOnce(jsonResponse(200, updatedMarker)) // update

    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    await wrapper.find('[data-testid="edit-marker-marker-1"]').trigger('click')
    await wrapper.find('[data-testid="marker-frame-input"]').setValue('1500')
    await wrapper.find('[data-testid="update-marker-marker-1"]').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    const request = getRequest(fetchMock, 1)
    expect(request.url).toContain('/api/v1/regattas/regatta-97/operator/markers/marker-1')
    expect(request.method).toBe('PATCH')
    await expect(request.json()).resolves.toEqual({ frame_offset: 1500 })
  })

  it('supports undo of marker position changes', async () => {
    const marker = buildMarker('marker-1', { frame_offset: 1000 })
    const updatedMarker = buildMarker('marker-1', { frame_offset: 1500 })
    const undoneMarker = buildMarker('marker-1', { frame_offset: 1000 })
    
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, [marker])) // initial load
      .mockResolvedValueOnce(jsonResponse(200, updatedMarker)) // update
      .mockResolvedValueOnce(jsonResponse(200, undoneMarker)) // undo

    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    // Make a change
    await wrapper.find('[data-testid="edit-marker-marker-1"]').trigger('click')
    await wrapper.find('[data-testid="marker-frame-input"]').setValue('1500')
    await wrapper.find('[data-testid="update-marker-marker-1"]').trigger('click')
    await flushPromises()

    // Undo the change
    await wrapper.find('[data-testid="undo-last-change"]').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledTimes(3)
    const request = getRequest(fetchMock, 2)
    expect(request.url).toContain('/api/v1/regattas/regatta-97/operator/markers/marker-1')
    expect(request.method).toBe('PATCH')
    await expect(request.json()).resolves.toEqual({ frame_offset: 1000 })
  })
})

describe('LineScanCapture component - Conflict Resolution UI', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.stubGlobal('__REGATTADESK_AUTH__', {
      operatorToken: 'token-97'
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows conflict panel when a data conflict is detected on link', async () => {
    const unlinkedMarker = buildMarker('marker-1', { is_linked: false })

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, [unlinkedMarker])) // initial load
      .mockResolvedValueOnce(
        jsonResponse(409, { error: { code: 'DATA_CONFLICT', message: 'Conflict detected' } })
      ) // link conflict

    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    await wrapper.find('[data-testid="link-marker-marker-1"]').trigger('click')
    await wrapper.find('[data-testid="link-entry-input"]').setValue('entry-99')
    await wrapper.find('[data-testid="link-entry-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="conflict-resolution-pane"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid^="conflict-keep-mine-"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid^="conflict-use-server-"]').exists()).toBe(true)
  })

  it('resolving conflict with keep-mine removes it from the panel', async () => {
    const unlinkedMarker = buildMarker('marker-1', { is_linked: false })

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, [unlinkedMarker])) // initial load
      .mockResolvedValueOnce(
        jsonResponse(409, { error: { code: 'DATA_CONFLICT', message: 'Conflict detected' } })
      ) // link conflict

    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    await wrapper.find('[data-testid="link-marker-marker-1"]').trigger('click')
    await wrapper.find('[data-testid="link-entry-input"]').setValue('entry-99')
    await wrapper.find('[data-testid="link-entry-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="conflict-resolution-pane"]').exists()).toBe(true)

    await wrapper.find('[data-testid^="conflict-keep-mine-"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="conflict-resolution-pane"]').exists()).toBe(false)
  })

  it('resolving conflict with use-server removes conflict and reloads markers', async () => {
    const unlinkedMarker = buildMarker('marker-1', { is_linked: false })
    const reloadedMarkers = [buildMarker('marker-1', { is_linked: true, entry_id: 'entry-server' })]

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, [unlinkedMarker])) // initial load
      .mockResolvedValueOnce(
        jsonResponse(409, { error: { code: 'DATA_CONFLICT', message: 'Conflict detected' } })
      ) // link conflict
      .mockResolvedValueOnce(jsonResponse(200, reloadedMarkers)) // reload after use-server

    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    await wrapper.find('[data-testid="link-marker-marker-1"]').trigger('click')
    await wrapper.find('[data-testid="link-entry-input"]').setValue('entry-99')
    await wrapper.find('[data-testid="link-entry-submit"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-testid^="conflict-use-server-"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="conflict-resolution-pane"]').exists()).toBe(false)
    // Markers should have been reloaded (3 total fetch calls)
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it('does not show conflict panel for MARKER_APPROVED 409 responses', async () => {
    const approvedMarker = buildMarker('marker-approved', {
      is_linked: false,
      is_approved: false
    })

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, [approvedMarker])) // initial load
      .mockResolvedValueOnce(
        jsonResponse(409, { error: { code: 'MARKER_APPROVED', message: 'Cannot modify approved marker' } })
      ) // link blocked by approval

    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    await wrapper.find('[data-testid="link-marker-marker-approved"]').trigger('click')
    await wrapper.find('[data-testid="link-entry-input"]').setValue('entry-99')
    await wrapper.find('[data-testid="link-entry-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="conflict-resolution-pane"]').exists()).toBe(false)
  })
})

describe('LineScanCapture component - High-Contrast Controls', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.stubGlobal('__REGATTADESK_AUTH__', {
      operatorToken: 'token-97'
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows high-contrast toggle button', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, []))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    expect(wrapper.find('[data-testid="toggle-high-contrast"]').exists()).toBe(true)
  })

  it('toggle button label reflects high-contrast mode (on)', async () => {
    const { ref } = await import('vue')
    const { useOperatorTheme } = await import('../composables/useOperatorTheme')
    vi.mocked(useOperatorTheme).mockReturnValue({
      isHighContrast: ref(true),
      toggleContrast: vi.fn()
    })

    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, []))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    expect(wrapper.find('[data-testid="toggle-high-contrast"]').text()).toContain('High Contrast: On')
  })

  it('toggle button label reflects standard mode (off)', async () => {
    const { ref } = await import('vue')
    const { useOperatorTheme } = await import('../composables/useOperatorTheme')
    vi.mocked(useOperatorTheme).mockReturnValue({
      isHighContrast: ref(false),
      toggleContrast: vi.fn()
    })

    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, []))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    expect(wrapper.find('[data-testid="toggle-high-contrast"]').text()).toContain('High Contrast: Off')
  })

  it('clicking toggle button calls toggleContrast', async () => {
    const { ref } = await import('vue')
    const mockToggle = vi.fn()
    const { useOperatorTheme } = await import('../composables/useOperatorTheme')
    vi.mocked(useOperatorTheme).mockReturnValue({
      isHighContrast: ref(true),
      toggleContrast: mockToggle
    })

    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, []))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    await wrapper.find('[data-testid="toggle-high-contrast"]').trigger('click')

    expect(mockToggle).toHaveBeenCalledTimes(1)
  })
})

describe('LineScanCapture component - Session Status and Tile Pane', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.stubGlobal('__REGATTADESK_AUTH__', {
      operatorToken: 'token-97'
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows session status refresh button', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, []))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    expect(wrapper.find('[data-testid="load-session-status"]').exists()).toBe(true)
  })

  it('loads session status when refresh button is clicked and shows sync state', async () => {
    const captureSessionData = {
      id: 'session-1',
      regatta_id: 'regatta-97',
      station: 'finish-line',
      state: 'open',
      is_synced: true
    }

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, [])) // initial markers load
      .mockResolvedValueOnce(jsonResponse(200, captureSessionData)) // session load

    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    await wrapper.find('[data-testid="load-session-status"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="session-status-detail"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="session-sync-indicator"]').text()).toContain('Synced')
  })

  it('shows tile status pane when markers are present', async () => {
    const markers = [buildMarker('marker-1', { tile_id: 'tile-abc', tile_x: 10, tile_y: 20 })]
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, markers))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    expect(wrapper.find('[data-testid="tile-status-pane"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="tile-item-marker-1"]').exists()).toBe(true)
  })

  it('does not show tile status pane when no markers exist', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, []))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScanCapture()
    await flushPromises()

    expect(wrapper.find('[data-testid="tile-status-pane"]').exists()).toBe(false)
  })
})
