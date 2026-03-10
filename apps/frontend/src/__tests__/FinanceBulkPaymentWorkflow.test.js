import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import FinanceBulkPaymentWorkflow from '../components/FinanceBulkPaymentWorkflow.vue'
import i18n from '../i18n'

function jsonResponse(body, status = 200) {
  return new Response(body == null ? '' : JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json'
    }
  })
}

async function getRequestCall(fetchMock, index = 0) {
  const request = fetchMock.mock.calls[index][0]
  return {
    request,
    url: new URL(request.url).pathname,
    method: request.method,
    body: request.body ? await request.clone().text() : ''
  }
}

describe('FinanceBulkPaymentWorkflow', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('requires at least one selection before confirmation', async () => {
    const wrapper = mount(FinanceBulkPaymentWorkflow, {
      props: { regattaId: 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc' },
      global: { plugins: [i18n] }
    })

    await wrapper.get('form').trigger('submit.prevent')

    expect(wrapper.text()).toContain(i18n.global.t('finance.bulk.select_one'))
    expect(wrapper.text()).not.toContain(i18n.global.t('finance.bulk.confirm'))
  })

  it('shows confirmation and submits successfully', async () => {
    const mockResponse = {
      success: true,
      message: 'Bulk payment update completed',
      total_requested: 1,
      processed_count: 1,
      updated_count: 1,
      unchanged_count: 0,
      failed_count: 0,
      failures: [],
      idempotency_key: 'key-1',
      idempotent_replay: false
    }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(mockResponse))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(FinanceBulkPaymentWorkflow, {
      props: { regattaId: 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc' },
      global: { plugins: [i18n] }
    })

    await wrapper.get('textarea[name="entry_ids"]').setValue('7f7af3d8-9090-49d5-b21c-9cc12d35a0e6')
    await wrapper.get('input[name="idempotency_key"]').setValue('key-1')
    await wrapper.get('form').trigger('submit.prevent')

    expect(wrapper.text()).toContain(i18n.global.t('finance.bulk.confirm'))

    await wrapper.get('.confirm-actions .primary').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const { url, method } = await getRequestCall(fetchMock)
    expect(url).toBe('/api/v1/regattas/f3cf2a08-91e0-469d-a851-41a6f3d0e3dc/payments/mark_bulk')
    expect(method).toBe('POST')
    // Assert against the mock response message to avoid coupling to a hardcoded string
    expect(wrapper.text()).toContain(mockResponse.message)
    expect(wrapper.text()).toContain(i18n.global.t('finance.bulk.updated', { count: 1 }))
  })

  it('only sends non-empty entry_ids/club_ids in the request body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        success: true,
        message: 'Bulk payment update completed',
        total_requested: 1,
        processed_count: 1,
        updated_count: 1,
        unchanged_count: 0,
        failed_count: 0,
        failures: [],
        idempotency_key: null,
        idempotent_replay: false
      })
    )
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(FinanceBulkPaymentWorkflow, {
      props: { regattaId: 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc' },
      global: { plugins: [i18n] }
    })

    // Only entry_ids filled — club_ids textarea is empty
    await wrapper.get('textarea[name="entry_ids"]').setValue('7f7af3d8-9090-49d5-b21c-9cc12d35a0e6')
    await wrapper.get('form').trigger('submit.prevent')
    await wrapper.get('.confirm-actions .primary').trigger('click')
    await flushPromises()

    const { body } = await getRequestCall(fetchMock)
    const parsedBody = JSON.parse(body)
    expect(parsedBody).toHaveProperty('entry_ids')
    expect(parsedBody).not.toHaveProperty('club_ids')
  })

  it('shows API error message from response when present', async () => {
    const apiErrorMessage = 'Failed to bulk update payment status'
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          error: {
            code: 'VALIDATION_ERROR',
            message: apiErrorMessage
          }
        }, 400)
      )
    )

    const wrapper = mount(FinanceBulkPaymentWorkflow, {
      props: { regattaId: 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc' },
      global: { plugins: [i18n] }
    })

    await wrapper.get('textarea[name="entry_ids"]').setValue('7f7af3d8-9090-49d5-b21c-9cc12d35a0e6')
    await wrapper.get('form').trigger('submit.prevent')
    await wrapper.get('.confirm-actions .primary').trigger('click')
    await flushPromises()

    // Component shows payload.error.message when present
    expect(wrapper.text()).toContain(apiErrorMessage)
  })

  it('shows i18n fallback when API error has no message', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          error: {
            code: 'INTERNAL_ERROR'
          }
        }, 500)
      )
    )

    const wrapper = mount(FinanceBulkPaymentWorkflow, {
      props: { regattaId: 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc' },
      global: { plugins: [i18n] }
    })

    await wrapper.get('textarea[name="entry_ids"]').setValue('7f7af3d8-9090-49d5-b21c-9cc12d35a0e6')
    await wrapper.get('form').trigger('submit.prevent')
    await wrapper.get('.confirm-actions .primary').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain(i18n.global.t('finance.bulk.error'))
  })

  it('does not show SSE status indicator', () => {
    const wrapper = mount(FinanceBulkPaymentWorkflow, {
      props: { regattaId: 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc' },
      global: { plugins: [i18n] }
    })

    // Component should not contain live/offline indicator
    expect(wrapper.text()).not.toContain(i18n.global.t('live.live'))
    expect(wrapper.text()).not.toContain(i18n.global.t('live.offline'))
    expect(wrapper.find('.sse-pill').exists()).toBe(false)
  })
})
