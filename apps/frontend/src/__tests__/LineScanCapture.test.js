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
    tile_id: 'tile-1',
    tile_x: 5,
    tile_y: 3,
    ...overrides
  }
}

function buildCaptureSession(id = 'session-1', overrides = {}) {
  return {
    capture_session_id: id,
    regatta_id: 'regatta-97',
    block_id: 'block-1',
    station: 'finish-line',
    device_id: 'device-1',
    session_type: 'finish',
    state: 'open',
    server_time_at_start: '2026-01-01T10:00:00Z',
    device_monotonic_offset_ms: 0,
    fps: 25,
    is_synced: true,
    drift_exceeded_threshold: false,
    unsynced_reason: '',
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
  it('shows the explicit development evidence source when no live frame is attached', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, buildCaptureSession()))
      .mockResolvedValueOnce(jsonResponse(200, { data: [] }))

    const wrapper = await mountLineScanCapture(fetchMock)

    expect(wrapper.find('[data-testid="dev-evidence-banner"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="evidence-source-mode"]').text()).toContain('Development evidence source')
  })

  it('creates a marker from explicit evidence metadata and clears the queue after sync', async () => {
    const createdMarker = buildMarker('marker-created', {
      frame_offset: 0,
      timestamp_ms: Date.parse('2026-01-01T10:00:00Z'),
      tile_id: null,
      tile_x: null,
      tile_y: null
    })
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, buildCaptureSession()))
      .mockResolvedValueOnce(jsonResponse(200, { data: [] }))

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
      tile_id: null,
      tile_x: null,
      tile_y: null
    })
    expect(wrapper.find('[data-testid="marker-item-marker-created"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="queue-empty-state"]').exists()).toBe(true)
  })

  it('recenters review state when an unlinked marker is selected from the overview', async () => {
    const markers = [
      buildMarker('marker-linked', { frame_offset: 2000, is_linked: true, entry_id: 'entry-2' }),
      buildMarker('marker-unlinked', { frame_offset: 1400, is_linked: false })
    ]
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, buildCaptureSession()))
      .mockResolvedValueOnce(jsonResponse(200, { data: markers }))

    const wrapper = await mountLineScanCapture(fetchMock)

    await wrapper.find('[data-testid="overview-marker-marker-unlinked"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Selected marker: marker-unlinked')
    expect(wrapper.find('[data-testid="overview-center-slider"]').element.value).toBe('1400')
  })

  it('queues link mutations while offline instead of attempting immediate sync', async () => {
    setNavigatorOnline(false)
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, buildCaptureSession()))
      .mockResolvedValueOnce(jsonResponse(200, { data: [buildMarker('marker-1')] }))

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
      .mockResolvedValueOnce(jsonResponse(200, buildCaptureSession()))
      .mockResolvedValueOnce(jsonResponse(200, { data: [buildMarker('marker-1', { is_linked: true, entry_id: 'entry-1' })] }))
      .mockResolvedValueOnce(jsonResponse(200, { data: [buildMarker('marker-1', { is_linked: true, entry_id: 'entry-1' })] }))

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
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, buildCaptureSession()))
      .mockResolvedValueOnce(jsonResponse(200, { data: [buildMarker('marker-1', { is_linked: true, entry_id: 'entry-1' })] }))

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
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, buildCaptureSession()))
      .mockResolvedValueOnce(jsonResponse(200, { data: [] }))

    const wrapper = await mountLineScanCapture(fetchMock)

    await wrapper.find('[data-testid="toggle-high-contrast"]').trigger('click')

    expect(toggleContrastMock).toHaveBeenCalledTimes(1)
  })
})