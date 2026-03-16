/**
 * RegattaDesk API Client
 * 
 * Centralized API client for all frontend surfaces.
 * Provides consistent error handling, idempotency support,
 * and typed access to backend contracts.
 * 
 * @example
 * import { createApiClient, createFinanceApi } from '@/api'
 * 
 * const client = createApiClient()
 * const financeApi = createFinanceApi(client)
 * 
 * try {
 *   const result = await financeApi.markBulkPayment(regattaId, payload)
 *   console.log(result)
 * } catch (error) {
 *   if (error.code === 'VALIDATION_ERROR') {
 *     console.error('Validation failed:', error.message)
 *   }
 * }
 */

export { createApiClient, ApiError } from './client.js'
export { normalizeApiError, isApiError } from './errors.js'
export { createAdjudicationApi } from './adjudication.js'
export { createFinanceApi } from './finance.js'
export { createOperatorApi } from './operator.js'
export { createStaffOperatorAccessApi } from './staffOperatorAccess.js'
export { createDrawApi } from './draw.js'
export { createExportApi } from './export.js'
