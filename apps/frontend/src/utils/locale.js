/**
 * Locale utilities for RegattaDesk.
 *
 * This file lives in src/utils/ alongside other stateless pure utilities
 * (storage.js, jsonUtils.js). Functions here have no Vue lifecycle dependency
 * and no reactive state — they are plain functions safe to call in any context.
 * Vue-lifecycle-aware code belongs in src/composables/ instead.
 */

/**
 * Normalize a locale string to a supported locale code ('en' or 'nl').
 * Returns null if the value cannot be mapped to a supported locale.
 *
 * @param {unknown} value - Raw locale string (e.g. 'nl-NL', 'en-US', 'EN')
 * @returns {'en' | 'nl' | null}
 */
export function normalizeLocale(value) {
  if (typeof value !== 'string') {
    return null
  }

  const normalizedValue = value.trim()
  if (normalizedValue.length === 0) {
    return null
  }

  const baseLanguage = normalizedValue.toLowerCase().split(/[-_]/)[0]
  if (baseLanguage === 'nl') {
    return 'nl'
  }
  if (baseLanguage === 'en') {
    return 'en'
  }
  return null
}
