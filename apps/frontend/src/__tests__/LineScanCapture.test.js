import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import LineScanCapture from '../components/operator/LineScanCapture.vue'
import en from '../i18n/locales/en.json'

const { syncQueueMock, toggleContrastMock, queueHarness } = vi.hoisted(() => ({
  syncQueueMock: vi.fn(),
  toggleContrastMock: vi.fn(),
  queueHarness: {
    items: [],
    nextId: 1,
    queueSizeRef: null
  }
}))

function resetQueueHarness() {
  queueHarness.items = []
  queueHarness.nextId = 1
  if (queueHarness.queueSizeRef) {
    queueHarness.queueSizeRef.value = 0
  }
}

function updateQueueSize() {
  if (queueHarness.queueSizeRef) {
    queueHarness.queueSizeRef.value = queueHarness.items.length
  }
}

vi.mock('../composables/useOperatorTheme', async () => {
  const { ref } = await import('vue')
  return {
    useOperatorTheme: vi.fn(() => ({
      isHighContrast: ref(true),
      toggleContrast: toggleContrastMock
    }))
  }
})

vi.mock('../composables/useOfflineQueue', async () => {
  const { ref } = await import('vue')
  queueHarness.queueSizeRef = ref(0)

  return {
    useOfflineQueue: vi.fn(() => ({
      queueSize: queueHarness.queueSizeRef,
      enqueue: vi.fn(async (operation) => {
        const id = queueHarness.nextId
        queueHarness.nextId += 1
        queueHarness.items.push({ ...operation, id })
        updateQueueSize()
        return id
      }),
      dequeue: vi.fn(async (queueId) => {
        queueHarness.items = queueHarness.items.filter((item) => item.id !== queueId)
        updateQueueSize()
      }),
      getQueue: vi.fn(async () => [...queueHarness.items]),
      updateQueueItem: vi.fn(async (queueId, updates) => {
        const index = queueHarness.items.findIndex((item) => item.id === queueId)
        if (index === -1) {
          throw new Error('Queue item not found')
        }
        queueHarness.items[index] = {
          ...queueHarness.items[index],
          ...updates
        }
        updateQueueSize()
        return queueHarness.items[index]
      })
    }))
  }
})

vi.mock('../composables/useOfflineSync', () => ({
  useOfflineSync: vi.fn(() => ({
    syncQueue: syncQueueMock
  }))
}))

function createTestI18n() {
  return createI18n({
    legacy: false,
    locale: 'en',
    messages: { en }
  })
}

