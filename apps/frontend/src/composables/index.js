/**
 * Composables barrel export for RegattaDesk
 * 
 * Import individual composables for tree-shaking:
 * import { useFocusTrap } from '@/composables/useFocusTrap'
 * 
 * Or import from the barrel:
 * import { useFocusTrap, useLiveAnnouncer } from '@/composables'
 */

// Accessibility composables
export { useFocusTrap } from './useFocusTrap.js'
export { useLiveAnnouncer } from './useLiveAnnouncer.js'
export { useArrowNavigation } from './useArrowNavigation.js'
export { createSkipLink, focusTemporarily } from './useSkipLink.js'
export { useFocusManagement } from './useFocusManagement.js'
export { useAccessibility } from './useAccessibility.js'

// Feature composables
export { useDrawImmutability } from './useDrawImmutability.js'
export { useExportJob } from './useExportJob.js'
export { useFormatting } from './useFormatting.js'
export { useLocale } from './useLocale.js'
export { useOfflineQueue } from './useOfflineQueue.js'
export { useOfflineSync } from './useOfflineSync.js'
export { useOperatorTheme } from './useOperatorTheme.js'
export { usePWAInstall } from './usePWAInstall.js'
export { useServiceWorker } from './useServiceWorker.js'
export { useSseReconnect } from './useSseReconnect.js'
export { useUserRole } from './useUserRole.js'

// Validation utilities
export {
  isBibPoolValidationError,
  parseBibPoolValidationError,
  formatOverlappingBibs,
  createValidationErrorMessage,
} from './bibPoolValidation.js'
