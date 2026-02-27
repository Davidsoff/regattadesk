/**
 * Draw and Bib Pool Management API
 * 
 * Provides methods for managing scheduling blocks and bib pool allocations.
 * 
 * @example
 * import { createApiClient, createDrawApi } from '@/api'
 * 
 * const client = createApiClient()
 * const drawApi = createDrawApi(client)
 * 
 * const blocks = await drawApi.listBlocks(regattaId)
 * await drawApi.createBlock(regattaId, {
 *   name: 'Morning Session',
 *   start_time: '09:00:00',
 *   event_interval_seconds: 120,
 *   crew_interval_seconds: 30
 * })
 */

/**
 * Create Draw API client for block and bib pool management
 * @param {Object} client - Base API client from createApiClient()
 * @returns {Object} Draw API methods
 */
export function createDrawApi(client) {
  return {
    // Block management methods
    
    /**
     * List all blocks for a regatta
     * @param {string} regattaId - Regatta ID
     * @returns {Promise<Object>} Response with blocks array
     */
    async listBlocks(regattaId) {
      return client.get(`/regattas/${regattaId}/blocks`)
    },

    /**
     * Create a new block
     * @param {string} regattaId - Regatta ID
     * @param {Object} payload - Block data (name, start_time, intervals, etc.)
     * @returns {Promise<Object>} Created block
     */
    async createBlock(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/blocks`, payload)
    },

    /**
     * Update an existing block
     * @param {string} regattaId - Regatta ID
     * @param {string} blockId - Block ID
     * @param {Object} payload - Updated block data
     * @returns {Promise<Object>} Updated block
     */
    async updateBlock(regattaId, blockId, payload) {
      return client.patch(`/regattas/${regattaId}/blocks/${blockId}`, payload)
    },

    /**
     * Delete a block
     * @param {string} regattaId - Regatta ID
     * @param {string} blockId - Block ID
     * @returns {Promise<void>}
     */
    async deleteBlock(regattaId, blockId) {
      return client.delete(`/regattas/${regattaId}/blocks/${blockId}`)
    },

    /**
     * Reorder blocks by display_order
     * @param {string} regattaId - Regatta ID
     * @param {Object} payload - Object with block_ids array in desired order
     * @returns {Promise<void>}
     */
    async reorderBlocks(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/blocks/reorder`, payload)
    },

    // Bib Pool management methods
    
    /**
     * List all bib pools for a regatta
     * @param {string} regattaId - Regatta ID
     * @returns {Promise<Object>} Response with bib_pools array
     */
    async listBibPools(regattaId) {
      return client.get(`/regattas/${regattaId}/bib_pools`)
    },

    /**
     * Create a new bib pool
     * @param {string} regattaId - Regatta ID
     * @param {Object} payload - Bib pool data (name, allocation_mode, range or list, etc.)
     * @returns {Promise<Object>} Created bib pool
     */
    async createBibPool(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/bib_pools`, payload)
    },

    /**
     * Update an existing bib pool
     * @param {string} regattaId - Regatta ID
     * @param {string} poolId - Bib pool ID
     * @param {Object} payload - Updated bib pool data
     * @returns {Promise<Object>} Updated bib pool
     */
    async updateBibPool(regattaId, poolId, payload) {
      return client.patch(`/regattas/${regattaId}/bib_pools/${poolId}`, payload)
    },

    /**
     * Delete a bib pool
     * @param {string} regattaId - Regatta ID
     * @param {string} poolId - Bib pool ID
     * @returns {Promise<void>}
     */
    async deleteBibPool(regattaId, poolId) {
      return client.delete(`/regattas/${regattaId}/bib_pools/${poolId}`)
    },

    /**
     * Reorder bib pools by priority
     * @param {string} regattaId - Regatta ID
     * @param {Object} payload - Object with bib_pool_ids array in desired priority order
     * @returns {Promise<void>}
     */
    async reorderBibPools(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/bib_pools/reorder`, payload)
    }
  }
}
