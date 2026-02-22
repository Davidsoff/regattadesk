import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import FinanceBulkPaymentWorkflow from '../components/FinanceBulkPaymentWorkflow.vue'

describe('FinanceBulkPaymentWorkflow', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('requires at least one selection before confirmation', async () => {
    const wrapper = mount(FinanceBulkPaymentWorkflow)

    await wrapper.get('form').trigger('submit.prevent')

    expect(wrapper.text()).toContain('Select at least one entry ID or club ID.')
    expect(wrapper.text()).not.toContain('Confirm Bulk Update')
  })

  it('shows confirmation and submits successfully', async () => {
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
        idempotency_key: 'key-1',
        idempotent_replay: false
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(FinanceBulkPaymentWorkflow, {
      props: { regattaId: 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc' }
    })

    await wrapper.get('textarea[name="entry_ids"]').setValue('7f7af3d8-9090-49d5-b21c-9cc12d35a0e6')
    await wrapper.get('input[name="idempotency_key"]').setValue('key-1')
    await wrapper.get('form').trigger('submit.prevent')

    expect(wrapper.text()).toContain('Confirm Bulk Update')

    await wrapper.get('.confirm-actions .primary').trigger('click')

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/regattas/f3cf2a08-91e0-469d-a851-41a6f3d0e3dc/payments/mark_bulk')
    expect(wrapper.text()).toContain('Bulk payment update completed')
    expect(wrapper.text()).toContain('Updated: 1')
  })

  it('shows API error feedback', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        json: async () => ({
          error: { message: 'Failed to bulk update payment status' }
        })
      })
    )

    const wrapper = mount(FinanceBulkPaymentWorkflow)

    await wrapper.get('textarea[name="entry_ids"]').setValue('7f7af3d8-9090-49d5-b21c-9cc12d35a0e6')
    await wrapper.get('form').trigger('submit.prevent')
    await wrapper.get('.confirm-actions .primary').trigger('click')

    expect(wrapper.text()).toContain('Failed to bulk update payment status')
  })
})
