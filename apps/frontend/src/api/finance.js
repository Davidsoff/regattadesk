/**
 * Finance API module for BC08 Finance and Payments.
 * 
 * Provides typed access to finance endpoints:
 * - Bulk payment marking
 * - Payment status updates (entry and club)
 * - Invoice operations
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
      return client.post(`/regattas/${regattaId}/payments/mark_bulk`, payload)
    },

    /**
     * Get payment status for a specific entry.
     * 
     * @param {string} regattaId - Regatta UUID
     * @param {string} entryId - Entry UUID
     * @returns {Promise<object>} Entry payment status
     */
    async getEntryPaymentStatus(regattaId, entryId) {
      return client.get(`/regattas/${regattaId}/entries/${entryId}/payment_status`)
    },

    /**
     * Update payment status for a specific entry.
     * 
     * @param {string} regattaId - Regatta UUID
     * @param {string} entryId - Entry UUID
     * @param {object} payload - Payment status update
     * @param {string} payload.payment_status - Target status ('paid' or 'unpaid')
     * @param {string} payload.payment_reference - Optional reference
     * @returns {Promise<object>} Updated entry payment status
     */
    async updateEntryPaymentStatus(regattaId, entryId, payload) {
      return client.put(`/regattas/${regattaId}/entries/${entryId}/payment_status`, payload)
    },

    /**
     * Get derived payment status for a club.
     * 
     * @param {string} regattaId - Regatta UUID
     * @param {string} clubId - Club UUID
     * @returns {Promise<object>} Club payment status summary
     */
    async getClubPaymentStatus(regattaId, clubId) {
      return client.get(`/regattas/${regattaId}/clubs/${clubId}/payment_status`)
    },

    /**
     * Update payment status for all billable entries of a club.
     * 
     * @param {string} regattaId - Regatta UUID
     * @param {string} clubId - Club UUID
     * @param {object} payload - Payment status update
     * @param {string} payload.payment_status - Target status ('paid' or 'unpaid')
     * @param {string} payload.payment_reference - Optional reference
     * @returns {Promise<object>} Bulk update result for club
     */
    async updateClubPaymentStatus(regattaId, clubId, payload) {
      return client.put(`/regattas/${regattaId}/clubs/${clubId}/payment_status`, payload)
    },

    /**
     * List invoices for a regatta.
     * 
     * @param {string} regattaId - Regatta UUID
     * @param {object} params - Optional pagination parameters
     * @param {number} params.limit - Maximum results per page
     * @param {string} params.cursor - Pagination cursor
     * @returns {Promise<object>} Invoice list with pagination
     */
    async listInvoices(regattaId, params = {}) {
      return client.get(`/regattas/${regattaId}/invoices`, { params })
    },

    /**
     * Get details of a specific invoice.
     * 
     * @param {string} regattaId - Regatta UUID
     * @param {string} invoiceId - Invoice UUID
     * @returns {Promise<object>} Invoice details
     */
    async getInvoice(regattaId, invoiceId) {
      return client.get(`/regattas/${regattaId}/invoices/${invoiceId}`)
    },

    /**
     * Generate invoices for unpaid entries.
     * 
     * @param {string} regattaId - Regatta UUID
     * @returns {Promise<object>} Job creation response
     */
    async generateInvoices(regattaId) {
      return client.post(`/regattas/${regattaId}/invoices/generate`, {})
    },

    /**
     * Mark an invoice as paid.
     * 
     * @param {string} regattaId - Regatta UUID
     * @param {string} invoiceId - Invoice UUID
     * @param {object} payload - Optional payment details
     * @param {string} payload.payment_reference - Optional reference
     * @param {string} payload.paid_at - Optional ISO timestamp
     * @returns {Promise<object>} Updated invoice
     */
    async markInvoicePaid(regattaId, invoiceId, payload = {}) {
      return client.post(`/regattas/${regattaId}/invoices/${invoiceId}/mark_paid`, payload)
    }
  }
}
