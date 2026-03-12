import { afterEach, describe, expect, it, vi } from 'vitest'

import { getStorage } from '../../utils/storage'

describe('getStorage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('returns localStorage when it is available', () => {
    const storage = {
      getItem: vi.fn(),
      setItem: vi.fn()
    }

    vi.stubGlobal('localStorage', storage)

    expect(getStorage()).toBe(storage)
  })

  it('returns null when reading localStorage throws', () => {
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      get() {
        throw new Error('storage blocked')
      }
    })

    expect(getStorage()).toBeNull()
  })
})
