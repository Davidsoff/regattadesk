/**
 * Draw immutability utilities for RegattaDesk
 * 
 * Provides helper functions to check draw publication state and
 * determine whether data can be edited based on immutability constraints.
 * 
 * Per BC04 requirements:
 * - Blocks are immutable after draw publication (draw_revision > 0)
 * - Bib pools are immutable after draw publication
 * - Rulesets cannot be changed after draw publication
 * - To restore editability, draw must be unpublished via DrawWorkflow
 * 
 * Usage:
 * import { isDrawPublished, canEditAfterDraw, getImmutabilityMessage } from '@/composables/useDrawImmutability'
 * 
 * if (isDrawPublished(regatta)) {
 *   console.log(getImmutabilityMessage(regatta, 'block'))
 * }
 */

/**
 * Check if draw has been published
 * 
 * Draw is considered published when draw_revision > 0.
 * This triggers immutability constraints on blocks, bib pools, and rulesets.
 * 
 * @param {Object|null|undefined} regatta - Regatta object with draw_revision
 * @returns {boolean} True if draw is published
 */
export function isDrawPublished(regatta) {
  if (!regatta || typeof regatta.draw_revision !== 'number') {
    return false
  }
  
  return regatta.draw_revision > 0
}

/**
 * Check if resource can be edited after draw publication
 * 
 * Inverse of isDrawPublished - returns true when editing is allowed.
 * 
 * @param {Object|null|undefined} regatta - Regatta object with draw_revision
 * @returns {boolean} True if editing is allowed
 */
export function canEditAfterDraw(regatta) {
  return !isDrawPublished(regatta)
}

/**
 * Get immutability warning message
 * 
 * Returns a user-friendly message explaining why editing is disabled
 * and how to restore editability.
 * 
 * @param {Object|null|undefined} regatta - Regatta object with draw_revision
 * @param {string} resourceType - Type of resource (e.g., 'block', 'bib pool')
 * @returns {string|null} Warning message or null if editing is allowed
 */
export function getImmutabilityMessage(regatta, resourceType = 'this resource') {
  if (!isDrawPublished(regatta)) {
    return null
  }
  
  return `Cannot edit ${resourceType} after draw publication. Use Unpublish in Draw Workflow to restore editability.`
}

/**
 * Composable for reactive immutability state
 * 
 * Usage in components:
 * const { isPublished, canEdit, getMessage } = useDrawImmutability(regattaRef)
 * 
 * @param {Ref<Object>} regattaRef - Reactive reference to regatta
 * @returns {Object} Immutability state helpers
 */
export function useDrawImmutability(regattaRef) {
  const { computed } = require('vue')
  
  const isPublished = computed(() => isDrawPublished(regattaRef.value))
  const canEdit = computed(() => canEditAfterDraw(regattaRef.value))
  
  const getMessage = (resourceType) => {
    return getImmutabilityMessage(regattaRef.value, resourceType)
  }
  
  return {
    isPublished,
    canEdit,
    getMessage
  }
}
