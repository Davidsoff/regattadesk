/**
 * Export API module for async print job workflows.
 * 
 * Provides typed access to export and job endpoints:
 * - Request printable exports
 * - Poll job status
 * - Download completed exports
 */

/**
 * Create an export API module.
 * @param {object} client - API client instance
 * @returns {object} Export API methods
 */
export function createExportApi(client) {
  return {
    /**
     * Request a printable export job for a regatta.
     * 
     * @param {string} regattaId - Regatta UUID
     * @returns {Promise<object>} Job creation response with job_id
     */
    async requestPrintableExport(regattaId) {
      return client.post(`/regattas/${regattaId}/export/printables`, {})
    },

    /**
     * Get the status of an async job.
     * 
     * @param {string} jobId - Job UUID
     * @returns {Promise<object>} Job status response
     */
    async getJobStatus(jobId) {
      return client.get(`/jobs/${jobId}`)
    }
  }
}
