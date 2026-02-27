import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import LineScan from '../views/operator/LineScan.vue'

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
    requesting_device_id: 'secondary-device',
    active_device_id: 'primary-device',
    status: 'pending',
    previous_device_mode: 'active',
    new_device_mode: 'read_only',
    expires_at: '2026-01-01T00:00:00.000Z',
    ...overrides
  }
}

function jsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: {
      get(name) {
        return name.toLowerCase() === 'content-type' ? 'application/json' : null
      }
    },
    async json() {
      return body
    }
  }
}

async function mountLineScan() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/operator/regattas/:regattaId/line-scan',
        name: 'operator-line-scan',
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
    vi.restoreAllMocks()
    vi.stubGlobal('__REGATTADESK_AUTH__', { operatorToken: 'token-97' })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('sends operator token and contract path when completing handoff, then demotes previous device to read-only', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, buildHandoff('handoff-1')))
      .mockResolvedValueOnce(
        jsonResponse(
          200,
          buildHandoff('handoff-1', {
            status: 'completed',
            previous_device_mode: 'read_only',
            new_device_mode: 'active'
          })
        )
      )

    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountLineScan()
    expect(wrapper.find('[data-testid="handoff-request-toast"]').exists()).toBe(false)
    await wrapper.find('[data-testid="handoff-request-start"]').trigger('click')
    await flushPromises()

    const createCall = fetchMock.mock.calls[0]
    expect(createCall[0]).toBe('/api/v1/regattas/regatta-97/operator/station_handoffs')
    expect(createCall[1].method).toBe('POST')
    expect(createCall[1].headers.x_operator_token).toBe('token-97')

    await wrapper.find('[data-testid="handoff-pin-input"]').setValue('123456')
    await wrapper.find('[data-testid="handoff-complete-submit"]').trigger('click')
    await flushPromises()

    const completeCall = fetchMock.mock.calls[1]
    expect(completeCall[0]).toBe('/api/v1/regattas/regatta-97/operator/station_handoffs/handoff-1/complete')
    expect(completeCall[1].method).toBe('POST')
    expect(completeCall[1].headers.x_operator_token).toBe('token-97')
    expect(JSON.parse(completeCall[1].body)).toEqual({
      pin: '123456',
      completing_device_id: 'primary-device'
    })

    expect(wrapper.find('[data-testid="operator-readonly-banner"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="line-scan-capture"]').attributes('disabled')).toBeDefined()
  })

  it('renders INVALID_PIN feedback from API 400 response', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, buildHandoff('handoff-invalid')))
      .mockResolvedValueOnce(
        jsonResponse(400, {
          error: {
            code: 'INVALID_PIN',
            message: 'Invalid PIN'
          }
        })
      )

    vi.stubGlobal('fetch', fetchMock)

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
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, buildHandoff('handoff-conflict')))
      .mockResolvedValueOnce(
        jsonResponse(409, {
          error: {
            code: 'HANDOFF_CONFLICT',
            message: 'This handoff is no longer available'
          }
        })
      )

    vi.stubGlobal('fetch', fetchMock)

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
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(200, buildHandoff('handoff-toast'))))

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
})