function jsonResponse(status, body) {
  return new Response(body === null ? '' : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

function buildMarker(id = 'marker-1', overrides = {}) {
  return {
    id,
    capture_session_id: 'session-1',
    entry_id: null,
    frame_offset: 1000,
    timestamp_ms: 1767261600000,
    is_linked: false,
    is_approved: false,
    tile_id: 'tile-ready',
    tile_x: 0,
    tile_y: 0,
    ...overrides
  }
}

function buildCaptureSession(id = 'session-1', overrides = {}) {
  return {
    capture_session_id: id,
    regatta_id: 'regatta-97',
    block_id: 'block-1',
    station: 'line-scan',
    device_id: 'device-1',
    session_type: 'finish',
    state: 'open',
    server_time_at_start: '2026-01-01T10:00:00Z',
    device_monotonic_offset_ms: 0,
    fps: 25,
    is_synced: true,
    drift_exceeded_threshold: false,
    unsynced_reason: '',
    capabilities: {
      persisted_evidence_workspace_supported: true,
      live_preview_supported: false,
      device_control_mode: 'read_only'
    },
    live_status: {
      preview_state: 'inactive',
      drift_state: 'synced',
      elapsed_capture_ms: 12000,
      status_observed_at: '2026-01-01T10:00:12Z'
    },
    ...overrides
  }
}

function buildWorkspace(overrides = {}) {
  return {
    regatta_id: 'regatta-97',
    capture_session_id: 'session-1',
    capture_session: buildCaptureSession(),
    evidence: {
      manifest_id: 'manifest-1',
      capture_session_id: 'session-1',
      availability_state: 'ready',
      availability_reason: null,
      upload_state: 'syncing',
      tile_size_px: 512,
      primary_format: 'webp_lossless',
      fallback_format: 'png',
      x_origin_timestamp_ms: Date.parse('2026-01-01T10:00:00Z'),
      ms_per_pixel: 4,
      span: {
        start_timestamp_ms: Date.parse('2026-01-01T10:00:00Z'),
        end_timestamp_ms: Date.parse('2026-01-01T10:00:04.096Z'),
        min_tile_x: 0,
        max_tile_x: 1,
        min_tile_y: 0,
        max_tile_y: 0,
        tile_columns: 2,
        tile_rows: 1,
        pixel_width: 1024,
        pixel_height: 512
      },
      tiles: [
        {
          tile_id: 'tile-ready',
          tile_x: 0,
          tile_y: 0,
          content_type: 'image/webp',
          byte_size: 256,
          upload_state: 'ready',
          upload_attempts: 1,
          tile_href: 'https://example.test/tile-ready.webp'
        },
        {
          tile_id: 'tile-pending',
          tile_x: 1,
          tile_y: 0,
          content_type: 'image/webp',
          upload_state: 'pending',
          upload_attempts: 0,
          tile_href: 'https://example.test/tile-pending.webp'
        }
      ]
    },
    markers: [buildMarker('marker-linked', { frame_offset: 20, timestamp_ms: Date.parse('2026-01-01T10:00:00.800Z'), is_linked: true, entry_id: 'entry-2', tile_x: 1 })],
    ...overrides
  }
}

async function mountLineScanCapture(fetchMock, options = {}) {
  vi.stubGlobal('fetch', fetchMock)

  const wrapper = mount(LineScanCapture, {
    props: {
      captureSessionId: 'session-1',
      regattaId: 'regatta-97',
      ...options.props
    },
    global: {
      plugins: [createTestI18n()]
    }
  })

  await flushPromises()
  return wrapper
}

function setStageRect(wrapper, rect = { left: 0, top: 0, width: 200, height: 100 }) {
  const stage = wrapper.find('[data-testid="evidence-stage"]').element
  stage.getBoundingClientRect = () => ({
    ...rect,
    right: rect.left + rect.width,
    bottom: rect.top + rect.height,
    x: rect.left,
    y: rect.top,
    toJSON: () => rect
  })
}

function dispatchPointerEvent(type, coords) {
  const event = new Event(type, { bubbles: true })
  Object.assign(event, coords)
  window.dispatchEvent(event)
}

function setNavigatorOnline(value) {
  Object.defineProperty(globalThis.navigator, 'onLine', {
    configurable: true,
    get: () => value
  })
}

beforeEach(() => {
  vi.restoreAllMocks()
  vi.stubGlobal('__REGATTADESK_AUTH__', {
    operatorAuth: 'token-97'
  })
  resetQueueHarness()
  syncQueueMock.mockReset()
  toggleContrastMock.mockReset()
  setNavigatorOnline(true)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('LineScanCapture operator evidence workspace', () => {
  it('renders the evidence-first stage with degraded status and overlay markers', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(
      jsonResponse(
        200,
        buildWorkspace({
          evidence: {
            ...buildWorkspace().evidence,
            availability_state: 'degraded',
            availability_reason: 'tile_upload_pending'
          },
          markers: [
            buildMarker('marker-a', {
              frame_offset: 10,
              timestamp_ms: Date.parse('2026-01-01T10:00:00.400Z'),
              tile_x: 0
            }),
            buildMarker('marker-b', {
              frame_offset: 30,
              timestamp_ms: Date.parse('2026-01-01T10:00:01.200Z'),
              is_linked: true,
              tile_x: 1
            })
          ]
        })
      )
    )

    const wrapper = await mountLineScanCapture(fetchMock)

    expect(wrapper.find('[data-testid="evidence-stage"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="evidence-degraded-banner"]').text()).toContain('still uploading')
    expect(wrapper.find('[data-testid="evidence-upload-state-badge"]').text()).toContain('Syncing')
    expect(wrapper.find('[data-testid="evidence-stage-marker-marker-a"]').attributes('style')).toContain('left:')
    expect(wrapper.find('[data-testid="evidence-cursor"]').exists()).toBe(true)
  })

  it('surfaces partial upload failures with retry guidance and tile error metadata', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(
      jsonResponse(
        200,
        buildWorkspace({
          evidence: {
            ...buildWorkspace().evidence,
            upload_state: 'partial_failure',
            availability_state: 'degraded',
            availability_reason: 'tile_upload_failed',
            tiles: [
              {
                tile_id: 'tile-ready',
                tile_x: 0,
                tile_y: 0,
                content_type: 'image/webp',
                byte_size: 256,
                upload_state: 'ready',
                upload_attempts: 1,
                tile_href: 'https://example.test/tile-ready.webp'
              },
              {
                tile_id: 'tile-failed',
                tile_x: 1,
                tile_y: 0,
                content_type: 'image/webp',
                upload_state: 'failed',
                upload_attempts: 2,
                last_upload_error: 'minio timeout'
              }
            ]
          },
          markers: []
        })
      )
    )

    const wrapper = await mountLineScanCapture(fetchMock)

    expect(wrapper.find('[data-testid="evidence-upload-state-badge"]').text()).toContain('Partial failure')
    expect(wrapper.find('[data-testid="evidence-upload-retry-banner"]').text()).toContain('Retry the same tile upload')
    expect(wrapper.text()).toContain('2 upload attempt(s)')
    expect(wrapper.text()).toContain('minio timeout')
  })

  it('shows an explicit unavailable state when persisted evidence is missing', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(
      jsonResponse(
        200,
        buildWorkspace({
          evidence: {
            manifest_id: null,
            capture_session_id: 'session-1',
            availability_state: 'unavailable',
            availability_reason: 'manifest_missing',
            tiles: []
          },
          markers: []
        })
      )
    )

    const wrapper = await mountLineScanCapture(fetchMock)

    expect(wrapper.find('[data-testid="evidence-unavailable-state"]').text()).toContain('Evidence unavailable')
    expect(wrapper.text()).toContain('No persisted evidence manifest')
    expect(wrapper.find('[data-testid="create-marker-button"]').attributes('disabled')).toBeDefined()
  })

  it('creates a marker from the persisted review cursor and clears the queue after sync', async () => {
    const createdMarker = buildMarker('marker-created', {
      frame_offset: 0,
      timestamp_ms: Date.parse('2026-01-01T10:00:00Z'),
      tile_id: 'tile-ready',
      tile_x: 0,
      tile_y: 0
    })
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, buildWorkspace({ markers: [] })))

    syncQueueMock.mockResolvedValueOnce({
      synced: [{ id: 1, data: createdMarker }],
      failed: [],
      conflicts: [],
      discarded: []
    })

    const wrapper = await mountLineScanCapture(fetchMock)

    await wrapper.find('[data-testid="create-marker-button"]').trigger('click')
    await flushPromises()

    expect(syncQueueMock).toHaveBeenCalledTimes(1)
    const [operations] = syncQueueMock.mock.calls[0]
    expect(operations[0].data).toEqual({
      capture_session_id: 'session-1',
      frame_offset: 0,
      timestamp_ms: Date.parse('2026-01-01T10:00:00Z'),
      tile_id: 'tile-ready',
      tile_x: 0,
      tile_y: 0
    })
    expect(wrapper.find('[data-testid="marker-item-marker-created"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="queue-empty-state"]').exists()).toBe(true)
  })

  it('places a new marker directly from a stage click', async () => {
    const createdMarker = buildMarker('marker-clicked', {
      frame_offset: 51,
      timestamp_ms: Date.parse('2026-01-01T10:00:02.048Z'),
      tile_id: 'tile-pending',
      tile_x: 1,
      tile_y: 0
    })
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, buildWorkspace({ markers: [] })))

    syncQueueMock.mockResolvedValueOnce({
      synced: [{ id: 1, data: createdMarker }],
      failed: [],
      conflicts: [],
      discarded: []
    })

    const wrapper = await mountLineScanCapture(fetchMock)
    setStageRect(wrapper)

    await wrapper.find('[data-testid="evidence-stage"]').trigger('pointerdown', {
      clientX: 120,
      clientY: 20
    })
    await wrapper.find('[data-testid="evidence-stage"]').trigger('click', {
      clientX: 120,
      clientY: 20
    })
    await flushPromises()

    const [operations] = syncQueueMock.mock.calls[0]
    expect(operations[0].type).toBe('CREATE_MARKER')
    expect(operations[0].data.frame_offset).toBe(61)
    expect(operations[0].data.tile_id).toBe('tile-pending')
    expect(wrapper.find('[data-testid="marker-item-marker-clicked"]').exists()).toBe(true)
  })

  it('supports keyboard cursor placement from the evidence stage', async () => {
    const createdMarker = buildMarker('marker-keyboard', {
      frame_offset: 5,
      timestamp_ms: Date.parse('2026-01-01T10:00:00.200Z'),
      tile_id: 'tile-ready',
      tile_x: 0,
      tile_y: 0
    })
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, buildWorkspace({ markers: [] })))

    syncQueueMock.mockResolvedValueOnce({
      synced: [{ id: 1, data: createdMarker }],
      failed: [],
      conflicts: [],
      discarded: []
    })

    const wrapper = await mountLineScanCapture(fetchMock)

    await wrapper.find('[data-testid="evidence-stage"]').trigger('keydown', { key: 'ArrowRight', shiftKey: true })
    await wrapper.find('[data-testid="evidence-stage"]').trigger('keydown', { key: 'Enter' })
    await flushPromises()

    const [operations] = syncQueueMock.mock.calls[0]
    expect(operations[0].data.frame_offset).toBe(5)
    expect(wrapper.find('[data-testid="marker-item-marker-keyboard"]').exists()).toBe(true)
  })

  it('drags an editable marker with immediate optimistic feedback before queue sync', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, buildWorkspace({
      markers: [buildMarker('marker-drag', { frame_offset: 20, timestamp_ms: Date.parse('2026-01-01T10:00:00.800Z'), tile_x: 0 })]
    })))

    syncQueueMock.mockResolvedValueOnce({
      synced: [
        {
          id: 1,
          data: buildMarker('marker-drag', {
            frame_offset: 51,
            timestamp_ms: Date.parse('2026-01-01T10:00:02.048Z'),
            tile_id: 'tile-pending',
            tile_x: 1,
            tile_y: 0
          })
        }
      ],
      failed: [],
      conflicts: [],
      discarded: []
    })

    const wrapper = await mountLineScanCapture(fetchMock)
    setStageRect(wrapper)

    await wrapper.find('[data-testid="evidence-stage-marker-marker-drag"]').trigger('pointerdown', {
      pointerId: 1,
      clientX: 40,
      clientY: 20
    })
    dispatchPointerEvent('pointermove', { clientX: 120, clientY: 20 })
    await flushPromises()

    expect(wrapper.text()).toContain('Selected frame: 61')

    dispatchPointerEvent('pointerup', { clientX: 120, clientY: 20 })
    await flushPromises()

    const [operations] = syncQueueMock.mock.calls[0]
    expect(operations[0].type).toBe('UPDATE_MARKER')
    expect(operations[0].data.frame_offset).toBe(61)
    expect(operations[0].data.tile_id).toBe('tile-pending')
  })

  it('nudges the selected editable marker from the keyboard', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, buildWorkspace({
      markers: [buildMarker('marker-nudge', { frame_offset: 20, timestamp_ms: Date.parse('2026-01-01T10:00:00.800Z') })]
    })))

    syncQueueMock.mockResolvedValueOnce({
      synced: [{ id: 1, data: buildMarker('marker-nudge', { frame_offset: 25, timestamp_ms: Date.parse('2026-01-01T10:00:01.000Z') }) }],
      failed: [],
      conflicts: [],
      discarded: []
    })

    const wrapper = await mountLineScanCapture(fetchMock)

    await wrapper.find('[data-testid="evidence-stage"]').trigger('keydown', {
      key: 'ArrowRight',
      altKey: true,
      shiftKey: true
    })
    await flushPromises()

    const [operations] = syncQueueMock.mock.calls[0]
    expect(operations[0].type).toBe('UPDATE_MARKER')
    expect(operations[0].data.frame_offset).toBe(25)
  })

  it('does not allow approved markers to be dragged', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, buildWorkspace({
      markers: [buildMarker('marker-locked', { is_approved: true, frame_offset: 20, timestamp_ms: Date.parse('2026-01-01T10:00:00.800Z') })]
    })))

    const wrapper = await mountLineScanCapture(fetchMock)
    setStageRect(wrapper)

    await wrapper.find('[data-testid="evidence-stage-marker-marker-locked"]').trigger('pointerdown', {
      pointerId: 1,
      clientX: 40,
      clientY: 20
    })
    dispatchPointerEvent('pointermove', { clientX: 100, clientY: 20 })
    dispatchPointerEvent('pointerup', { clientX: 100, clientY: 20 })
    await flushPromises()

    expect(syncQueueMock).not.toHaveBeenCalled()
  })

  it('queues link mutations while offline instead of attempting immediate sync', async () => {
    setNavigatorOnline(false)
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, buildWorkspace({
      markers: [buildMarker('marker-1')]
    })))

    const wrapper = await mountLineScanCapture(fetchMock)

    await wrapper.find('[data-testid="link-marker-marker-1"]').trigger('click')
    await wrapper.find('[data-testid="link-entry-input"]').setValue('entry-99')
    await wrapper.find('[data-testid="link-entry-submit"]').trigger('click')
    await flushPromises()

    expect(syncQueueMock).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="queue-item-1"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Link marker')
    expect(wrapper.text()).toContain('Queued')
  })

  it('surfaces queued conflicts and lets the operator discard the blocked mutation', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, buildWorkspace({
        markers: [buildMarker('marker-1', { is_linked: true, entry_id: 'entry-1' })]
      })))
      .mockResolvedValueOnce(jsonResponse(200, buildWorkspace({
        markers: [buildMarker('marker-1', { is_linked: true, entry_id: 'entry-1' })]
      })))

    syncQueueMock.mockResolvedValueOnce({
      synced: [],
      failed: [],
      conflicts: [
        {
          conflictCode: 'CONFLICT',
          conflictMessage: 'Marker conflict',
          limitation: 'backend-no-force-override',
          policy: 'last-write-wins'
        }
      ],
      discarded: []
    })

    const wrapper = await mountLineScanCapture(fetchMock)

    await wrapper.find('[data-testid="unlink-marker-marker-1"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="conflict-resolution-pane"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('last-write-wins')

    await wrapper.find('[data-testid="conflict-discard-1"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="conflict-resolution-pane"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="queue-empty-state"]').exists()).toBe(true)
  })

  it('approves a linked marker through the queued marker approval flow', async () => {
    const approvedMarker = buildMarker('marker-1', {
      is_linked: true,
      is_approved: true,
      entry_id: 'entry-1'
    })
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, buildWorkspace({
      markers: [buildMarker('marker-1', { is_linked: true, entry_id: 'entry-1' })]
    })))

    syncQueueMock.mockResolvedValueOnce({
      synced: [{ id: 1, data: approvedMarker }],
      failed: [],
      conflicts: [],
      discarded: []
    })

    const wrapper = await mountLineScanCapture(fetchMock)

    await wrapper.find('[data-testid="approve-marker-marker-1"]').trigger('click')
    await flushPromises()

    expect(syncQueueMock).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[data-testid="marker-locked-marker-1"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('marker approval only')
  })

  it('keeps the high-contrast control wired to the operator theme composable', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(200, buildWorkspace()))

    const wrapper = await mountLineScanCapture(fetchMock)

    await wrapper.find('[data-testid="toggle-high-contrast"]').trigger('click')

    expect(toggleContrastMock).toHaveBeenCalledTimes(1)
  })
})
