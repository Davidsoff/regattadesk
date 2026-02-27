/**
 * Finance API module for BC08 Finance and Payments.
 * 
 * Provides typed access to finance endpoints:
 * - Bulk payment marking
 * - Payment status updates
 */

/**
 * Create a finance API module.
 * @param {object} client - API client instance
 * @returns {object} Finance API methods
 */
export function createFinanceApi(client) {
  return {
    /**
     * Mark bulk payment status for entries or clubs.
     * 
     * @param {string} regattaId - Regatta UUID
     * @param {object} payload - Bulk payment payload
     * @param {string[]} payload.entry_ids - Optional entry UUIDs
     * @param {string[]} payload.club_ids - Optional club UUIDs
     * @param {string} payload.payment_status - Target status ('paid' or 'unpaid')
     * @param {string} payload.payment_reference - Optional reference
     * @param {string} payload.idempotency_key - Optional idempotency key
     * @returns {Promise<object>} Bulk operation result
     */
    async markBulkPayment(regattaId, payload) {
      // Extract idempotency_key from payload if present
      const options = payload.idempotency_key 
        ? { idempotencyKey: payload.idempotency_key }
        : undefined

      return client.post(`/regattas/${regattaId}/payments/mark_bulk`, payload, options)
    }
  }
}
