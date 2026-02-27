import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createApiClient } from '../client'

describe('client', () => {
  let client

  beforeEach(() => {
    vi.restoreAllMocks()
    client = createApiClient({ baseUrl: '/api/v1' })
  })

  describe('createApiClient', () => {
    it('creates a client with request method', () => {
      expect(client).toBeDefined()
      expect(typeof client.request).toBe('function')
    })

    it('uses default baseUrl when not provided', () => {
      const defaultClient = createApiClient()
      expect(defaultClient).toBeDefined()
    })
  })

  describe('request', () => {
    it('makes GET request with correct URL', async () => {
      const mockResponse = { data: 'test' }
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => mockResponse
      })
      vi.stubGlobal('fetch', fetchMock)

      const result = await client.request('GET', '/test')

      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/test',
        expect.objectContaining({
          method: 'GET',
          headers: expect.objectContaining({
            'Content-Type': 'application/json'
          })
        })
      )
      expect(result).toEqual(mockResponse)
    })

    it('makes POST request with body', async () => {
      const mockResponse = { success: true }
      const requestBody = { name: 'test' }
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => mockResponse
      })
      vi.stubGlobal('fetch', fetchMock)

      const result = await client.request('POST', '/test', { body: requestBody })

      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/test',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify(requestBody)
        })
      )
      expect(result).toEqual(mockResponse)
    })

    it('includes idempotency key header when provided', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({})
      })
      vi.stubGlobal('fetch', fetchMock)

      await client.request('POST', '/test', { idempotencyKey: 'key-123' })

      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/test',
        expect.objectContaining({
          headers: expect.objectContaining({
            'Idempotency-Key': 'key-123'
          })
        })
      )
    })

    it('merges custom headers with defaults', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({})
      })
      vi.stubGlobal('fetch', fetchMock)

      await client.request('GET', '/test', {
        headers: { 'X-Custom': 'value' }
      })

      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/test',
        expect.objectContaining({
          headers: expect.objectContaining({
            'Content-Type': 'application/json',
            'X-Custom': 'value'
          })
        })
      )
    })

    it('throws normalized error when response is not ok', async () => {
      const errorResponse = {
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Invalid input'
        }
      }
      const fetchMock = vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        json: async () => errorResponse
      })
      vi.stubGlobal('fetch', fetchMock)

      await expect(client.request('POST', '/test')).rejects.toThrow()

      try {
        await client.request('POST', '/test')
      } catch (error) {
        expect(error.code).toBe('VALIDATION_ERROR')
        expect(error.message).toBe('Invalid input')
      }
    })

    it('includes status code in error', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
        json: async () => ({
          error: {
            code: 'NOT_FOUND',
            message: 'Resource not found'
          }
        })
      })
      vi.stubGlobal('fetch', fetchMock)

      try {
        await client.request('GET', '/test')
      } catch (error) {
        expect(error.status).toBe(404)
      }
    })

    it('handles network errors gracefully', async () => {
      const fetchMock = vi.fn().mockRejectedValue(new Error('Network error'))
      vi.stubGlobal('fetch', fetchMock)

      await expect(client.request('GET', '/test')).rejects.toThrow('Network error')
    })

    it('handles non-JSON responses', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        json: async () => {
          throw new Error('Not JSON')
        }
      })
      vi.stubGlobal('fetch', fetchMock)

      try {
        await client.request('GET', '/test')
      } catch (error) {
        expect(error.code).toBe('UNKNOWN_ERROR')
      }
    })
  })

  describe('convenience methods', () => {
    it('provides get method', async () => {
      const mockResponse = { data: 'test' }
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => mockResponse
      })
      vi.stubGlobal('fetch', fetchMock)

      const result = await client.get('/test')

      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/test',
        expect.objectContaining({ method: 'GET' })
      )
      expect(result).toEqual(mockResponse)
    })

    it('provides post method', async () => {
      const mockResponse = { success: true }
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => mockResponse
      })
      vi.stubGlobal('fetch', fetchMock)

      const result = await client.post('/test', { name: 'test' })

      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/test',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ name: 'test' })
        })
      )
      expect(result).toEqual(mockResponse)
    })

    it('provides patch method', async () => {
      const mockResponse = { success: true }
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => mockResponse
      })
      vi.stubGlobal('fetch', fetchMock)

      const result = await client.patch('/test', { name: 'updated' })

      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/test',
        expect.objectContaining({
          method: 'PATCH',
          body: JSON.stringify({ name: 'updated' })
        })
      )
      expect(result).toEqual(mockResponse)
    })

    it('provides delete method', async () => {
      const mockResponse = { success: true }
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => mockResponse
      })
      vi.stubGlobal('fetch', fetchMock)

      const result = await client.delete('/test')

      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/test',
        expect.objectContaining({ method: 'DELETE' })
      )
      expect(result).toEqual(mockResponse)
    })
  })
})
