import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createApiClient } from '../client'

describe('client', () => {
  let client

  beforeEach(() => {
    vi.restoreAllMocks()
    client = createApiClient({ baseUrl: '/api/v1' })
  })

  function jsonResponse(payload, overrides = {}) {
    return {
      ok: true,
      status: 200,
      headers: { get: () => 'application/json' },
      json: async () => payload,
      ...overrides
    }
  }

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
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(mockResponse))
      vi.stubGlobal('fetch', fetchMock)

      const result = await client.request('GET', '/test')

      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/test',
        expect.objectContaining({
          method: 'GET',
          headers: {}
        })
      )
      expect(result).toEqual(mockResponse)
    })

    it('makes POST request with JSON body and default content type', async () => {
      const mockResponse = { success: true }
      const requestBody = { name: 'test' }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(mockResponse))
      vi.stubGlobal('fetch', fetchMock)

      const result = await client.request('POST', '/test', { body: requestBody })

      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/test',
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({
            'Content-Type': 'application/json'
          }),
          body: JSON.stringify(requestBody)
        })
      )
      expect(result).toEqual(mockResponse)
    })

    it('does not stringify non-JSON body payloads', async () => {
      const bytes = new Uint8Array([1, 2, 3])
      const fetchMock = vi.fn().mockResolvedValue(
        jsonResponse({ ok: true }, { headers: { get: () => 'application/json' } })
      )
      vi.stubGlobal('fetch', fetchMock)

      await client.request('PUT', '/tiles/1', {
        body: bytes,
        headers: { 'Content-Type': 'image/png' }
      })

      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/tiles/1',
        expect.objectContaining({
          body: bytes,
          headers: expect.objectContaining({
            'Content-Type': 'image/png'
          })
        })
      )
    })

    it('merges custom headers with defaults', async () => {
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse({}))
      vi.stubGlobal('fetch', fetchMock)

      await client.request('GET', '/test', {
        headers: { 'X-Custom': 'value' }
      })

      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/test',
        expect.objectContaining({
          headers: expect.objectContaining({
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
      const fetchMock = vi.fn().mockResolvedValue(
        jsonResponse(errorResponse, {
          ok: false,
          status: 400
        })
      )
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
      const fetchMock = vi.fn().mockResolvedValue(
        jsonResponse(
          {
            error: {
              code: 'NOT_FOUND',
              message: 'Resource not found'
            }
          },
          { ok: false, status: 404 }
        )
      )
      vi.stubGlobal('fetch', fetchMock)

      try {
        await client.request('GET', '/test')
      } catch (error) {
        expect(error.status).toBe(404)
      }
    })

    it('wraps network errors in ApiError shape', async () => {
      const fetchMock = vi.fn().mockRejectedValue(new Error('Network error'))
      vi.stubGlobal('fetch', fetchMock)

      await expect(client.request('GET', '/test')).rejects.toMatchObject({
        code: 'NETWORK_ERROR',
        status: 0,
        message: 'Network error'
      })
    })

    it('handles non-JSON error responses', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        headers: { get: () => 'text/plain' }
      })
      vi.stubGlobal('fetch', fetchMock)

      await expect(client.request('GET', '/test')).rejects.toMatchObject({
        code: 'UNKNOWN_ERROR'
      })
    })

    it('returns null for successful no-content responses', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        status: 204,
        headers: { get: () => '' }
      })
      vi.stubGlobal('fetch', fetchMock)

      const result = await client.request('DELETE', '/test')
      expect(result).toBeNull()
    })

    it('returns raw response for successful non-JSON payloads', async () => {
      const rawResponse = {
        ok: true,
        status: 200,
        headers: { get: () => 'application/pdf' }
      }
      const fetchMock = vi.fn().mockResolvedValue(rawResponse)
      vi.stubGlobal('fetch', fetchMock)

      const result = await client.request('GET', '/export')
      expect(result).toBe(rawResponse)
    })
  })

  describe('convenience methods', () => {
    it('provides get method', async () => {
      const mockResponse = { data: 'test' }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(mockResponse))
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
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(mockResponse))
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
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(mockResponse))
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
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(mockResponse))
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
