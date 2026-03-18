import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import LineScan from '../views/operator/LineScan.vue'
import en from '../i18n/locales/en.json'

const {
  requestStationHandoff,
  revealStationHandoffPin,
  getStationHandoffStatus,
  cancelStationHandoff,
  completeStationHandoff,
  queueState
} = vi.hoisted(() => ({
  requestStationHandoff: vi.fn(),
  revealStationHandoffPin: vi.fn(),
  getStationHandoffStatus: vi.fn(),
  cancelStationHandoff: vi.fn(),
  completeStationHandoff: vi.fn(),
  queueState: { value: 0 }
}))

vi.mock('../api', () => ({
  ApiError: class ApiError extends Error {
    constructor(error, status) {
      super(error.message)
      this.code = error.code
      this.status = status
    }
  },
  createApiClient: vi.fn(() => ({})),
  createOperatorApi: vi.fn(() => ({
    requestStationHandoff,
    revealStationHandoffPin,
    getStationHandoffStatus,
    cancelStationHandoff,
    completeStationHandoff
  }))
}))

vi.mock('../composables/useOfflineQueue', async () => {
  const { ref } = await import('vue')
  const state = ref(0)
  Object.defineProperty(queueState, 'value', {
    get: () => state.value,
    set: (value) => {
      state.value = value
    }
  })

  return {
    useOfflineQueue: vi.fn(() => ({
      queueSize: state
    }))
  }
})

const CaptureStub = {
  template: `
    <div>
      <div data-testid="line-scan-capture-stub">Capture workspace</div>
      <button
        type="button"
        data-testid="emit-queue-summary"
        @click="$emit('queue-state-change', { queuedCount: 2, conflictCount: 1, failedCount: 1 })"
      >
        emit
      </button>
    </div>
  `
}

function createTestI18n() {
  return createI18n({
    legacy: false,
    locale: 'en',
    messages: { en }
  })
}

function buildHandoff(id = 'handoff-97', overrides = {}) {
  return {
    id,
    regatta_id: 'regatta-97',
    station: 'finish-line',
    requesting_device_id: 'operator-device',
    active_device_id: 'operator-device',
    status: 'pending',
    previous_device_mode: 'active',
    new_device_mode: 'read_only',
    expires_at: '2026-01-01T00:00:00.000Z',
    ...overrides
  }
}

async function mountLineScan(path = '/operator/regattas/regatta-97/sessions/session-97/line-scan') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/operator/regattas/:regattaId/sessions/:captureSessionId/line-scan',
        name: 'operator-session-line-scan',
        component: LineScan
      },
      {
        path: '/operator/regattas/:regattaId/line-scan',
        name: 'operator-line-scan-legacy-test',
        component: LineScan
      }
    ]
  })

  await router.push(path)
  await router.isReady()

  return mount(LineScan, {
    global: {
      plugins: [router, createTestI18n()],
      stubs: {
        LineScanCapture: CaptureStub
      }
    }
  })
}

describe('Operator line-scan handoff UX', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    queueState.value = 0
    vi.stubGlobal('__REGATTADESK_AUTH__', {
      operatorAuth: 'token-97',
      operatorStation: 'finish-line'
    })
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue('operator-device')
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('sends operator station/device metadata and uses new_device_mode to set local access', async () => {
    requestStationHandoff.mockResolvedValueOnce(buildHandoff('handoff-1'))
    completeStationHandoff.mockResolvedValueOnce(
      buildHandoff('handoff-1', {
        status: 'completed',
        previous_device_mode: 'active',
        new_device_mode: 'read_only'
      })
    )

    const wrapper = await mountLineScan()
    await wrapper.find('[data-testid="handoff-request-start"]').trigger('click')
    await flushPromises()

    expect(requestStationHandoff).toHaveBeenCalledWith('regatta-97', {
      station: 'finish-line',
      requesting_device_id: 'operator-device'
    })

    await wrapper.find('[data-testid="handoff-pin-input"]').setValue('123456')
    await wrapper.find('[data-testid="handoff-complete-submit"]').trigger('click')
    await flushPromises()

    expect(completeStationHandoff).toHaveBeenCalledWith('regatta-97', 'handoff-1', {
      pin: '123456',
      completing_device_id: 'operator-device'
    })
    expect(wrapper.find('[data-testid="operator-readonly-banner"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="line-scan-capture"]').attributes('disabled')).toBeDefined()
  })

  it('renders INVALID_PIN feedback from API 400 response', async () => {
    const { ApiError } = await import('../api')
    requestStationHandoff.mockResolvedValueOnce(buildHandoff('handoff-invalid'))
    completeStationHandoff.mockRejectedValueOnce(
      new ApiError(
        {
          code: 'INVALID_PIN',
          message: 'Invalid PIN'
        },
        400
      )
    )

    const wrapper = await mountLineScan()
    await wrapper.find('[data-testid="handoff-request-start"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-testid="handoff-pin-input"]').setValue('000000')
    await wrapper.find('[data-testid="handoff-complete-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="handoff-invalid-pin-error"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="handoff-conflict-banner"]').exists()).toBe(false)
  })

  it('shows a non-blocking handoff toast while the evidence workspace remains active', async () => {
    requestStationHandoff.mockResolvedValueOnce(buildHandoff('handoff-toast'))

    const wrapper = await mountLineScan()
    await wrapper.find('[data-testid="handoff-request-start"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="handoff-request-toast"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="line-scan-capture-stub"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="line-scan-capture"]').attributes('disabled')).toBeUndefined()
  })

  it('shows queue summary details after the workspace reports conflicts and failed syncs', async () => {
    queueState.value = 2
    const wrapper = await mountLineScan()

    await wrapper.find('[data-testid="emit-queue-summary"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="offline-queue-indicator"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('2 operation(s) queued for sync')
    expect(wrapper.text()).toContain('1 conflict')
    expect(wrapper.text()).toContain('1 failed')
  })

  it('keeps the workspace honest when no capture session is present', async () => {
    const wrapper = await mountLineScan('/operator/regattas/regatta-97/line-scan')

    await wrapper.find('[data-testid="line-scan-capture"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Capture session is required to open workspace')
  })
})