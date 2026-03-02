import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createFinanceApi } from '../finance'

describe('finance', () => {
  let api
  let mockClient

  beforeEach(() => {
    mockClient = {
      request: vi.fn(),
      get: vi.fn(),
      patch: vi.fn(),
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

  describe('getEntryPaymentStatus', () => {
    it('calls correct endpoint with entry ID', async () => {
      const regattaId = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
      const entryId = '7f7af3d8-9090-49d5-b21c-9cc12d35a0e6'
      const mockResponse = {
        entry_id: entryId,
        payment_status: 'paid',
        paid_at: '2026-02-27T10:00:00Z',
        paid_by: 'staff@example.com',
        payment_reference: 'REF-123'
      }

      mockClient.get.mockResolvedValue(mockResponse)

      const result = await api.getEntryPaymentStatus(regattaId, entryId)

      expect(mockClient.get).toHaveBeenCalledWith(
        `/regattas/${regattaId}/entries/${entryId}/payment_status`
      )
      expect(result).toEqual(mockResponse)
    })
  })

  describe('updateEntryPaymentStatus', () => {
    it('calls correct endpoint with payment update', async () => {
      const regattaId = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
      const entryId = '7f7af3d8-9090-49d5-b21c-9cc12d35a0e6'
      const payload = {
        payment_status: 'paid',
        payment_reference: 'REF-456'
      }
      const mockResponse = {
        entry_id: entryId,
        payment_status: 'paid',
        paid_at: '2026-02-27T11:00:00Z',
        paid_by: 'staff@example.com',
        payment_reference: 'REF-456'
      }

      mockClient.patch.mockResolvedValue(mockResponse)

      const result = await api.updateEntryPaymentStatus(regattaId, entryId, payload)

      expect(mockClient.patch).toHaveBeenCalledWith(
        `/regattas/${regattaId}/entries/${entryId}/payment_status`,
        payload
      )
      expect(result).toEqual(mockResponse)
    })
  })

  describe('getClubPaymentStatus', () => {
    it('calls correct endpoint with club ID', async () => {
      const regattaId = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
      const clubId = '81a4c9ea-2e7d-4e67-8c0e-4657d8ce26fd'
      const mockResponse = {
        club_id: clubId,
        total_entries: 5,
        paid_entries: 3,
        unpaid_entries: 2,
        entries: []
      }

      mockClient.get.mockResolvedValue(mockResponse)

      const result = await api.getClubPaymentStatus(regattaId, clubId)

      expect(mockClient.get).toHaveBeenCalledWith(
        `/regattas/${regattaId}/clubs/${clubId}/payment_status`
      )
      expect(result).toEqual(mockResponse)
    })
  })

  describe('updateClubPaymentStatus', () => {
    it('calls correct endpoint with payment update', async () => {
      const regattaId = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
      const clubId = '81a4c9ea-2e7d-4e67-8c0e-4657d8ce26fd'
      const payload = {
        payment_status: 'paid',
        payment_reference: 'CLUB-REF-789'
      }
      const mockResponse = {
        club_id: clubId,
        updated_entries: 2,
        message: 'Updated payment status for 2 entries'
      }

      mockClient.patch.mockResolvedValue(mockResponse)

      const result = await api.updateClubPaymentStatus(regattaId, clubId, payload)

      expect(mockClient.patch).toHaveBeenCalledWith(
        `/regattas/${regattaId}/clubs/${clubId}/payment_status`,
        payload
      )
      expect(result).toEqual(mockResponse)
    })
  })

  describe('listInvoices', () => {
    it('calls correct endpoint for invoice list', async () => {
      const regattaId = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
      const mockResponse = {
        invoices: [
          { invoice_id: 'inv-1', status: 'unpaid' },
          { invoice_id: 'inv-2', status: 'paid' }
        ],
        cursor: 'next-cursor'
      }

      mockClient.get.mockResolvedValue(mockResponse)

      const result = await api.listInvoices(regattaId)

      expect(mockClient.get).toHaveBeenCalledWith(
        `/regattas/${regattaId}/invoices`,
        { params: {} }
      )
      expect(result).toEqual(mockResponse)
    })

    it('supports pagination parameters', async () => {
      const regattaId = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
      const params = { limit: 50, cursor: 'prev-cursor' }

      mockClient.get.mockResolvedValue({ invoices: [], cursor: null })

      await api.listInvoices(regattaId, params)

      expect(mockClient.get).toHaveBeenCalledWith(
        `/regattas/${regattaId}/invoices`,
        { params }
      )
    })
  })

  describe('getInvoice', () => {
    it('calls correct endpoint with invoice ID', async () => {
      const regattaId = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
      const invoiceId = 'inv-123'
      const mockResponse = {
        invoice_id: invoiceId,
        club_id: '81a4c9ea-2e7d-4e67-8c0e-4657d8ce26fd',
        status: 'unpaid',
        amount: 150
      }

      mockClient.get.mockResolvedValue(mockResponse)

      const result = await api.getInvoice(regattaId, invoiceId)

      expect(mockClient.get).toHaveBeenCalledWith(
        `/regattas/${regattaId}/invoices/${invoiceId}`
      )
      expect(result).toEqual(mockResponse)
    })
  })

  describe('generateInvoices', () => {
    it('calls correct endpoint to generate invoices', async () => {
      const regattaId = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
      const mockResponse = {
        job_id: 'job-456',
        status: 'accepted',
        message: 'Invoice generation job accepted'
      }

      mockClient.post.mockResolvedValue(mockResponse)

      const result = await api.generateInvoices(regattaId)

      expect(mockClient.post).toHaveBeenCalledWith(
        `/regattas/${regattaId}/invoices/generate`,
        {}
      )
      expect(result).toEqual(mockResponse)
    })
  })

  describe('markInvoicePaid', () => {
    it('calls correct endpoint with payment details', async () => {
      const regattaId = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
      const invoiceId = 'inv-789'
      const payload = {
        payment_reference: 'PAYMENT-REF-001',
        paid_at: '2026-02-27T12:00:00Z'
      }
      const mockResponse = {
        invoice_id: invoiceId,
        status: 'paid',
        paid_at: '2026-02-27T12:00:00Z',
        payment_reference: 'PAYMENT-REF-001'
      }

      mockClient.post.mockResolvedValue(mockResponse)

      const result = await api.markInvoicePaid(regattaId, invoiceId, payload)

      expect(mockClient.post).toHaveBeenCalledWith(
        `/regattas/${regattaId}/invoices/${invoiceId}/mark_paid`,
        payload
      )
      expect(result).toEqual(mockResponse)
    })

    it('supports empty payload', async () => {
      const regattaId = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
      const invoiceId = 'inv-789'

      mockClient.post.mockResolvedValue({ invoice_id: invoiceId, status: 'paid' })

      await api.markInvoicePaid(regattaId, invoiceId)

      expect(mockClient.post).toHaveBeenCalledWith(
        `/regattas/${regattaId}/invoices/${invoiceId}/mark_paid`,
        {}
      )
    })
  })
})
