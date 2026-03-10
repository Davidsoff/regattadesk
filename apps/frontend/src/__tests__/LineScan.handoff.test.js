import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import LineScan from '../views/operator/LineScan.vue'

const requestStationHandoff = vi.fn()
const revealStationHandoffPin = vi.fn()
const getStationHandoffStatus = vi.fn()
const cancelStationHandoff = vi.fn()
const completeStationHandoff = vi.fn()

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

function createTestI18n() {
  return createI18n({
    legacy: false,
    locale: 'en',
    messages: {
      en: {
        operator: {
          regatta: {
            id: 'Regatta ID'
          },
          line_scan: {
            title: 'Line Scan',
            description: 'Capture line scan images and mark finish times'
          },
          capture: {
            title: 'Line Scan Capture',
            create_marker: 'Create Marker',
            no_markers: 'No markers yet'
          }
        }
      }
    }
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

async function mountLineScan() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/operator/regattas/:regattaId/sessions/:captureSessionId/line-scan',
        name: 'operator-session-line-scan',
        component: LineScan
      }
    ]
  })

  await router.push('/operator/regattas/regatta-97/sessions/session-97/line-scan')
  await router.isReady()

  return mount(LineScan, {
    global: {
      plugins: [router, createTestI18n()]
    }
  })
}

async function mountLineScanWithoutSession() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/operator/regattas/:regattaId/line-scan',
        name: 'operator-line-scan-legacy-test',
        component: LineScan
      }
    ]
  })

  await router.push('/operator/regattas/regatta-97/line-scan')
  await router.isReady()

  return mount(LineScan, {
    global: {
      plugins: [router, createTestI18n()]
    }
  })
}

describe('Operator line-scan handoff UX (issue #97)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('__REGATTADESK_AUTH__', {
      operatorToken: 'token-97',
      operatorStation: 'finish-line'
    })
  })

  afterEach(() => {
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
    expect(wrapper.find('[data-testid="handoff-request-toast"]').exists()).toBe(false)
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

  it('prevents concurrent reveal requests while one reveal is in flight', async () => {
    let resolveReveal
    const revealPromise = new Promise((resolve) => {
      resolveReveal = resolve
    })

    requestStationHandoff.mockResolvedValueOnce(buildHandoff('handoff-reveal'))
    revealStationHandoffPin.mockReturnValueOnce(revealPromise)

    const wrapper = await mountLineScan()
    await wrapper.find('[data-testid="handoff-request-start"]').trigger('click')
    await flushPromises()

    const buttons = wrapper.findAll('button')
    const revealButton = buttons.find((button) => button.text() === 'Show PIN')
    expect(revealButton).toBeDefined()

    await revealButton?.trigger('click')
    await revealButton?.trigger('click')
    expect(revealStationHandoffPin).toHaveBeenCalledTimes(1)

    resolveReveal({ pin: '123456' })
    await flushPromises()
    expect(wrapper.find('[data-testid="handoff-revealed-pin"]').text()).toContain('123456')
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

  it('renders conflict guidance from API 409 while keeping line-scan usable during pending handoff', async () => {
    const { ApiError } = await import('../api')
    requestStationHandoff.mockResolvedValueOnce(buildHandoff('handoff-conflict'))
    completeStationHandoff.mockRejectedValueOnce(
      new ApiError(
        {
          code: 'HANDOFF_CONFLICT',
          message: 'This handoff is no longer available'
        },
        409
      )
    )

    const wrapper = await mountLineScan()
    await wrapper.find('[data-testid="handoff-request-start"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="handoff-request-toast"]').exists()).toBe(true)
    const scanCaptureButton = wrapper.find('[data-testid="line-scan-capture"]')
    expect(scanCaptureButton.attributes('disabled')).toBeUndefined()

    await wrapper.find('[data-testid="handoff-pin-input"]').setValue('123456')
    await wrapper.find('[data-testid="handoff-complete-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="handoff-conflict-banner"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('This handoff is no longer available')
  })

  it('shows non-interrupting handoff request toast while keeping line-scan actions active', async () => {
    requestStationHandoff.mockResolvedValueOnce(buildHandoff('handoff-toast'))

    const wrapper = await mountLineScan()
    expect(wrapper.find('[data-testid="handoff-request-toast"]').exists()).toBe(false)
    await wrapper.find('[data-testid="handoff-request-start"]').trigger('click')
    await flushPromises()

    const handoffToast = wrapper.find('[data-testid="handoff-request-toast"]')
    expect(handoffToast.exists()).toBe(true)
    expect(handoffToast.text()).toContain('Show PIN')
    expect(handoffToast.text()).not.toContain('Approve')
    expect(handoffToast.text()).not.toContain('Deny')

    const scanCaptureButton = wrapper.find('[data-testid="line-scan-capture"]')
    expect(scanCaptureButton.exists()).toBe(true)
    expect(scanCaptureButton.attributes('disabled')).toBeUndefined()
  })

  it('does not call capture session create endpoint when workspace is toggled without capture_session_id', async () => {
    const wrapper = await mountLineScanWithoutSession()
    await wrapper.find('[data-testid="toggle-capture-workspace"]').trigger('click')
    await flushPromises()

    expect(requestStationHandoff).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Capture session is required to open workspace')
  })
})
