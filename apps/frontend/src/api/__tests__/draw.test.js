import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createDrawApi } from '../draw'

describe('draw', () => {
  let api
  let mockClient

  beforeEach(() => {
    mockClient = {
      get: vi.fn(),
      post: vi.fn(),
      patch: vi.fn(),
      delete: vi.fn()
    }
    api = createDrawApi(mockClient)
  })

  describe('rulesets', () => {
    describe('listRulesets', () => {
      it('fetches all rulesets without filter', async () => {
        const mockResponse = {
          data: [
            {
              id: '550e8400-e29b-41d4-a716-446655440000',
              name: 'FISA Rules',
              version: '2024',
              description: 'FISA rowing rules',
              age_calculation_type: 'actual_at_start',
              is_global: true
            }
          ]
        }
        mockClient.get.mockResolvedValue(mockResponse)

        const result = await api.listRulesets()

        expect(mockClient.get).toHaveBeenCalledWith('/rulesets')
        expect(result).toEqual(mockResponse)
      })

      it('fetches global rulesets when is_global=true', async () => {
        const mockResponse = { data: [] }
        mockClient.get.mockResolvedValue(mockResponse)

        await api.listRulesets({ is_global: true })

        expect(mockClient.get).toHaveBeenCalledWith('/rulesets?is_global=true')
      })

      it('fetches regatta-owned rulesets when is_global=false', async () => {
        const mockResponse = { data: [] }
        mockClient.get.mockResolvedValue(mockResponse)

        await api.listRulesets({ is_global: false })

        expect(mockClient.get).toHaveBeenCalledWith('/rulesets?is_global=false')
      })
    })

    describe('getRuleset', () => {
      it('fetches a single ruleset by id', async () => {
        const rulesetId = '550e8400-e29b-41d4-a716-446655440000'
        const mockResponse = {
          id: rulesetId,
          name: 'Custom Rules',
          version: 'v1',
          age_calculation_type: 'age_as_of_jan_1',
          is_global: false
        }
        mockClient.get.mockResolvedValue(mockResponse)

        const result = await api.getRuleset(rulesetId)

        expect(mockClient.get).toHaveBeenCalledWith(`/rulesets/${rulesetId}`)
        expect(result).toEqual(mockResponse)
      })
    })

    describe('createRuleset', () => {
      it('creates a new ruleset', async () => {
        const payload = {
          name: 'New Ruleset',
          version: '2024',
          description: 'Test ruleset',
          age_calculation_type: 'actual_at_start'
        }
        const mockResponse = {
          id: '550e8400-e29b-41d4-a716-446655440000',
          ...payload,
          is_global: false
        }
        mockClient.post.mockResolvedValue(mockResponse)

        const result = await api.createRuleset(payload)

        expect(mockClient.post).toHaveBeenCalledWith('/rulesets', payload)
        expect(result).toEqual(mockResponse)
      })
    })

    describe('updateRuleset', () => {
      it('updates an existing ruleset', async () => {
        const rulesetId = '550e8400-e29b-41d4-a716-446655440000'
        const payload = {
          name: 'Updated Name',
          description: 'Updated description'
        }
        const mockResponse = {
          id: rulesetId,
          name: 'Updated Name',
          version: '2024',
          description: 'Updated description',
          age_calculation_type: 'actual_at_start',
          is_global: false
        }
        mockClient.patch.mockResolvedValue(mockResponse)

        const result = await api.updateRuleset(rulesetId, payload)

        expect(mockClient.patch).toHaveBeenCalledWith(`/rulesets/${rulesetId}`, payload)
        expect(result).toEqual(mockResponse)
      })
    })

    describe('duplicateRuleset', () => {
      it('duplicates a ruleset with new name and version', async () => {
        const rulesetId = '550e8400-e29b-41d4-a716-446655440000'
        const payload = {
          new_name: 'Duplicated Ruleset',
          new_version: '2024-v2'
        }
        const mockResponse = {
          id: '660e8400-e29b-41d4-a716-446655440000',
          name: 'Duplicated Ruleset',
          version: '2024-v2',
          age_calculation_type: 'actual_at_start',
          is_global: false
        }
        mockClient.post.mockResolvedValue(mockResponse)

        const result = await api.duplicateRuleset(rulesetId, payload)

        expect(mockClient.post).toHaveBeenCalledWith(`/rulesets/${rulesetId}/duplicate`, payload)
        expect(result).toEqual(mockResponse)
      })
    })

    describe('promoteRuleset', () => {
      it('promotes a regatta-owned ruleset to global (super_admin only)', async () => {
        const rulesetId = '550e8400-e29b-41d4-a716-446655440000'
        const mockResponse = {
          id: rulesetId,
          name: 'Promoted Ruleset',
          version: '2024',
          age_calculation_type: 'actual_at_start',
          is_global: true
        }
        mockClient.post.mockResolvedValue(mockResponse)

        const result = await api.promoteRuleset(rulesetId)

        expect(mockClient.post).toHaveBeenCalledWith(`/rulesets/${rulesetId}/promote`)
        expect(result).toEqual(mockResponse)
      })
    })
  })

  describe('blocks', () => {
    describe('listBlocks', () => {
      it('fetches blocks for a regatta', async () => {
        const regattaId = 'regatta-123'
        const mockResponse = {
          data: [
            {
              id: 'block-1',
              regatta_id: regattaId,
              name: 'Morning Session',
              start_time: '2024-08-15T09:00:00Z',
              event_interval_seconds: 300,
              crew_interval_seconds: 60,
              display_order: 1
            }
          ]
        }
        mockClient.get.mockResolvedValue(mockResponse)

        const result = await api.listBlocks(regattaId)

        expect(mockClient.get).toHaveBeenCalledWith(`/regattas/${regattaId}/blocks`)
        expect(result).toEqual(mockResponse)
      })
    })

    describe('createBlock', () => {
      it('creates a new block', async () => {
        const regattaId = 'regatta-123'
        const payload = {
          name: 'Afternoon Session',
          start_time: '2024-08-15T14:00:00Z',
          event_interval_seconds: 300,
          crew_interval_seconds: 60
        }
        const mockResponse = {
          id: 'block-2',
          regatta_id: regattaId,
          ...payload,
          display_order: 2
        }
        mockClient.post.mockResolvedValue(mockResponse)

        const result = await api.createBlock(regattaId, payload)

        expect(mockClient.post).toHaveBeenCalledWith(`/regattas/${regattaId}/blocks`, payload)
        expect(result).toEqual(mockResponse)
      })
    })

    describe('updateBlock', () => {
      it('updates an existing block', async () => {
        const regattaId = 'regatta-123'
        const blockId = 'block-1'
        const payload = {
          name: 'Updated Morning Session',
          start_time: '2024-08-15T08:30:00Z'
        }
        const mockResponse = {
          id: blockId,
          regatta_id: regattaId,
          name: 'Updated Morning Session',
          start_time: '2024-08-15T08:30:00Z',
          event_interval_seconds: 300,
          crew_interval_seconds: 60,
          display_order: 1
        }
        mockClient.patch.mockResolvedValue(mockResponse)

        const result = await api.updateBlock(regattaId, blockId, payload)

        expect(mockClient.patch).toHaveBeenCalledWith(
          `/regattas/${regattaId}/blocks/${blockId}`,
          payload
        )
        expect(result).toEqual(mockResponse)
      })
    })

    describe('deleteBlock', () => {
      it('deletes a block', async () => {
        const regattaId = 'regatta-123'
        const blockId = 'block-1'
        mockClient.delete.mockResolvedValue(null)

        await api.deleteBlock(regattaId, blockId)

        expect(mockClient.delete).toHaveBeenCalledWith(`/regattas/${regattaId}/blocks/${blockId}`)
      })
    })

    describe('reorderBlocks', () => {
      it('reorders blocks by display_order', async () => {
        const regattaId = 'regatta-123'
        const payload = {
          items: [
            { block_id: 'block-2', display_order: 1 },
            { block_id: 'block-1', display_order: 2 }
          ]
        }
        const mockResponse = { data: [] }
        mockClient.post.mockResolvedValue(mockResponse)

        const result = await api.reorderBlocks(regattaId, payload)

        expect(mockClient.post).toHaveBeenCalledWith(
          `/regattas/${regattaId}/blocks/reorder`,
          payload
        )
        expect(result).toEqual(mockResponse)
      })
    })
  })

  describe('bib pools', () => {
    describe('listBibPools', () => {
      it('fetches bib pools for a regatta', async () => {
        const regattaId = 'regatta-123'
        const mockResponse = {
          data: [
            {
              id: 'pool-1',
              regatta_id: regattaId,
              block_id: 'block-1',
              name: 'Morning Pool',
              allocation_mode: 'range',
              start_bib: 1,
              end_bib: 100,
              bib_numbers: null,
              priority: 1,
              is_overflow: false
            }
          ]
        }
        mockClient.get.mockResolvedValue(mockResponse)

        const result = await api.listBibPools(regattaId)

        expect(mockClient.get).toHaveBeenCalledWith(`/regattas/${regattaId}/bib_pools`)
        expect(result).toEqual(mockResponse)
      })
    })

    describe('createBibPool', () => {
      it('creates a range-based bib pool', async () => {
        const regattaId = 'regatta-123'
        const payload = {
          block_id: 'block-1',
          name: 'Pool 1-100',
          allocation_mode: 'range',
          start_bib: 1,
          end_bib: 100,
          priority: 1,
          is_overflow: false
        }
        const mockResponse = {
          id: 'pool-1',
          regatta_id: regattaId,
          ...payload,
          bib_numbers: null
        }
        mockClient.post.mockResolvedValue(mockResponse)

        const result = await api.createBibPool(regattaId, payload)

        expect(mockClient.post).toHaveBeenCalledWith(`/regattas/${regattaId}/bib_pools`, payload)
        expect(result).toEqual(mockResponse)
      })

      it('creates an explicit-list bib pool', async () => {
        const regattaId = 'regatta-123'
        const payload = {
          block_id: 'block-1',
          name: 'Custom Pool',
          allocation_mode: 'explicit_list',
          bib_numbers: [101, 102, 103, 200, 201],
          priority: 2,
          is_overflow: false
        }
        const mockResponse = {
          id: 'pool-2',
          regatta_id: regattaId,
          ...payload,
          start_bib: null,
          end_bib: null
        }
        mockClient.post.mockResolvedValue(mockResponse)

        const result = await api.createBibPool(regattaId, payload)

        expect(mockClient.post).toHaveBeenCalledWith(`/regattas/${regattaId}/bib_pools`, payload)
        expect(result).toEqual(mockResponse)
      })

      it('creates an overflow bib pool', async () => {
        const regattaId = 'regatta-123'
        const payload = {
          name: 'Overflow Pool',
          allocation_mode: 'range',
          start_bib: 500,
          end_bib: 599,
          priority: 99,
          is_overflow: true
        }
        const mockResponse = {
          id: 'pool-overflow',
          regatta_id: regattaId,
          block_id: null,
          ...payload
        }
        mockClient.post.mockResolvedValue(mockResponse)

        const result = await api.createBibPool(regattaId, payload)

        expect(mockClient.post).toHaveBeenCalledWith(`/regattas/${regattaId}/bib_pools`, payload)
        expect(result).toEqual(mockResponse)
      })
    })

    describe('updateBibPool', () => {
      it('updates a bib pool', async () => {
        const regattaId = 'regatta-123'
        const poolId = 'pool-1'
        const payload = {
          name: 'Updated Pool Name',
          start_bib: 1,
          end_bib: 150
        }
        const mockResponse = {
          id: poolId,
          regatta_id: regattaId,
          block_id: 'block-1',
          name: 'Updated Pool Name',
          allocation_mode: 'range',
          start_bib: 1,
          end_bib: 150,
          bib_numbers: null,
          priority: 1,
          is_overflow: false
        }
        mockClient.patch.mockResolvedValue(mockResponse)

        const result = await api.updateBibPool(regattaId, poolId, payload)

        expect(mockClient.patch).toHaveBeenCalledWith(
          `/regattas/${regattaId}/bib_pools/${poolId}`,
          payload
        )
        expect(result).toEqual(mockResponse)
      })
    })

    describe('deleteBibPool', () => {
      it('deletes a bib pool', async () => {
        const regattaId = 'regatta-123'
        const poolId = 'pool-1'
        mockClient.delete.mockResolvedValue(null)

        await api.deleteBibPool(regattaId, poolId)

        expect(mockClient.delete).toHaveBeenCalledWith(
          `/regattas/${regattaId}/bib_pools/${poolId}`
        )
      })
    })

    describe('reorderBibPools', () => {
      it('reorders bib pools by priority', async () => {
        const regattaId = 'regatta-123'
        const payload = {
          items: [
            { bib_pool_id: 'pool-2', priority: 1 },
            { bib_pool_id: 'pool-1', priority: 2 }
          ]
        }
        const mockResponse = { data: [] }
        mockClient.post.mockResolvedValue(mockResponse)

        const result = await api.reorderBibPools(regattaId, payload)

        expect(mockClient.post).toHaveBeenCalledWith(
          `/regattas/${regattaId}/bib_pools/reorder`,
          payload
        )
        expect(result).toEqual(mockResponse)
      })
    })
  })

  describe('draw operations', () => {
    describe('generateDraw', () => {
      it('generates draw without custom seed', async () => {
        const regattaId = 'regatta-123'
        const mockResponse = {
          draw_revision: 1,
          results_revision: 0,
          seed: 'auto-generated-seed-123'
        }
        mockClient.post.mockResolvedValue(mockResponse)

        const result = await api.generateDraw(regattaId)

        expect(mockClient.post).toHaveBeenCalledWith(`/regattas/${regattaId}/draw/generate`)
        expect(result).toEqual(mockResponse)
      })

      it('generates draw with custom seed', async () => {
        const regattaId = 'regatta-123'
        const payload = {
          seed: 'custom-seed-456'
        }
        const mockResponse = {
          draw_revision: 1,
          results_revision: 0,
          seed: 'custom-seed-456'
        }
        mockClient.post.mockResolvedValue(mockResponse)

        const result = await api.generateDraw(regattaId, payload)

        expect(mockClient.post).toHaveBeenCalledWith(
          `/regattas/${regattaId}/draw/generate`,
          payload
        )
        expect(result).toEqual(mockResponse)
      })
    })

    describe('publishDraw', () => {
      it('publishes draw and increments draw_revision', async () => {
        const regattaId = 'regatta-123'
        const mockResponse = {
          draw_revision: 2,
          results_revision: 0
        }
        mockClient.post.mockResolvedValue(mockResponse)

        const result = await api.publishDraw(regattaId)

        expect(mockClient.post).toHaveBeenCalledWith(`/regattas/${regattaId}/draw/publish`)
        expect(result).toEqual(mockResponse)
      })
    })

    describe('unpublishDraw', () => {
      it('unpublishes draw and reverts draw_revision', async () => {
        const regattaId = 'regatta-123'
        const mockResponse = {
          draw_revision: 1,
          results_revision: 0
        }
        mockClient.post.mockResolvedValue(mockResponse)

        const result = await api.unpublishDraw(regattaId)

        expect(mockClient.post).toHaveBeenCalledWith(`/regattas/${regattaId}/draw/unpublish`)
        expect(result).toEqual(mockResponse)
      })
    })
  })
})
