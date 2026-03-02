import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createDrawApi } from '../draw.js'

describe('createDrawApi', () => {
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

  describe('Blocks API', () => {
    const regattaId = 'regatta-123'
    const blockId = 'block-456'

    describe('listBlocks', () => {
      it('calls GET /regattas/:regattaId/blocks', async () => {
        const mockResponse = {
          data: [
            { id: 'block-1', name: 'Morning Session', start_time: '2026-03-02T09:00:00Z' }
          ]
        }
        mockClient.get.mockResolvedValue(mockResponse)

        const result = await api.listBlocks(regattaId)

        expect(mockClient.get).toHaveBeenCalledWith(`/regattas/${regattaId}/blocks`)
        expect(result).toEqual(mockResponse)
      })
    })

    describe('createBlock', () => {
      it('calls POST /regattas/:regattaId/blocks with payload', async () => {
        const payload = {
          name: 'Morning Session',
          start_time: '09:00:00',
          event_interval_seconds: 120,
          crew_interval_seconds: 30
        }
        const mockResponse = { id: blockId, ...payload }
        mockClient.post.mockResolvedValue(mockResponse)

        const result = await api.createBlock(regattaId, payload)

        expect(mockClient.post).toHaveBeenCalledWith(
          `/regattas/${regattaId}/blocks`,
          payload
        )
        expect(result).toEqual(mockResponse)
      })
    })

    describe('updateBlock', () => {
      it('calls PATCH /regattas/:regattaId/blocks/:blockId with payload', async () => {
        const payload = { name: 'Updated Morning Session' }
        const mockResponse = { id: blockId, ...payload }
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
      it('calls DELETE /regattas/:regattaId/blocks/:blockId', async () => {
        mockClient.delete.mockResolvedValue(undefined)

        await api.deleteBlock(regattaId, blockId)

        expect(mockClient.delete).toHaveBeenCalledWith(
          `/regattas/${regattaId}/blocks/${blockId}`
        )
      })
    })

    describe('reorderBlocks', () => {
      it('calls POST /regattas/:regattaId/blocks/reorder with OpenAPI items payload', async () => {
        const payload = { block_ids: ['block-1', 'block-2', 'block-3'] }
        mockClient.post.mockResolvedValue(undefined)

        await api.reorderBlocks(regattaId, payload)

        expect(mockClient.post).toHaveBeenCalledWith(
          `/regattas/${regattaId}/blocks/reorder`,
          {
            items: [
              { block_id: 'block-1', display_order: 1 },
              { block_id: 'block-2', display_order: 2 },
              { block_id: 'block-3', display_order: 3 }
            ]
          }
        )
      })
    })
  })

  describe('Bib Pools API', () => {
    const regattaId = 'regatta-123'
    const poolId = 'pool-789'

    describe('listBibPools', () => {
      it('calls GET /regattas/:regattaId/bib_pools', async () => {
        const mockResponse = {
          data: [
            { id: 'pool-1', name: 'Main Pool', allocation_mode: 'range', start_bib: 1, end_bib: 100 }
          ]
        }
        mockClient.get.mockResolvedValue(mockResponse)

        const result = await api.listBibPools(regattaId)

        expect(mockClient.get).toHaveBeenCalledWith(`/regattas/${regattaId}/bib_pools`)
        expect(result).toEqual(mockResponse)
      })
    })

    describe('createBibPool', () => {
      it('calls POST /regattas/:regattaId/bib_pools with range mode payload', async () => {
        const payload = {
          name: 'Main Pool',
          block_id: 'block-123',
          allocation_mode: 'range',
          start_bib: 1,
          end_bib: 100,
          is_overflow: false
        }
        const mockResponse = { id: poolId, ...payload }
        mockClient.post.mockResolvedValue(mockResponse)

        const result = await api.createBibPool(regattaId, payload)

        expect(mockClient.post).toHaveBeenCalledWith(
          `/regattas/${regattaId}/bib_pools`,
          payload
        )
        expect(result).toEqual(mockResponse)
      })

      it('calls POST /regattas/:regattaId/bib_pools with explicit list mode payload', async () => {
        const payload = {
          name: 'Special Pool',
          block_id: 'block-123',
          allocation_mode: 'explicit_list',
          bib_numbers: [1, 5, 10, 15],
          is_overflow: false
        }
        const mockResponse = { id: poolId, ...payload }
        mockClient.post.mockResolvedValue(mockResponse)

        const result = await api.createBibPool(regattaId, payload)

        expect(mockClient.post).toHaveBeenCalledWith(
          `/regattas/${regattaId}/bib_pools`,
          payload
        )
        expect(result).toEqual(mockResponse)
      })

      it('calls POST /regattas/:regattaId/bib_pools with overflow pool payload', async () => {
        const payload = {
          name: 'Overflow Pool',
          allocation_mode: 'range',
          start_bib: 900,
          end_bib: 999,
          is_overflow: true
        }
        const mockResponse = { id: poolId, ...payload }
        mockClient.post.mockResolvedValue(mockResponse)

        const result = await api.createBibPool(regattaId, payload)

        expect(mockClient.post).toHaveBeenCalledWith(
          `/regattas/${regattaId}/bib_pools`,
          payload
        )
        expect(result).toEqual(mockResponse)
      })
    })

    describe('updateBibPool', () => {
      it('calls PATCH /regattas/:regattaId/bib_pools/:poolId with payload', async () => {
        const payload = { name: 'Updated Pool' }
        const mockResponse = { id: poolId, ...payload }
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
      it('calls DELETE /regattas/:regattaId/bib_pools/:poolId', async () => {
        mockClient.delete.mockResolvedValue(undefined)

        await api.deleteBibPool(regattaId, poolId)

        expect(mockClient.delete).toHaveBeenCalledWith(
          `/regattas/${regattaId}/bib_pools/${poolId}`
        )
      })
    })

    describe('reorderBibPools', () => {
      it('calls POST /regattas/:regattaId/bib_pools/reorder with OpenAPI items payload', async () => {
        const payload = { bib_pool_ids: ['pool-1', 'pool-2', 'pool-3'] }
        mockClient.post.mockResolvedValue(undefined)

        await api.reorderBibPools(regattaId, payload)

        expect(mockClient.post).toHaveBeenCalledWith(
          `/regattas/${regattaId}/bib_pools/reorder`,
          {
            items: [
              { bib_pool_id: 'pool-1', priority: 1 },
              { bib_pool_id: 'pool-2', priority: 2 },
              { bib_pool_id: 'pool-3', priority: 3 }
            ]
          }
        )
      })
    })
  })
})
