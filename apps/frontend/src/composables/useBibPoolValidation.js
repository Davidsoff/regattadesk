/**
 * Bib pool validation utilities for RegattaDesk
 * 
 * Provides helper functions to parse and display bib pool validation errors,
 * specifically handling overlap detection between bib pools.
 * 
 * Usage:
 * import { parseBibPoolValidationError, formatOverlappingBibs } from '@/composables/useBibPoolValidation'
 * 
 * try {
 *   await saveBibPool(data)
 * } catch (error) {
 *   const validationError = parseBibPoolValidationError(error)
 *   if (validationError) {
 *     const bibsText = formatOverlappingBibs(validationError.overlappingBibs)
 *     console.log(`Bibs ${bibsText} overlap with ${validationError.conflictingPoolName}`)
 *   }
 * }
 */

/**
 * Check if error is a bib pool validation error
 * 
 * @param {Error|Object|null|undefined} error - Error object from API
 * @returns {boolean} True if error is BIB_POOL_VALIDATION_ERROR
 */
export function isBibPoolValidationError(error) {
  return error?.code === 'BIB_POOL_VALIDATION_ERROR'
}

/**
 * Parse bib pool validation error response
 * 
 * Extracts validation details including overlapping bib numbers
 * and conflicting pool information.
 * 
 * @param {Error|Object|null} error - Error object from API
 * @returns {Object|null} Parsed validation error or null
 */
export function parseBibPoolValidationError(error) {
  if (!isBibPoolValidationError(error)) {
    return null
  }

  const details = error.details || {}

  return {
    message: error.message || 'Bib pool validation failed',
    overlappingBibs: Array.isArray(details.overlapping_bibs) 
      ? details.overlapping_bibs 
      : [],
    conflictingPoolId: details.conflicting_pool_id || null,
    conflictingPoolName: details.conflicting_pool_name || null
  }
}

/**
 * Format overlapping bib numbers for display
 * 
 * Sorts bib numbers numerically and formats as comma-separated string.
 * Removes duplicates and handles edge cases.
 * 
 * @param {Array<number>|null|undefined} bibs - Array of bib numbers
 * @returns {string} Formatted bib numbers (e.g., "50, 51, 52, 100")
 */
export function formatOverlappingBibs(bibs) {
  if (!Array.isArray(bibs) || bibs.length === 0) {
    return ''
  }

  // Remove duplicates using Set, sort numerically, and join
  const uniqueBibs = [...new Set(bibs)]
  const sortedBibs = [...uniqueBibs].sort((a, b) => a - b)
  
  return sortedBibs.join(', ')
}

/**
 * Create user-friendly validation error message
 * 
 * @param {Object} validationError - Parsed validation error
 * @returns {string} User-friendly error message
 */
export function createValidationErrorMessage(validationError) {
  if (!validationError) {
    return 'Validation error'
  }

  const bibsText = formatOverlappingBibs(validationError.overlappingBibs)
  const poolName = validationError.conflictingPoolName || 'another pool'

  if (bibsText) {
    return `Bib numbers ${bibsText} overlap with ${poolName}. Change range or delete conflicting pool.`
  }

  return validationError.message
}
