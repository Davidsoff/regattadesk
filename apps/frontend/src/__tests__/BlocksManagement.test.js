import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import i18n from '../i18n'
import BlocksManagement from '../views/staff/BlocksManagement.vue'
import * as api from '../api'

vi.mock('../api', () => ({
  createApiClient: vi.fn(() => ({})),
  createDrawApi: vi.fn()
}))

const REGATTA_ID = 'regatta-123'

async function mountPage(mockDrawApi = {}) {
  api.createDrawApi.mockReturnValue(mockDrawApi)

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/staff/regattas/:regattaId/blocks',
        name: 'staff-blocks-management',
        component: BlocksManagement
      }
    ]
  })

  await router.push(`/staff/regattas/${REGATTA_ID}/blocks`)
  await router.isReady()

  const wrapper = mount(BlocksManagement, {
    attachTo: document.body,
    global: {
      plugins: [router, i18n]
    }
  })

  await flushPromises()
  return wrapper
}

describe('BlocksManagement view (FEGAP-008-B)', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  describe('Initial rendering', () => {
    it('renders blocks list with timing details', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({
          blocks: [
            {
              id: 'block-1',
              name: 'Morning Session',
              start_time: '09:00:00',
              event_interval_seconds: 120,
              crew_interval_seconds: 30,
              display_order: 1
            },
            {
              id: 'block-2',
              name: 'Afternoon Session',
              start_time: '14:00:00',
              event_interval_seconds: 150,
              crew_interval_seconds: 45,
              display_order: 2
            }
          ]
        }),
        listBibPools: vi.fn().mockResolvedValue({ bib_pools: [] })
      }

      const wrapper = await mountPage(mockDrawApi)

      expect(mockDrawApi.listBlocks).toHaveBeenCalledWith(REGATTA_ID)
      
      const blocksList = wrapper.find('[data-testid="blocks-list"]')
      expect(blocksList.exists()).toBe(true)

      const blockItems = wrapper.findAll('[data-testid^="block-item-"]')
      expect(blockItems).toHaveLength(2)

      const firstBlock = wrapper.find('[data-testid="block-item-block-1"]')
      expect(firstBlock.text()).toContain('Morning Session')
      expect(firstBlock.text()).toContain('09:00:00')
      expect(firstBlock.text()).toContain('120')
      expect(firstBlock.text()).toContain('30')
    })

    it('displays empty state when no blocks configured', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({ blocks: [] }),
        listBibPools: vi.fn().mockResolvedValue({ bib_pools: [] })
      }

      const wrapper = await mountPage(mockDrawApi)

      expect(wrapper.find('[data-testid="no-blocks-message"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="no-blocks-message"]').text()).toContain('No blocks configured')
    })
  })

  describe('Block CRUD operations', () => {
    it('opens create block dialog when add button clicked', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({ blocks: [] }),
        listBibPools: vi.fn().mockResolvedValue({ bib_pools: [] })
      }

      const wrapper = await mountPage(mockDrawApi)

      const addButton = wrapper.find('[data-testid="add-block-button"]')
      expect(addButton.exists()).toBe(true)

      await addButton.trigger('click')

      const dialog = wrapper.find('[data-testid="block-dialog"]')
      expect(dialog.exists()).toBe(true)
      expect(dialog.attributes('open')).toBeDefined()

      expect(wrapper.find('input[name="block_name"]').exists()).toBe(true)
      expect(wrapper.find('input[name="start_time"]').exists()).toBe(true)
      expect(wrapper.find('input[name="event_interval_seconds"]').exists()).toBe(true)
      expect(wrapper.find('input[name="crew_interval_seconds"]').exists()).toBe(true)
    })

    it('creates new block with valid data', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn()
          .mockResolvedValueOnce({ blocks: [] })
          .mockResolvedValueOnce({
            blocks: [
              {
                id: 'block-new',
                name: 'Test Block',
                start_time: '10:00:00',
                event_interval_seconds: 180,
                crew_interval_seconds: 60,
                display_order: 1
              }
            ]
          }),
        listBibPools: vi.fn().mockResolvedValue({ bib_pools: [] }),
        createBlock: vi.fn().mockResolvedValue({
          id: 'block-new',
          name: 'Test Block',
          start_time: '10:00:00',
          event_interval_seconds: 180,
          crew_interval_seconds: 60
        })
      }

      const wrapper = await mountPage(mockDrawApi)

      await wrapper.find('[data-testid="add-block-button"]').trigger('click')

      await wrapper.find('input[name="block_name"]').setValue('Test Block')
      await wrapper.find('input[name="start_time"]').setValue('10:00:00')
      await wrapper.find('input[name="event_interval_seconds"]').setValue('180')
      await wrapper.find('input[name="crew_interval_seconds"]').setValue('60')

      await wrapper.find('[data-testid="save-block-button"]').trigger('click')
      await flushPromises()

      expect(mockDrawApi.createBlock).toHaveBeenCalledWith(REGATTA_ID, {
        name: 'Test Block',
        start_time: '10:00:00',
        event_interval_seconds: 180,
        crew_interval_seconds: 60
      })

      expect(mockDrawApi.listBlocks).toHaveBeenCalledTimes(2)
    })

    it('shows validation errors for invalid block data', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({ blocks: [] }),
        listBibPools: vi.fn().mockResolvedValue({ bib_pools: [] })
      }

      const wrapper = await mountPage(mockDrawApi)

      await wrapper.find('[data-testid="add-block-button"]').trigger('click')
      await wrapper.find('[data-testid="save-block-button"]').trigger('click')

      const validationErrors = wrapper.find('[data-testid="block-validation-errors"]')
      expect(validationErrors.exists()).toBe(true)
      expect(validationErrors.text()).toContain('required')
    })

    it('edits existing block', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn()
          .mockResolvedValueOnce({
            blocks: [
              {
                id: 'block-1',
                name: 'Morning Session',
                start_time: '09:00:00',
                event_interval_seconds: 120,
                crew_interval_seconds: 30,
                display_order: 1
              }
            ]
          })
          .mockResolvedValueOnce({
            blocks: [
              {
                id: 'block-1',
                name: 'Updated Session',
                start_time: '09:30:00',
                event_interval_seconds: 150,
                crew_interval_seconds: 40,
                display_order: 1
              }
            ]
          }),
        listBibPools: vi.fn().mockResolvedValue({ bib_pools: [] }),
        updateBlock: vi.fn().mockResolvedValue({})
      }

      const wrapper = await mountPage(mockDrawApi)

      const editButton = wrapper.find('[data-testid="edit-block-block-1"]')
      await editButton.trigger('click')

      await wrapper.find('input[name="block_name"]').setValue('Updated Session')
      await wrapper.find('input[name="start_time"]').setValue('09:30:00')
      await wrapper.find('input[name="event_interval_seconds"]').setValue('150')
      await wrapper.find('input[name="crew_interval_seconds"]').setValue('40')

      await wrapper.find('[data-testid="save-block-button"]').trigger('click')
      await flushPromises()

      expect(mockDrawApi.updateBlock).toHaveBeenCalledWith(REGATTA_ID, 'block-1', {
        name: 'Updated Session',
        start_time: '09:30:00',
        event_interval_seconds: 150,
        crew_interval_seconds: 40
      })
    })

    it('deletes block with confirmation dialog', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn()
          .mockResolvedValueOnce({
            blocks: [
              {
                id: 'block-1',
                name: 'Morning Session',
                start_time: '09:00:00',
                event_interval_seconds: 120,
                crew_interval_seconds: 30,
                display_order: 1
              }
            ]
          })
          .mockResolvedValueOnce({ blocks: [] }),
        listBibPools: vi.fn().mockResolvedValue({ bib_pools: [] }),
        deleteBlock: vi.fn().mockResolvedValue({})
      }

      const wrapper = await mountPage(mockDrawApi)

      const deleteButton = wrapper.find('[data-testid="delete-block-block-1"]')
      await deleteButton.trigger('click')

      const confirmDialog = wrapper.find('[data-testid="confirm-delete-dialog"]')
      expect(confirmDialog.exists()).toBe(true)
      expect(confirmDialog.text()).toContain('delete')

      await wrapper.find('[data-testid="confirm-delete-button"]').trigger('click')
      await flushPromises()

      expect(mockDrawApi.deleteBlock).toHaveBeenCalledWith(REGATTA_ID, 'block-1')
    })
  })

  describe('Bib Pool display and management', () => {
    it('displays bib pools grouped by block', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({
          blocks: [
            {
              id: 'block-1',
              name: 'Morning Session',
              start_time: '09:00:00',
              event_interval_seconds: 120,
              crew_interval_seconds: 30,
              display_order: 1
            }
          ]
        }),
        listBibPools: vi.fn().mockResolvedValue({
          bib_pools: [
            {
              id: 'pool-1',
              name: 'Main Pool',
              block_id: 'block-1',
              allocation_mode: 'range',
              start_bib: 1,
              end_bib: 100,
              is_overflow: false,
              priority: 1
            },
            {
              id: 'pool-2',
              name: 'Special Pool',
              block_id: 'block-1',
              allocation_mode: 'explicit_list',
              bib_numbers: [101, 102, 103],
              is_overflow: false,
              priority: 2
            }
          ]
        })
      }

      const wrapper = await mountPage(mockDrawApi)

      const poolsList = wrapper.find('[data-testid="bib-pools-block-1"]')
      expect(poolsList.exists()).toBe(true)

      const poolItems = wrapper.findAll('[data-testid^="bib-pool-item-"]')
      expect(poolItems).toHaveLength(2)

      const rangePool = wrapper.find('[data-testid="bib-pool-item-pool-1"]')
      expect(rangePool.text()).toContain('Main Pool')
      expect(rangePool.text()).toContain('1')
      expect(rangePool.text()).toContain('100')

      const explicitPool = wrapper.find('[data-testid="bib-pool-item-pool-2"]')
      expect(explicitPool.text()).toContain('Special Pool')
      expect(explicitPool.text()).toContain('101')
    })

    it('displays overflow pool separately', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({ blocks: [] }),
        listBibPools: vi.fn().mockResolvedValue({
          bib_pools: [
            {
              id: 'pool-overflow',
              name: 'Overflow Pool',
              allocation_mode: 'range',
              start_bib: 900,
              end_bib: 999,
              is_overflow: true,
              priority: 999
            }
          ]
        })
      }

      const wrapper = await mountPage(mockDrawApi)

      const overflowSection = wrapper.find('[data-testid="overflow-pool-section"]')
      expect(overflowSection.exists()).toBe(true)

      const overflowPool = wrapper.find('[data-testid="bib-pool-item-pool-overflow"]')
      expect(overflowPool.exists()).toBe(true)
      expect(overflowPool.text()).toContain('Overflow Pool')
      expect(overflowPool.text()).toContain('900')
      expect(overflowPool.text()).toContain('999')
    })

    it('creates bib pool in range mode', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({
          blocks: [{ id: 'block-1', name: 'Morning', start_time: '09:00:00', event_interval_seconds: 120, crew_interval_seconds: 30 }]
        }),
        listBibPools: vi.fn()
          .mockResolvedValueOnce({ bib_pools: [] })
          .mockResolvedValueOnce({
            bib_pools: [
              {
                id: 'pool-new',
                name: 'Test Pool',
                block_id: 'block-1',
                allocation_mode: 'range',
                start_bib: 1,
                end_bib: 50,
                is_overflow: false
              }
            ]
          }),
        createBibPool: vi.fn().mockResolvedValue({})
      }

      const wrapper = await mountPage(mockDrawApi)

      await wrapper.find('[data-testid="add-bib-pool-button"]').trigger('click')

      await wrapper.find('input[name="pool_name"]').setValue('Test Pool')
      await wrapper.find('select[name="block_id"]').setValue('block-1')
      await wrapper.find('select[name="allocation_mode"]').setValue('range')
      await wrapper.find('input[name="start_bib"]').setValue('1')
      await wrapper.find('input[name="end_bib"]').setValue('50')

      await wrapper.find('[data-testid="save-bib-pool-button"]').trigger('click')
      await flushPromises()

      expect(mockDrawApi.createBibPool).toHaveBeenCalledWith(REGATTA_ID, {
        name: 'Test Pool',
        block_id: 'block-1',
        allocation_mode: 'range',
        start_bib: 1,
        end_bib: 50,
        is_overflow: false
      })
    })

    it('creates bib pool in explicit list mode', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({
          blocks: [{ id: 'block-1', name: 'Morning', start_time: '09:00:00', event_interval_seconds: 120, crew_interval_seconds: 30 }]
        }),
        listBibPools: vi.fn()
          .mockResolvedValueOnce({ bib_pools: [] })
          .mockResolvedValueOnce({
            bib_pools: [
              {
                id: 'pool-new',
                name: 'Explicit Pool',
                block_id: 'block-1',
                allocation_mode: 'explicit_list',
                bib_numbers: [1, 5, 10],
                is_overflow: false
              }
            ]
          }),
        createBibPool: vi.fn().mockResolvedValue({})
      }

      const wrapper = await mountPage(mockDrawApi)

      await wrapper.find('[data-testid="add-bib-pool-button"]').trigger('click')

      await wrapper.find('input[name="pool_name"]').setValue('Explicit Pool')
      await wrapper.find('select[name="block_id"]').setValue('block-1')
      await wrapper.find('select[name="allocation_mode"]').setValue('explicit_list')
      await wrapper.find('input[name="bib_numbers"]').setValue('1, 5, 10')

      await wrapper.find('[data-testid="save-bib-pool-button"]').trigger('click')
      await flushPromises()

      expect(mockDrawApi.createBibPool).toHaveBeenCalledWith(REGATTA_ID, {
        name: 'Explicit Pool',
        block_id: 'block-1',
        allocation_mode: 'explicit_list',
        bib_numbers: [1, 5, 10],
        is_overflow: false
      })
    })

    it('shows bib pool overlap validation errors', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({
          blocks: [{ id: 'block-1', name: 'Morning', start_time: '09:00:00', event_interval_seconds: 120, crew_interval_seconds: 30 }]
        }),
        listBibPools: vi.fn().mockResolvedValue({ bib_pools: [] }),
        createBibPool: vi.fn().mockRejectedValue({
          code: 'BIB_POOL_VALIDATION_ERROR',
          message: 'Bib pool overlap detected',
          details: {
            overlapping_bibs: [10, 11, 12],
            conflicting_pool_name: 'Existing Pool'
          }
        })
      }

      const wrapper = await mountPage(mockDrawApi)

      await wrapper.find('[data-testid="add-bib-pool-button"]').trigger('click')
      await wrapper.find('input[name="pool_name"]').setValue('New Pool')
      await wrapper.find('select[name="block_id"]').setValue('block-1')
      await wrapper.find('select[name="allocation_mode"]').setValue('range')
      await wrapper.find('input[name="start_bib"]').setValue('10')
      await wrapper.find('input[name="end_bib"]').setValue('20')

      await wrapper.find('[data-testid="save-bib-pool-button"]').trigger('click')
      await flushPromises()

      const errorMessage = wrapper.find('[data-testid="bib-pool-error"]')
      expect(errorMessage.exists()).toBe(true)
      expect(errorMessage.text()).toContain('overlap')
      expect(errorMessage.text()).toContain('10, 11, 12')
      expect(errorMessage.text()).toContain('Existing Pool')
    })
  })
})
