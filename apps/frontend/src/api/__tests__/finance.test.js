import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createFinanceApi } from '../finance'

describe('finance', () => {
  let api
  let mockClient

  beforeEach(() => {
    mockClient = {
      request: vi.fn(),
      post: vi.fn()
    }
    api = createFinanceApi(mockClient)
  })

  describe('markBulkPayment', () => {
    it('calls correct endpoint with payload', async () => {
      const regattaId = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
      const payload = {
        entry_ids: ['7f7af3d8-9090-49d5-b21c-9cc12d35a0e6'],
        payment_status: 'paid'
      }
      const mockResponse = {
        success: true,
        message: 'Bulk payment update completed',
        total_requested: 1,
        processed_count: 1,
        updated_count: 1,
        unchanged_count: 0,
        failed_count: 0,
        failures: []
      }

      mockClient.post.mockResolvedValue(mockResponse)

      const result = await api.markBulkPayment(regattaId, payload)

      expect(mockClient.post).toHaveBeenCalledWith(
        `/regattas/${regattaId}/payments/mark_bulk`,
        payload
      )
      expect(result).toEqual(mockResponse)
    })

    it('supports idempotency key', async () => {
      const regattaId = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
      const payload = {
        entry_ids: ['7f7af3d8-9090-49d5-b21c-9cc12d35a0e6'],
        payment_status: 'paid',
        idempotency_key: 'key-123'
      }
      const mockResponse = {
        success: true,
        message: 'Bulk payment update completed',
        total_requested: 1,
        processed_count: 1,
        updated_count: 1,
        unchanged_count: 0,
        failed_count: 0,
        failures: [],
        idempotency_key: 'key-123',
        idempotent_replay: false
      }

      mockClient.post.mockResolvedValue(mockResponse)

      await api.markBulkPayment(regattaId, payload)

      expect(mockClient.post).toHaveBeenCalledWith(
        `/regattas/${regattaId}/payments/mark_bulk`,
        payload
      )
    })

    it('keeps idempotency key in payload per OpenAPI contract', async () => {
      const regattaId = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
      const payload = {
        entry_ids: ['7f7af3d8-9090-49d5-b21c-9cc12d35a0e6'],
        payment_status: 'paid',
        idempotency_key: 'key-456'
      }

      mockClient.post.mockResolvedValue({
        success: true,
        message: 'Bulk payment update completed',
        total_requested: 1,
        processed_count: 1,
        updated_count: 1,
        unchanged_count: 0,
        failed_count: 0,
        failures: []
      })

      await api.markBulkPayment(regattaId, payload)

      const callArgs = mockClient.post.mock.calls[0]
      expect(callArgs).toEqual([`/regattas/${regattaId}/payments/mark_bulk`, payload])
    })

    it('handles API errors', async () => {
      const regattaId = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
      const payload = {
        entry_ids: ['invalid-id'],
        payment_status: 'paid'
      }
      const apiError = new Error('Invalid entry ID')
      apiError.code = 'VALIDATION_ERROR'

      mockClient.post.mockRejectedValue(apiError)

      await expect(api.markBulkPayment(regattaId, payload)).rejects.toThrow('Invalid entry ID')
    })
  })
})
