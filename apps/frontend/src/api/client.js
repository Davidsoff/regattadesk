/**
 * Shared API client for RegattaDesk frontend.
 * 
 * Provides:
 * - Consistent request/response handling
 * - OpenAPI error normalization
 * - Flexible request body handling
 */

import { normalizeApiError } from './errors.js'

function isJsonSerializableBody(body) {
  return (
    body !== null &&
    typeof body === 'object' &&
    !(body instanceof ArrayBuffer) &&
    !ArrayBuffer.isView(body) &&
    !(typeof Blob !== 'undefined' && body instanceof Blob) &&
    !(typeof FormData !== 'undefined' && body instanceof FormData) &&
    !(typeof URLSearchParams !== 'undefined' && body instanceof URLSearchParams)
  )
}

function createRequestInit(method, body, headers = {}) {
  const requestHeaders = { ...headers }
  const requestInit = {
    method,
    headers: requestHeaders
  }

  if (body !== undefined) {
    if (isJsonSerializableBody(body)) {
      if (!('Content-Type' in requestHeaders) && !('content-type' in requestHeaders)) {
        requestHeaders['Content-Type'] = 'application/json'
      }
      requestInit.body = JSON.stringify(body)
    } else {
      requestInit.body = body
    }
  }

  return requestInit
}

function getResponseType(response) {
  const contentType = response.headers?.get?.('content-type') || ''
  const isJsonResponse = contentType.includes('application/json')
  const isNoContentStatus = response.status === 204 || response.status === 205
  return { isJsonResponse, isNoContentStatus }
}

async function readSuccessJson(response) {
  try {
    return await response.json()
  } catch (error) {
    return null
  }
}

async function readErrorJson(response) {
  try {
    return await response.json()
  } catch (error) {
    const normalized = normalizeApiError(null)
    throw new ApiError(normalized, response.status)
  }
}

function normalizeNetworkError(error) {
  if (error instanceof ApiError) {
    return error
  }

  return new ApiError(
    {
      code: 'NETWORK_ERROR',
      message: error instanceof Error ? error.message : '',
      details: undefined,
      requestId: undefined
    },
    0
  )
}

/**
 * Custom error class for API errors with normalized fields.
 */
export class ApiError extends Error {
  constructor(normalizedError, status) {
    super(typeof normalizedError.message === 'string' ? normalizedError.message : '')
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
   * @param {any} options.body - Request body
   * @param {object} options.headers - Additional headers
   * @returns {Promise<any>} Response data
   * @throws {ApiError} When response is not ok
   */
  async function request(method, path, options = {}) {
    const { body, headers = {} } = options

    const url = `${baseUrl}${path}`
    const requestInit = createRequestInit(method, body, headers)

    try {
      const response = await fetch(url, requestInit)
      const { isJsonResponse, isNoContentStatus } = getResponseType(response)

      if (isNoContentStatus) {
        return null
      }

      if (!response.ok) {
        const data = isJsonResponse ? await readErrorJson(response) : null
        const normalized = normalizeApiError(data)
        throw new ApiError(normalized, response.status)
      }

      if (isJsonResponse) {
        return readSuccessJson(response)
      }

      return response
    } catch (error) {
      // Network error or other fetch failure
      throw normalizeNetworkError(error)
    }
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
