export const SUCCESS_MESSAGE_DURATION_MS = 3000

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

export function validateRouteParam(param, name) {
  const value = Array.isArray(param) ? param[0] : param

  if (typeof value !== 'string' || !UUID_PATTERN.test(value)) {
    return null
  }

  return value
}
