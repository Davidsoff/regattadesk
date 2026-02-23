import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import FinanceBulkPaymentWorkflow from '../components/FinanceBulkPaymentWorkflow.vue'
import i18n from '../i18n'

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
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockResponse
    })
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

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/regattas/f3cf2a08-91e0-469d-a851-41a6f3d0e3dc/payments/mark_bulk')
    // Assert against the mock response message to avoid coupling to a hardcoded string
    expect(wrapper.text()).toContain(mockResponse.message)
    expect(wrapper.text()).toContain(i18n.global.t('finance.bulk.updated', { count: 1 }))
  })

  it('only sends non-empty entry_ids/club_ids in the request body', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
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
    })
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(FinanceBulkPaymentWorkflow, {
      props: { regattaId: 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc' },
      global: { plugins: [i18n] }
    })

    // Only entry_ids filled — club_ids textarea is empty
    await wrapper.get('textarea[name="entry_ids"]').setValue('7f7af3d8-9090-49d5-b21c-9cc12d35a0e6')
    await wrapper.get('form').trigger('submit.prevent')
    await wrapper.get('.confirm-actions .primary').trigger('click')

    const body = JSON.parse(fetchMock.mock.calls[0][1].body)
    expect(body).toHaveProperty('entry_ids')
    expect(body).not.toHaveProperty('club_ids')
  })

  it('shows API error message from response when present', async () => {
    const apiErrorMessage = 'Failed to bulk update payment status'
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        json: async () => ({
          error: { message: apiErrorMessage }
        })
      })
    )

    const wrapper = mount(FinanceBulkPaymentWorkflow, {
      props: { regattaId: 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc' },
      global: { plugins: [i18n] }
    })

    await wrapper.get('textarea[name="entry_ids"]').setValue('7f7af3d8-9090-49d5-b21c-9cc12d35a0e6')
    await wrapper.get('form').trigger('submit.prevent')
    await wrapper.get('.confirm-actions .primary').trigger('click')

    // Component shows payload.error.message when present
    expect(wrapper.text()).toContain(apiErrorMessage)
  })

  it('shows i18n fallback when API error has no message', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        json: async () => ({ error: {} })
      })
    )

    const wrapper = mount(FinanceBulkPaymentWorkflow, {
      props: { regattaId: 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc' },
      global: { plugins: [i18n] }
    })

    await wrapper.get('textarea[name="entry_ids"]').setValue('7f7af3d8-9090-49d5-b21c-9cc12d35a0e6')
    await wrapper.get('form').trigger('submit.prevent')
    await wrapper.get('.confirm-actions .primary').trigger('click')

    // Falls back to i18n error key when API provides no message
    expect(wrapper.text()).toContain(i18n.global.t('finance.bulk.error'))
  })
})
