const OPERATOR_TOKEN_STORAGE_KEY = 'rd_operator_token'
const OPERATOR_STATION_STORAGE_KEY = 'rd_operator_station'
const OPERATOR_DEVICE_ID_STORAGE_KEY = 'rd_operator_device_id'

function getStorage() {
  const storage = globalThis.window?.localStorage ?? globalThis.localStorage
  if (
    storage &&
    typeof storage.getItem === 'function' &&
    typeof storage.setItem === 'function'
  ) {
    return storage
  }

  return null
}

function readContextValue(key) {
  return typeof globalThis.__REGATTADESK_AUTH__?.[key] === 'string'
    ? globalThis.__REGATTADESK_AUTH__[key].trim()
    : ''
}

export function resolveOperatorToken() {
  const contextToken = readContextValue('operatorToken')
  if (contextToken) {
    return contextToken
  }

  return getStorage()?.getItem(OPERATOR_TOKEN_STORAGE_KEY)?.trim() ?? ''
}

export function resolveOperatorStation() {
  const contextStation = readContextValue('operatorStation')
  if (contextStation) {
    return contextStation
  }

  return getStorage()?.getItem(OPERATOR_STATION_STORAGE_KEY)?.trim() ?? 'finish-line'
}

export function resolveOperatorDeviceId() {
  const storage = getStorage()
  if (!storage) {
    return 'operator-device'
  }

  const existing = (storage.getItem(OPERATOR_DEVICE_ID_STORAGE_KEY) || '').trim()
  if (existing) {
    return existing
  }

  const generated =
    typeof globalThis.crypto?.randomUUID === 'function'
      ? globalThis.crypto.randomUUID()
      : 'operator-device'
  storage.setItem(OPERATOR_DEVICE_ID_STORAGE_KEY, generated)
  return generated
}
