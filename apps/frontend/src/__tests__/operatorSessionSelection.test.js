import { beforeEach, describe, expect, it } from 'vitest'
import {
  SELECTED_CAPTURE_SESSIONS_STORAGE_KEY,
  clearSelectedCaptureSessionId,
  getSelectedCaptureSessionId,
  setSelectedCaptureSessionId
} from '../operatorSessionSelection'

function installStorage() {
  const values = new Map()
  const storage = {
    getItem(key) {
      return values.has(key) ? values.get(key) : null
    },
    setItem(key, value) {
      values.set(key, String(value))
    },
    removeItem(key) {
      values.delete(key)
    },
    clear() {
      values.clear()
    }
  }

  if (globalThis.window) {
    Object.defineProperty(globalThis.window, 'localStorage', {
      value: storage,
      configurable: true
    })
  }

  Object.defineProperty(globalThis, 'localStorage', {
    value: storage,
    configurable: true
  })

  return storage
}

describe('operatorSessionSelection', () => {
  beforeEach(() => {
    installStorage().clear()
  })

  it('normalizes regatta and session ids for storage, lookup, and clearing', () => {
    setSelectedCaptureSessionId(' regatta-138 ', ' session-138 ')

    expect(getSelectedCaptureSessionId('regatta-138')).toBe('session-138')
    expect(JSON.parse(globalThis.localStorage.getItem(SELECTED_CAPTURE_SESSIONS_STORAGE_KEY))).toEqual({
      'regatta-138': 'session-138'
    })

    clearSelectedCaptureSessionId('regatta-138 ', ' session-138')

    expect(getSelectedCaptureSessionId('regatta-138')).toBeNull()
    expect(JSON.parse(globalThis.localStorage.getItem(SELECTED_CAPTURE_SESSIONS_STORAGE_KEY))).toEqual({})
  })
})
