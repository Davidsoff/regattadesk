import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createDrawApi } from '../api/draw'

/**
 * Integration tests for draw lifecycle workflow
 * 
 * These tests validate the complete workflow from ruleset setup through
 * draw generation and publication, ensuring immutability constraints are respected.
 */
describe('Draw Lifecycle Integration', () => {
  let api
  let mockClient
  const regattaId = 'regatta-123'

  beforeEach(() => {
    mockClient = {
      get: vi.fn(),
      post: vi.fn(),
      patch: vi.fn(),
      delete: vi.fn()
    }
    api = createDrawApi(mockClient)
  })

  describe('Complete draw workflow', () => {
    it('supports full workflow: create ruleset -> setup blocks/bib pools -> generate -> publish', async () => {
      // Step 1: Create a ruleset
      const rulesetPayload = {
        name: 'Test Rules',
        version: '2024',
        age_calculation_type: 'actual_at_start'
      }
      const createdRuleset = {
        id: 'ruleset-1',
        ...rulesetPayload,
        is_global: false
      }
      mockClient.post.mockResolvedValueOnce(createdRuleset)
      const ruleset = await api.createRuleset(rulesetPayload)
      expect(ruleset.id).toBe('ruleset-1')

      // Step 2: Create a block
      const blockPayload = {
        name: 'Morning Session',
        start_time: '2024-08-15T09:00:00Z',
        event_interval_seconds: 300,
        crew_interval_seconds: 60
      }
      const createdBlock = {
        id: 'block-1',
        regatta_id: regattaId,
        ...blockPayload,
        display_order: 1
      }
      mockClient.post.mockResolvedValueOnce(createdBlock)
      const block = await api.createBlock(regattaId, blockPayload)
      expect(block.id).toBe('block-1')

      // Step 3: Create a bib pool
      const bibPoolPayload = {
        block_id: 'block-1',
        name: 'Pool 1-100',
        allocation_mode: 'range',
        start_bib: 1,
        end_bib: 100,
        priority: 1,
        is_overflow: false
      }
      const createdPool = {
        id: 'pool-1',
        regatta_id: regattaId,
        ...bibPoolPayload,
        bib_numbers: null
      }
      mockClient.post.mockResolvedValueOnce(createdPool)
      const pool = await api.createBibPool(regattaId, bibPoolPayload)
      expect(pool.id).toBe('pool-1')

      // Step 4: Generate draw
      mockClient.post.mockResolvedValueOnce({
        draw_revision: 1,
        results_revision: 0,
        seed: 'generated-seed'
      })
      const drawGenerated = await api.generateDraw(regattaId)
      expect(drawGenerated.draw_revision).toBe(1)
      expect(drawGenerated.seed).toBeTruthy()

      // Step 5: Publish draw (increments draw_revision)
      mockClient.post.mockResolvedValueOnce({
        draw_revision: 2,
        results_revision: 0
      })
      const published = await api.publishDraw(regattaId)
      expect(published.draw_revision).toBe(2)

      // Verify all API calls were made in correct order
      expect(mockClient.post).toHaveBeenCalledTimes(5)
      expect(mockClient.post).toHaveBeenNthCalledWith(1, '/rulesets', rulesetPayload)
      expect(mockClient.post).toHaveBeenNthCalledWith(2, `/regattas/${regattaId}/blocks`, blockPayload)
      expect(mockClient.post).toHaveBeenNthCalledWith(3, `/regattas/${regattaId}/bib_pools`, bibPoolPayload)
      expect(mockClient.post).toHaveBeenNthCalledWith(4, `/regattas/${regattaId}/draw/generate`)
      expect(mockClient.post).toHaveBeenNthCalledWith(5, `/regattas/${regattaId}/draw/publish`)
    })

    it('allows unpublishing draw to enable regeneration', async () => {
      // Publish draw first
      mockClient.post.mockResolvedValueOnce({
        draw_revision: 2,
        results_revision: 0
      })
      await api.publishDraw(regattaId)

      // Unpublish to allow regeneration
      mockClient.post.mockResolvedValueOnce({
        draw_revision: 1,
        results_revision: 0
      })
      const unpublished = await api.unpublishDraw(regattaId)
      expect(unpublished.draw_revision).toBe(1)

      // Can now regenerate with new seed
      mockClient.post.mockResolvedValueOnce({
        draw_revision: 1,
        results_revision: 0,
        seed: 'new-seed'
      })
      const regenerated = await api.generateDraw(regattaId, { seed: 'new-seed' })
      expect(regenerated.seed).toBe('new-seed')
    })

    it('allows reproducible draw with custom seed', async () => {
      const customSeed = 'reproducible-seed-12345'
      mockClient.post.mockResolvedValue({
        draw_revision: 1,
        results_revision: 0,
        seed: customSeed
      })

      const result = await api.generateDraw(regattaId, { seed: customSeed })
      
      expect(result.seed).toBe(customSeed)
      expect(mockClient.post).toHaveBeenCalledWith(
        `/regattas/${regattaId}/draw/generate`,
        { seed: customSeed }
      )
    })
  })

  describe('Super admin ruleset promotion', () => {
    it('promotes regatta-owned ruleset to global', async () => {
      const rulesetId = 'ruleset-1'
      mockClient.post.mockResolvedValue({
        id: rulesetId,
        name: 'Promoted Rules',
        version: '2024',
        age_calculation_type: 'actual_at_start',
        is_global: true
      })

      const promoted = await api.promoteRuleset(rulesetId)

      expect(promoted.is_global).toBe(true)
      expect(mockClient.post).toHaveBeenCalledWith(`/rulesets/${rulesetId}/promote`)
    })
  })

  describe('Bib pool overlap validation', () => {
    it('creates non-overlapping bib pools successfully', async () => {
      // Pool 1: 1-100
      mockClient.post.mockResolvedValueOnce({
        id: 'pool-1',
        regatta_id: regattaId,
        name: 'Pool 1',
        allocation_mode: 'range',
        start_bib: 1,
        end_bib: 100,
        priority: 1,
        is_overflow: false
      })
      await api.createBibPool(regattaId, {
        name: 'Pool 1',
        allocation_mode: 'range',
        start_bib: 1,
        end_bib: 100,
        priority: 1,
        is_overflow: false
      })

      // Pool 2: 101-200 (non-overlapping)
      mockClient.post.mockResolvedValueOnce({
        id: 'pool-2',
        regatta_id: regattaId,
        name: 'Pool 2',
        allocation_mode: 'range',
        start_bib: 101,
        end_bib: 200,
        priority: 2,
        is_overflow: false
      })
      await api.createBibPool(regattaId, {
        name: 'Pool 2',
        allocation_mode: 'range',
        start_bib: 101,
        end_bib: 200,
        priority: 2,
        is_overflow: false
      })

      expect(mockClient.post).toHaveBeenCalledTimes(2)
    })

    it('simulates server rejection of overlapping bib pools', async () => {
      // First pool: 1-100
      mockClient.post.mockResolvedValueOnce({
        id: 'pool-1',
        regatta_id: regattaId,
        name: 'Pool 1',
        allocation_mode: 'range',
        start_bib: 1,
        end_bib: 100
      })
      await api.createBibPool(regattaId, {
        name: 'Pool 1',
        allocation_mode: 'range',
        start_bib: 1,
        end_bib: 100
      })

      // Second pool: 50-150 (overlaps with first)
      const overlapError = {
        code: 'BIB_POOL_VALIDATION_ERROR',
        message: 'Bib numbers overlap with existing pool',
        details: {
          overlapping_bibs: [50, 51, 52, 100],
          conflicting_pool_id: 'pool-1'
        }
      }
      mockClient.post.mockRejectedValueOnce(overlapError)

      await expect(
        api.createBibPool(regattaId, {
          name: 'Pool 2',
          allocation_mode: 'range',
          start_bib: 50,
          end_bib: 150
        })
      ).rejects.toEqual(overlapError)
    })
  })

  describe('Block and bib pool reordering', () => {
    it('reorders blocks by display_order', async () => {
      const reorderPayload = {
        items: [
          { block_id: 'block-2', display_order: 1 },
          { block_id: 'block-1', display_order: 2 }
        ]
      }
      mockClient.post.mockResolvedValue({ data: [] })

      await api.reorderBlocks(regattaId, reorderPayload)

      expect(mockClient.post).toHaveBeenCalledWith(
        `/regattas/${regattaId}/blocks/reorder`,
        reorderPayload
      )
    })

    it('reorders bib pools by priority', async () => {
      const reorderPayload = {
        items: [
          { bib_pool_id: 'pool-2', priority: 1 },
          { bib_pool_id: 'pool-1', priority: 2 }
        ]
      }
      mockClient.post.mockResolvedValue({ data: [] })

      await api.reorderBibPools(regattaId, reorderPayload)

      expect(mockClient.post).toHaveBeenCalledWith(
        `/regattas/${regattaId}/bib_pools/reorder`,
        reorderPayload
      )
    })
  })

  describe('Ruleset duplication workflow', () => {
    it('duplicates existing ruleset with new name and version', async () => {
      const originalId = 'ruleset-original'
      const duplicatePayload = {
        new_name: 'Duplicated Rules',
        new_version: '2024-v2'
      }
      mockClient.post.mockResolvedValue({
        id: 'ruleset-duplicate',
        name: 'Duplicated Rules',
        version: '2024-v2',
        age_calculation_type: 'actual_at_start',
        is_global: false
      })

      const duplicated = await api.duplicateRuleset(originalId, duplicatePayload)

      expect(duplicated.id).not.toBe(originalId)
      expect(duplicated.name).toBe('Duplicated Rules')
      expect(duplicated.version).toBe('2024-v2')
    })
  })
})
