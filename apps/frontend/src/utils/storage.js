/**
 * Returns the localStorage instance if available, or null.
 * Works in SSR/test environments where window/localStorage may be absent.
 */
export function getStorage() {
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
