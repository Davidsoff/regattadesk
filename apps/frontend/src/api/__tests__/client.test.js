import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createApiClient } from '../client'

function jsonResponse(payload, overrides = {}) {
  const status = overrides.status ?? 200
  const ok = overrides.ok ?? (status >= 200 && status < 300)
  const headers = new Headers(overrides.headers ?? { 'Content-Type': 'application/json' })
  const body = payload === undefined ? '' : JSON.stringify(payload)
  return new Response(body, { status, headers })
}

function getRequest(fetchMock) {
  const [request] = fetchMock.mock.calls[0]
  return request
}

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
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(mockResponse))
      vi.stubGlobal('fetch', fetchMock)

      const result = await client.request('GET', '/test')

      const request = getRequest(fetchMock)
      expect(request.method).toBe('GET')
      expect(request.url).toContain('/api/v1/test')
      expect(result).toEqual(mockResponse)
    })

    it('makes POST request with JSON body and default content type', async () => {
      const mockResponse = { success: true }
      const requestBody = { name: 'test' }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(mockResponse))
      vi.stubGlobal('fetch', fetchMock)

      const result = await client.request('POST', '/test', { body: requestBody })

      const request = getRequest(fetchMock)
      expect(request.method).toBe('POST')
      expect(request.url).toContain('/api/v1/test')
      expect(request.headers.get('Content-Type')).toBe('application/json')
      await expect(request.json()).resolves.toEqual(requestBody)
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

      const request = getRequest(fetchMock)
      expect(request.url).toContain('/api/v1/tiles/1')
      expect(request.headers.get('Content-Type')).toBe('image/png')
    })

    it('merges custom headers with defaults', async () => {
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse({}))
      vi.stubGlobal('fetch', fetchMock)

      await client.request('GET', '/test', {
        headers: { 'X-Custom': 'value' }
      })

      const request = getRequest(fetchMock)
      expect(request.url).toContain('/api/v1/test')
      expect(request.headers.get('X-Custom')).toBe('value')
    })

    it('throws normalized error when response is not ok', async () => {
      const errorResponse = {
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Invalid input'
        }
      }
      const fetchMock = vi.fn().mockImplementation(() =>
        Promise.resolve(jsonResponse(errorResponse, { status: 400 }))
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
        jsonResponse({
          error: {
            code: 'NOT_FOUND',
            message: 'Resource not found'
          }
        }, { status: 404 })
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
      const fetchMock = vi.fn().mockResolvedValue(
        new Response('internal error', {
          status: 500,
          headers: { 'Content-Type': 'text/plain' }
        })
      )
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
        status: 200,
        headers: { 'Content-Type': 'application/pdf' }
      }
      const fetchMock = vi.fn().mockResolvedValue(new Response('pdf-binary', rawResponse))
      vi.stubGlobal('fetch', fetchMock)

      const result = await client.request('GET', '/export')
      expect(result).toBeInstanceOf(Blob)
    })
  })

  describe('convenience methods', () => {
    it('provides get method', async () => {
      const mockResponse = { data: 'test' }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(mockResponse))
      vi.stubGlobal('fetch', fetchMock)

      const result = await client.get('/test')

      const request = getRequest(fetchMock)
      expect(request.method).toBe('GET')
      expect(request.url).toContain('/api/v1/test')
      expect(result).toEqual(mockResponse)
    })

    it('provides post method', async () => {
      const mockResponse = { success: true }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(mockResponse))
      vi.stubGlobal('fetch', fetchMock)

      const result = await client.post('/test', { name: 'test' })

      const request = getRequest(fetchMock)
      expect(request.method).toBe('POST')
      expect(request.url).toContain('/api/v1/test')
      await expect(request.json()).resolves.toEqual({ name: 'test' })
      expect(result).toEqual(mockResponse)
    })

    it('provides patch method', async () => {
      const mockResponse = { success: true }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(mockResponse))
      vi.stubGlobal('fetch', fetchMock)

      const result = await client.patch('/test', { name: 'updated' })

      const request = getRequest(fetchMock)
      expect(request.method).toBe('PATCH')
      expect(request.url).toContain('/api/v1/test')
      await expect(request.json()).resolves.toEqual({ name: 'updated' })
      expect(result).toEqual(mockResponse)
    })

    it('provides delete method', async () => {
      const mockResponse = { success: true }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(mockResponse))
      vi.stubGlobal('fetch', fetchMock)

      const result = await client.delete('/test')

      const request = getRequest(fetchMock)
      expect(request.method).toBe('DELETE')
      expect(request.url).toContain('/api/v1/test')
      expect(result).toEqual(mockResponse)
    })
  })
})
