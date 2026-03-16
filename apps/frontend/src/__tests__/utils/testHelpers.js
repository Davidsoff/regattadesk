/**
 * Shared test utility helpers for RegattaDesk frontend tests.
 */

/**
 * Build a mock fetch Response with JSON content-type.
 * @param {number} status - HTTP status code
 * @param {*} body - Response body (will be JSON-serialized)
 * @returns {Response}
 */
export function jsonResponse(status, body) {
  return new Response(body === null || body === undefined ? '' : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

/**
 * Extract the first Request object from a vi.fn() fetch mock.
 * @param {import('vitest').MockInstance} fetchMock
 * @returns {Request}
 */
export function getRequest(fetchMock) {
  const [request] = fetchMock.mock.calls[0]
  return request
}

/**
 * Extract any Request object by index from a vi.fn() fetch mock.
 * @param {import('vitest').MockInstance} fetchMock
 * @param {number} index
 * @returns {Request}
 */
export function getRequestAt(fetchMock, index = 0) {
  const [request] = fetchMock.mock.calls[index]
  return request
}
