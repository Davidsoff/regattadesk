/**
 * Draw API - Rulesets, Blocks, Bib Pools, and Draw Operations
 * 
 * Provides API methods for BC04 Rules, Scheduling, and Draw management:
 * - Ruleset CRUD and promotion
 * - Block timing configuration
 * - Bib pool allocation
 * - Draw generation and publication
 * 
 * @module api/draw
 */

/**
 * Create draw API instance.
 * @param {object} client - API client instance
 * @returns {object} Draw API methods
 */
export function createDrawApi(client) {
  return {
    // ==================== Rulesets ====================

    /**
     * List rulesets with optional global filter.
     * @param {object} options - Query options
     * @param {boolean} options.is_global - Filter by global status
     * @returns {Promise<object>} Ruleset list response
     */
    async listRulesets(options = {}) {
      const params = new URLSearchParams()
      if (options.is_global !== undefined) {
        params.append('is_global', options.is_global)
      }
      const query = params.toString()
      const url = query ? `/rulesets?${query}` : '/rulesets'
      return client.get(url)
    },

    /**
     * Get a single ruleset by ID.
     * @param {string} rulesetId - Ruleset UUID
     * @returns {Promise<object>} Ruleset
     */
    async getRuleset(rulesetId) {
      return client.get(`/rulesets/${rulesetId}`)
    },

    /**
     * Create a new ruleset.
     * @param {object} payload - Ruleset data
     * @param {string} payload.name - Ruleset name
     * @param {string} payload.version - Ruleset version
     * @param {string} payload.description - Optional description
     * @param {string} payload.age_calculation_type - 'actual_at_start' or 'age_as_of_jan_1'
     * @returns {Promise<object>} Created ruleset
     */
    async createRuleset(payload) {
      return client.post('/rulesets', payload)
    },

    /**
     * Update an existing ruleset.
     * @param {string} rulesetId - Ruleset UUID
     * @param {object} payload - Fields to update
     * @returns {Promise<object>} Updated ruleset
     */
    async updateRuleset(rulesetId, payload) {
      return client.patch(`/rulesets/${rulesetId}`, payload)
    },

    /**
     * Duplicate a ruleset with new name and version.
     * @param {string} rulesetId - Ruleset UUID to duplicate
     * @param {object} payload - Duplication parameters
     * @param {string} payload.new_name - New ruleset name
     * @param {string} payload.new_version - New ruleset version
     * @returns {Promise<object>} Duplicated ruleset
     */
    async duplicateRuleset(rulesetId, payload) {
      return client.post(`/rulesets/${rulesetId}/duplicate`, payload)
    },

    /**
     * Promote a regatta-owned ruleset to global (super_admin only).
     * @param {string} rulesetId - Ruleset UUID
     * @returns {Promise<object>} Promoted ruleset with is_global: true
     * @throws {ApiError} 403 if user is not super_admin
     */
    async promoteRuleset(rulesetId) {
      return client.post(`/rulesets/${rulesetId}/promote`)
    },

    // ==================== Blocks ====================

    /**
     * List blocks for a regatta.
     * @param {string} regattaId - Regatta UUID
     * @returns {Promise<object>} Block list response
     */
    async listBlocks(regattaId) {
      return client.get(`/regattas/${regattaId}/blocks`)
    },

    /**
     * Create a new block.
     * @param {string} regattaId - Regatta UUID
     * @param {object} payload - Block data
     * @param {string} payload.name - Block name
     * @param {string} payload.start_time - ISO 8601 start time
     * @param {number} payload.event_interval_seconds - Interval between events
     * @param {number} payload.crew_interval_seconds - Interval between crews
     * @param {number} payload.display_order - Optional display order
     * @returns {Promise<object>} Created block
     */
    async createBlock(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/blocks`, payload)
    },

    /**
     * Update an existing block.
     * @param {string} regattaId - Regatta UUID
     * @param {string} blockId - Block UUID
     * @param {object} payload - Fields to update
     * @returns {Promise<object>} Updated block
     */
    async updateBlock(regattaId, blockId, payload) {
      return client.patch(`/regattas/${regattaId}/blocks/${blockId}`, payload)
    },

    /**
     * Delete a block.
     * @param {string} regattaId - Regatta UUID
     * @param {string} blockId - Block UUID
     * @returns {Promise<null>} No content on success
     */
    async deleteBlock(regattaId, blockId) {
      return client.delete(`/regattas/${regattaId}/blocks/${blockId}`)
    },

    /**
     * Reorder blocks by display_order.
     * @param {string} regattaId - Regatta UUID
     * @param {object} payload - Reorder request
     * @param {Array<{block_id: string, display_order: number}>} payload.items - Ordered blocks
     * @returns {Promise<object>} Block list response
     */
    async reorderBlocks(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/blocks/reorder`, payload)
    },

    // ==================== Bib Pools ====================

    /**
     * List bib pools for a regatta.
     * @param {string} regattaId - Regatta UUID
     * @returns {Promise<object>} Bib pool list response
     */
    async listBibPools(regattaId) {
      return client.get(`/regattas/${regattaId}/bib_pools`)
    },

    /**
     * Create a new bib pool.
     * @param {string} regattaId - Regatta UUID
     * @param {object} payload - Bib pool data
     * @param {string} payload.block_id - Block UUID (null for overflow pool)
     * @param {string} payload.name - Pool name
     * @param {string} payload.allocation_mode - 'range' or 'explicit_list'
     * @param {number} payload.start_bib - Range start (required for 'range' mode)
     * @param {number} payload.end_bib - Range end (required for 'range' mode)
     * @param {number[]} payload.bib_numbers - Explicit bib numbers (required for 'explicit_list' mode)
     * @param {number} payload.priority - Pool priority
     * @param {boolean} payload.is_overflow - Overflow pool flag
     * @returns {Promise<object>} Created bib pool
     * @throws {ApiError} 400 with code BIB_POOL_VALIDATION_ERROR if bibs overlap
     */
    async createBibPool(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/bib_pools`, payload)
    },

    /**
     * Update an existing bib pool.
     * @param {string} regattaId - Regatta UUID
     * @param {string} poolId - Bib pool UUID
     * @param {object} payload - Fields to update
     * @returns {Promise<object>} Updated bib pool
     */
    async updateBibPool(regattaId, poolId, payload) {
      return client.patch(`/regattas/${regattaId}/bib_pools/${poolId}`, payload)
    },

    /**
     * Delete a bib pool.
     * @param {string} regattaId - Regatta UUID
     * @param {string} poolId - Bib pool UUID
     * @returns {Promise<null>} No content on success
     */
    async deleteBibPool(regattaId, poolId) {
      return client.delete(`/regattas/${regattaId}/bib_pools/${poolId}`)
    },

    /**
     * Reorder bib pools by priority.
     * @param {string} regattaId - Regatta UUID
     * @param {object} payload - Reorder request
     * @param {Array<{bib_pool_id: string, priority: number}>} payload.items - Ordered pools
     * @returns {Promise<object>} Bib pool list response
     */
    async reorderBibPools(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/bib_pools/reorder`, payload)
    },

    // ==================== Draw Operations ====================

    /**
     * Generate draw with optional custom seed.
     * @param {string} regattaId - Regatta UUID
     * @param {object} payload - Optional generation parameters
     * @param {string} payload.seed - Custom seed for reproducibility
     * @returns {Promise<object>} Revision response with seed
     */
    async generateDraw(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/draw/generate`, payload || undefined)
    },

    /**
     * Publish draw and increment draw_revision.
     * @param {string} regattaId - Regatta UUID
     * @returns {Promise<object>} Revision response with updated draw_revision
     */
    async publishDraw(regattaId) {
      return client.post(`/regattas/${regattaId}/draw/publish`)
    },

    /**
     * Unpublish draw (allows re-generation).
     * @param {string} regattaId - Regatta UUID
     * @returns {Promise<object>} Revision response
     */
    async unpublishDraw(regattaId) {
      return client.post(`/regattas/${regattaId}/draw/unpublish`)
    }
  }
}
