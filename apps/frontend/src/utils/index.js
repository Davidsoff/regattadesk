/**
 * Utils barrel export for RegattaDesk
 * 
 * Import individual utilities for tree-shaking:
 * import { safeJsonParse } from '@/utils/jsonUtils'
 * 
 * Or import from the barrel:
 * import { safeJsonParse, normalizeLocale } from '@/utils'
 */

export { safeJsonParse } from './jsonUtils.js'
export { normalizeLocale, formatDate, formatTime, getLocale } from './locale.js'
export { getStorage, setStorageItem, getStorageItem } from './storage.js'
