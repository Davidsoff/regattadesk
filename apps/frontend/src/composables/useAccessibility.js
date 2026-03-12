/**
 * Accessibility composables for RegattaDesk
 * 
 * Provides utilities for focus management, keyboard navigation,
 * and ARIA announcements aligned with WCAG 2.2 AA requirements.
 * 
 * Note: This file re-exports all accessibility utilities from their
 * individual files for backward compatibility.
 * Import directly from individual files for tree-shaking benefits:
 * - useFocusTrap from './useFocusTrap.js'
 * - useLiveAnnouncer from './useLiveAnnouncer.js'
 * - useArrowNavigation from './useArrowNavigation.js'
 * - createSkipLink from './useSkipLink.js'
 * - useFocusManagement from './useFocusManagement.js'
 */

export { useFocusTrap } from './useFocusTrap.js';
export { useLiveAnnouncer } from './useLiveAnnouncer.js';
export { useArrowNavigation } from './useArrowNavigation.js';
export { createSkipLink, focusTemporarily } from './useSkipLink.js';
export { useFocusManagement } from './useFocusManagement.js';
