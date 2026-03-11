/**
 * Storage utilities for RegattaDesk.
 *
 * This file lives in src/utils/ alongside other stateless pure utilities
 * (locale.js, jsonUtils.js). Functions here have no Vue lifecycle dependency
 * and no reactive state — they are plain functions safe to call in any context.
 * Vue-lifecycle-aware code belongs in src/composables/ instead.
 */

/**
 * Returns the localStorage instance if available, or null.
 * Works in SSR/test environments where window/localStorage may be absent.
 */
export function getStorage() {
  try {
    const storage = globalThis.window?.localStorage ?? globalThis.localStorage
    if (
      storage &&
      typeof storage.getItem === 'function' &&
      typeof storage.setItem === 'function'
    ) {
      return storage
    }
  } catch {
    return null
  }

  return null
}

export function getStorageValue(key, defaultValue = null) {
  const storage = getStorage()
  if (storage === null) {
    return defaultValue
  }

  const value = storage.getItem(key)
  if (value === null) {
    return defaultValue
  }

  return value
}
