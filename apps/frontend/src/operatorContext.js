import { getStorage } from './utils/storage.js'

const OPERATOR_TOKEN_STORAGE_KEY = 'rd_operator_token'
const OPERATOR_STATION_STORAGE_KEY = 'rd_operator_station'
const OPERATOR_DEVICE_ID_STORAGE_KEY = 'rd_operator_device_id'
const OPERATOR_BLOCK_ID_STORAGE_KEY = 'rd_operator_block_id'

function readContextValue(key) {
  const value = globalThis.__REGATTADESK_AUTH__?.[key]
  return typeof value === 'string' ? value.trim() : ''
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

export function resolveOperatorBlockId() {
  const contextBlockId = readContextValue('operatorBlockId')
  if (contextBlockId) {
    return contextBlockId
  }

  return getStorage()?.getItem(OPERATOR_BLOCK_ID_STORAGE_KEY)?.trim() ?? ''
}
