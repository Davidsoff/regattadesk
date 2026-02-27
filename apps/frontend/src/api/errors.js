/**
 * Error normalization for OpenAPI error_response schema.
 * 
 * OpenAPI contract:
 * error_response:
 *   type: object
 *   required: [error]
 *   properties:
 *     error:
 *       type: object
 *       required: [code, message]
 *       properties:
 *         code: { type: string }
 *         message: { type: string }
 *         details: { type: object, additionalProperties: true }
 */

/**
 * Check if a response matches the OpenAPI error_response schema.
 * @param {any} response - Response to check
 * @returns {boolean} True if response has error field with object value
 */
export function isApiError(response) {
  return (
    response !== null &&
    typeof response === 'object' &&
    'error' in response &&
    typeof response.error === 'object' &&
    response.error !== null
  )
}

/**
 * Normalize an API error response to a consistent shape.
 * @param {any} response - API response (may be error_response or other)
 * @returns {object} Normalized error with code, message, details, requestId
 */
export function normalizeApiError(response) {
  if (!isApiError(response)) {
    return {
      code: 'UNKNOWN_ERROR',
      message: '',
      details: undefined,
      requestId: undefined
    }
  }

  const { error } = response
  const code = typeof error.code === 'string' ? error.code : 'UNKNOWN_ERROR'
  const message = typeof error.message === 'string' ? error.message : ''
  const details = error.details
  const requestId = response.request_id

  return {
    code,
    message,
    details,
    requestId
  }
}
