import { createClient as createGeneratedClient } from './generated/client/client.gen'
import { normalizeApiError } from './errors.js'

function normalizeUnknownError(error) {
  const normalized = normalizeApiError(error)
  if (normalized.code !== 'UNKNOWN_ERROR' || normalized.message) {
    return normalized
  }

  if (error instanceof Error) {
    return {
      code: 'NETWORK_ERROR',
      message: error.message,
      details: undefined,
      requestId: undefined
    }
  }

  if (error && typeof error === 'object') {
    const code = typeof error.code === 'string' ? error.code : 'UNKNOWN_ERROR'
    const message = typeof error.message === 'string' ? error.message : ''
    return {
      code,
      message,
      details: error.details,
      requestId: error.request_id
    }
  }

  return {
    code: 'UNKNOWN_ERROR',
    message: '',
    details: undefined,
    requestId: undefined
  }
}

function getResponseStatus(response) {
  if (response && typeof response.status === 'number') {
    return response.status
  }
  return 0
}

function hasOwn(value, key) {
  return Object.prototype.hasOwnProperty.call(value, key)
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
  const generatedClient = createGeneratedClient({
    baseUrl,
    responseStyle: 'fields',
    throwOnError: false
  })

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
    const { body, headers = {}, params, query, ...requestOptions } = options

    try {
      const result = await generatedClient.request({
        method,
        url: path,
        body,
        headers,
        query: query ?? params,
        ...requestOptions
      })

      if (!result || typeof result !== 'object') {
        throw new ApiError(
          {
            code: 'UNKNOWN_ERROR',
            message: 'Malformed API client response',
            details: undefined,
            requestId: undefined
          },
          0
        )
      }

      const status = getResponseStatus(result.response)

      if (hasOwn(result, 'error') && result.error !== undefined && result.error !== null) {
        throw new ApiError(normalizeUnknownError(result.error), status)
      }

      if (status === 204 || status === 205) {
        return null
      }

      if (!hasOwn(result, 'data') || result.data === undefined) {
        return null
      }

      return result.data
    } catch (error) {
      if (error instanceof ApiError) {
        throw error
      }
      throw new ApiError(normalizeUnknownError(error), 0)
    }
  }

  return {
    generatedClient,
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
     * Make a PUT request.
     * @param {string} path - Request path
     * @param {object} body - Request body
     * @param {object} options - Request options
     * @returns {Promise<object>} Response data
     */
    put(path, body, options) {
      return request('PUT', path, { ...options, body })
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
