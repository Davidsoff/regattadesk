/**
 * Shared API client for RegattaDesk frontend.
 * 
 * Provides:
 * - Consistent request/response handling
 * - OpenAPI error normalization
 * - Idempotency key support
 * - Auth token handling
 */

import { normalizeApiError, isApiError } from './errors.js'

/**
 * Custom error class for API errors with normalized fields.
 */
export class ApiError extends Error {
  constructor(normalizedError, status) {
    super(normalizedError.message)
    this.name = 'ApiError'
    this.code = normalizedError.code
    this.details = normalizedError.details
    this.requestId = normalizedError.requestId
    this.status = status
  }
}

/**
 * Create an API client instance.
 * @param {object} options - Client configuration
 * @param {string} options.baseUrl - Base URL for API requests (default: '/api/v1')
 * @returns {object} API client with request methods
 */
export function createApiClient(options = {}) {
  const { baseUrl = '/api/v1' } = options

  /**
   * Make an HTTP request to the API.
   * @param {string} method - HTTP method
   * @param {string} path - Request path (relative to baseUrl)
   * @param {object} options - Request options
   * @param {object} options.body - Request body (will be JSON-stringified)
   * @param {object} options.headers - Additional headers
   * @param {string} options.idempotencyKey - Idempotency key for safe retries
   * @returns {Promise<object>} Response data
   * @throws {ApiError} When response is not ok
   */
  async function request(method, path, options = {}) {
    const { body, headers = {}, idempotencyKey } = options

    const url = `${baseUrl}${path}`
    const requestHeaders = {
      'Content-Type': 'application/json',
      ...headers
    }

    if (idempotencyKey) {
      requestHeaders['Idempotency-Key'] = idempotencyKey
    }

    const requestInit = {
      method,
      headers: requestHeaders
    }

    if (body !== undefined) {
      requestInit.body = JSON.stringify(body)
    }

    let response
    try {
      response = await fetch(url, requestInit)
    } catch (error) {
      // Network error or other fetch failure
      throw error
    }

    let data
    try {
      data = await response.json()
    } catch (error) {
      // Response is not JSON or malformed
      if (!response.ok) {
        // Non-JSON error response
        const normalized = normalizeApiError(null)
        throw new ApiError(normalized, response.status)
      }
      throw error
    }

    if (!response.ok) {
      const normalized = normalizeApiError(data)
      throw new ApiError(normalized, response.status)
    }

    return data
  }

  return {
    request,

    /**
     * Make a GET request.
     * @param {string} path - Request path
     * @param {object} options - Request options
     * @returns {Promise<object>} Response data
     */
    get(path, options) {
      return request('GET', path, options)
    },

    /**
     * Make a POST request.
     * @param {string} path - Request path
     * @param {object} body - Request body
     * @param {object} options - Request options
     * @returns {Promise<object>} Response data
     */
    post(path, body, options) {
      return request('POST', path, { ...options, body })
    },

    /**
     * Make a PATCH request.
     * @param {string} path - Request path
     * @param {object} body - Request body
     * @param {object} options - Request options
     * @returns {Promise<object>} Response data
     */
    patch(path, body, options) {
      return request('PATCH', path, { ...options, body })
    },

    /**
     * Make a DELETE request.
     * @param {string} path - Request path
     * @param {object} options - Request options
     * @returns {Promise<object>} Response data
     */
    delete(path, options) {
      return request('DELETE', path, options)
    }
  }
}
