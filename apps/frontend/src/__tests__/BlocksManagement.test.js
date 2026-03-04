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

const REGATTA_ID = '11111111-1111-4111-8111-111111111111'
const MORNING_BLOCK = {
  id: 'block-1',
  name: 'Morning Session',
  start_time: '2026-03-02T09:00:00Z',
  event_interval_seconds: 120,
  crew_interval_seconds: 30,
  display_order: 1
}
const AFTERNOON_BLOCK = {
  id: 'block-2',
  name: 'Afternoon Session',
  start_time: '2026-03-02T14:00:00Z',
  event_interval_seconds: 150,
  crew_interval_seconds: 45,
  display_order: 2
}
const REORDERED_BLOCKS = [
  { ...AFTERNOON_BLOCK, display_order: 1 },
  { ...MORNING_BLOCK, display_order: 2 }
]
const REORDER_PAYLOAD = {
  items: [
    { block_id: 'block-2', display_order: 1 },
    { block_id: 'block-1', display_order: 2 }
  ]
}

function blocksResponse(blocks) {
  return { data: blocks.map((block) => ({ ...block })) }
}

function defaultDrawApi(overrides = {}) {
  return {
    listBlocks: vi.fn().mockResolvedValue({ data: [] }),
    listBibPools: vi.fn().mockResolvedValue({ data: [] }),
    ...overrides
  }
}

async function dragBlockOnto(wrapper, sourceBlockId, targetBlockId) {
  const source = wrapper.find(`[data-testid="block-item-${sourceBlockId}"]`)
  const target = wrapper.find(`[data-testid="block-item-${targetBlockId}"]`)
  const dataTransfer = {
    effectAllowed: 'move',
    setData: vi.fn(),
    getData: vi.fn(() => sourceBlockId)
  }

  await source.trigger('dragstart', { dataTransfer })
  await target.trigger('drop', { dataTransfer, preventDefault: vi.fn() })
}

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
      const mockDrawApi = defaultDrawApi({
        listBlocks: vi.fn().mockResolvedValue(blocksResponse([MORNING_BLOCK, AFTERNOON_BLOCK]))
      })

      const wrapper = await mountPage(mockDrawApi)

      expect(mockDrawApi.listBlocks).toHaveBeenCalledWith(REGATTA_ID)
      
      const blocksList = wrapper.find('[data-testid="blocks-list"]')
      expect(blocksList.exists()).toBe(true)

      const blockItems = wrapper.findAll('[data-testid^="block-item-"]')
      expect(blockItems).toHaveLength(2)

      const firstBlock = wrapper.find('[data-testid="block-item-block-1"]')
      expect(firstBlock.text()).toContain('Morning Session')
      expect(firstBlock.text()).toContain('120')
      expect(firstBlock.text()).toContain('30')
    })

    it('displays empty state when no blocks configured', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({ data: [] }),
        listBibPools: vi.fn().mockResolvedValue({ data: [] })
      }

      const wrapper = await mountPage(mockDrawApi)

      expect(wrapper.find('[data-testid="no-blocks-message"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="no-blocks-message"]').text()).toContain('No blocks configured')
    })
  })

  describe('Block CRUD operations', () => {
    it('opens create block dialog when add button clicked', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({ data: [] }),
        listBibPools: vi.fn().mockResolvedValue({ data: [] })
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
          .mockResolvedValueOnce({ data: [] })
          .mockResolvedValueOnce({
            data: [
              {
                id: 'block-new',
                name: 'Test Block',
                start_time: '2026-03-02T10:00:00Z',
                event_interval_seconds: 180,
                crew_interval_seconds: 60,
                display_order: 1
              }
            ]
          }),
        listBibPools: vi.fn().mockResolvedValue({ data: [] }),
        createBlock: vi.fn().mockResolvedValue({
          id: 'block-new',
          name: 'Test Block',
          start_time: '2026-03-02T10:00:00Z',
          event_interval_seconds: 180,
          crew_interval_seconds: 60
        })
      }

      const wrapper = await mountPage(mockDrawApi)

      await wrapper.find('[data-testid="add-block-button"]').trigger('click')

      await wrapper.find('input[name="block_name"]').setValue('Test Block')
      await wrapper.find('input[name="start_time"]').setValue('2026-03-02T10:00')
      await wrapper.find('input[name="event_interval_seconds"]').setValue('180')
      await wrapper.find('input[name="crew_interval_seconds"]').setValue('60')

      await wrapper.find('[data-testid="save-block-button"]').trigger('click')
      await flushPromises()

      expect(mockDrawApi.createBlock).toHaveBeenCalledWith(REGATTA_ID, {
        name: 'Test Block',
        start_time: new Date('2026-03-02T10:00').toISOString(),
        event_interval_seconds: 180,
        crew_interval_seconds: 60
      })

      expect(mockDrawApi.listBlocks).toHaveBeenCalledTimes(2)
    })

    it('shows validation errors for invalid block data', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({ data: [] }),
        listBibPools: vi.fn().mockResolvedValue({ data: [] })
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
            data: [
              {
                id: 'block-1',
                name: 'Morning Session',
                start_time: '2026-03-02T09:00:00Z',
                event_interval_seconds: 120,
                crew_interval_seconds: 30,
                display_order: 1
              }
            ]
          })
          .mockResolvedValueOnce({
            data: [
              {
                id: 'block-1',
                name: 'Updated Session',
                start_time: '2026-03-02T09:30:00Z',
                event_interval_seconds: 150,
                crew_interval_seconds: 40,
                display_order: 1
              }
            ]
          }),
        listBibPools: vi.fn().mockResolvedValue({ data: [] }),
        updateBlock: vi.fn().mockResolvedValue({})
      }

      const wrapper = await mountPage(mockDrawApi)

      const editButton = wrapper.find('[data-testid="edit-block-block-1"]')
      await editButton.trigger('click')

      await wrapper.find('input[name="block_name"]').setValue('Updated Session')
      await wrapper.find('input[name="start_time"]').setValue('2026-03-02T09:30')
      await wrapper.find('input[name="event_interval_seconds"]').setValue('150')
      await wrapper.find('input[name="crew_interval_seconds"]').setValue('40')

      await wrapper.find('[data-testid="save-block-button"]').trigger('click')
      await flushPromises()

      expect(mockDrawApi.updateBlock).toHaveBeenCalledWith(REGATTA_ID, 'block-1', {
        name: 'Updated Session',
        start_time: new Date('2026-03-02T09:30').toISOString(),
        event_interval_seconds: 150,
        crew_interval_seconds: 40
      })
    })

    it('deletes block with confirmation dialog', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn()
          .mockResolvedValueOnce({
            data: [
              {
                id: 'block-1',
                name: 'Morning Session',
                start_time: '2026-03-02T09:00:00Z',
                event_interval_seconds: 120,
                crew_interval_seconds: 30,
                display_order: 1
              }
            ]
          })
          .mockResolvedValueOnce({ data: [] }),
        listBibPools: vi.fn().mockResolvedValue({ data: [] }),
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
          data: [
            {
              id: 'block-1',
              name: 'Morning Session',
              start_time: '2026-03-02T09:00:00Z',
              event_interval_seconds: 120,
              crew_interval_seconds: 30,
              display_order: 1
            }
          ]
        }),
        listBibPools: vi.fn().mockResolvedValue({
          data: [
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
        listBlocks: vi.fn().mockResolvedValue({ data: [] }),
        listBibPools: vi.fn().mockResolvedValue({
          data: [
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
          data: [{ id: MORNING_BLOCK.id, name: 'Morning', start_time: MORNING_BLOCK.start_time, event_interval_seconds: MORNING_BLOCK.event_interval_seconds, crew_interval_seconds: MORNING_BLOCK.crew_interval_seconds }]
        }),
        listBibPools: vi.fn()
          .mockResolvedValueOnce({ data: [] })
          .mockResolvedValueOnce({
            data: [
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
          data: [{ id: MORNING_BLOCK.id, name: 'Morning', start_time: MORNING_BLOCK.start_time, event_interval_seconds: MORNING_BLOCK.event_interval_seconds, crew_interval_seconds: MORNING_BLOCK.crew_interval_seconds }]
        }),
        listBibPools: vi.fn()
          .mockResolvedValueOnce({ data: [] })
          .mockResolvedValueOnce({
            data: [
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
          data: [{ id: 'block-1', name: 'Morning', start_time: '2026-03-02T09:00:00Z', event_interval_seconds: 120, crew_interval_seconds: 30 }]
        }),
        listBibPools: vi.fn().mockResolvedValue({ data: [] }),
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

  describe('Block reordering via drag-and-drop (FEGAP-008-B1)', () => {
    it('displays drag handles for blocks when multiple blocks exist', async () => {
      const mockDrawApi = defaultDrawApi({
        listBlocks: vi.fn().mockResolvedValue(blocksResponse([MORNING_BLOCK, AFTERNOON_BLOCK]))
      })

      const wrapper = await mountPage(mockDrawApi)

      const dragHandles = wrapper.findAll('[data-testid^="drag-handle-"]')
      expect(dragHandles).toHaveLength(2)
      expect(dragHandles[0].attributes('draggable')).toBe('true')
      expect(dragHandles[0].attributes('aria-label')).toContain('Drag to reorder')
      expect(dragHandles[0].attributes('aria-describedby')).toBe('blocks-reorder-instructions')
    })

    it('does not display drag handles when only one block exists', async () => {
      const mockDrawApi = defaultDrawApi({
        listBlocks: vi.fn().mockResolvedValue(blocksResponse([MORNING_BLOCK]))
      })

      const wrapper = await mountPage(mockDrawApi)

      const dragHandles = wrapper.findAll('[data-testid^="drag-handle-"]')
      expect(dragHandles).toHaveLength(0)
    })

    it('successfully reorders blocks via drag-and-drop', async () => {
      const mockDrawApi = defaultDrawApi({
        listBlocks: vi.fn()
          .mockResolvedValueOnce(blocksResponse([MORNING_BLOCK, AFTERNOON_BLOCK]))
          .mockResolvedValueOnce(blocksResponse(REORDERED_BLOCKS)),
        reorderBlocks: vi.fn().mockResolvedValue(blocksResponse(REORDERED_BLOCKS))
      })

      const wrapper = await mountPage(mockDrawApi)

      await dragBlockOnto(wrapper, 'block-1', 'block-2')

      await flushPromises()

      expect(mockDrawApi.reorderBlocks).toHaveBeenCalledWith(REGATTA_ID, REORDER_PAYLOAD)

      expect(mockDrawApi.listBlocks).toHaveBeenCalledTimes(2)
    })

    it('restores original order on reorder API failure', async () => {
      const mockDrawApi = defaultDrawApi({
        listBlocks: vi.fn().mockResolvedValue(blocksResponse([MORNING_BLOCK, AFTERNOON_BLOCK])),
        reorderBlocks: vi.fn().mockRejectedValue(new Error('Network error'))
      })

      const wrapper = await mountPage(mockDrawApi)

      await dragBlockOnto(wrapper, 'block-1', 'block-2')

      await flushPromises()

      expect(mockDrawApi.reorderBlocks).toHaveBeenCalled()

      const errorBanner = wrapper.find('[role="alert"]')
      expect(errorBanner.exists()).toBe(true)
      expect(errorBanner.text()).toContain('Failed to save new block order')

      const blockItems = wrapper.findAll('[data-testid^="block-item-"]')
      expect(blockItems[0].attributes('data-testid')).toBe('block-item-block-1')
      expect(blockItems[1].attributes('data-testid')).toBe('block-item-block-2')
    })

    it('supports keyboard navigation for reordering blocks', async () => {
      const mockDrawApi = defaultDrawApi({
        listBlocks: vi.fn()
          .mockResolvedValueOnce(blocksResponse([MORNING_BLOCK, AFTERNOON_BLOCK]))
          .mockResolvedValueOnce(blocksResponse(REORDERED_BLOCKS)),
        reorderBlocks: vi.fn().mockResolvedValue({ data: [] })
      })

      const wrapper = await mountPage(mockDrawApi)

      const firstDragHandle = wrapper.find('[data-testid="drag-handle-block-1"]')

      await firstDragHandle.trigger('keydown', { key: 'Enter' })

      expect(firstDragHandle.attributes('aria-pressed')).toBe('true')

      await firstDragHandle.trigger('keydown', { key: 'ArrowDown' })

      expect(mockDrawApi.reorderBlocks).not.toHaveBeenCalled()

      await firstDragHandle.trigger('keydown', { key: 'Enter' })
      await flushPromises()

      expect(mockDrawApi.reorderBlocks).toHaveBeenCalledWith(REGATTA_ID, REORDER_PAYLOAD)
    })

    it('cancels keyboard reordering on Escape key', async () => {
      const mockDrawApi = defaultDrawApi({
        listBlocks: vi.fn().mockResolvedValue(blocksResponse([MORNING_BLOCK, AFTERNOON_BLOCK])),
        reorderBlocks: vi.fn()
      })

      const wrapper = await mountPage(mockDrawApi)

      const firstDragHandle = wrapper.find('[data-testid="drag-handle-block-1"]')

      await firstDragHandle.trigger('keydown', { key: 'Enter' })

      expect(firstDragHandle.attributes('aria-pressed')).toBe('true')

      await firstDragHandle.trigger('keydown', { key: 'ArrowDown' })

      await firstDragHandle.trigger('keydown', { key: 'Escape' })

      expect(firstDragHandle.attributes('aria-pressed')).toBe('false')

      expect(mockDrawApi.reorderBlocks).not.toHaveBeenCalled()
    })

    it('displays reorder instructions for accessibility', async () => {
      const mockDrawApi = defaultDrawApi({
        listBlocks: vi.fn().mockResolvedValue(blocksResponse([MORNING_BLOCK, AFTERNOON_BLOCK]))
      })

      const wrapper = await mountPage(mockDrawApi)

      const instructions = wrapper.find('[data-testid="reorder-instructions"]')
      expect(instructions.exists()).toBe(true)
      expect(instructions.attributes('id')).toBe('blocks-reorder-instructions')
      expect(instructions.text()).toContain('drag and drop')
      expect(instructions.text()).toContain('arrow keys')
    })
  })

  describe('Bib Pool Drag-and-Drop Reordering (FEGAP-008-B2)', () => {
    it('displays drag handles on bib pool items', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({
          data: [
            {
              id: 'block-1',
              name: 'Morning Session',
              start_time: '2026-03-02T09:00:00Z',
              event_interval_seconds: 120,
              crew_interval_seconds: 30,
              display_order: 1
            }
          ]
        }),
        listBibPools: vi.fn().mockResolvedValue({
          data: [
            {
              id: 'pool-1',
              name: 'Pool 1',
              block_id: 'block-1',
              allocation_mode: 'range',
              start_bib: 1,
              end_bib: 50,
              is_overflow: false,
              priority: 1
            },
            {
              id: 'pool-2',
              name: 'Pool 2',
              block_id: 'block-1',
              allocation_mode: 'range',
              start_bib: 51,
              end_bib: 100,
              is_overflow: false,
              priority: 2
            }
          ]
        })
      }

      const wrapper = await mountPage(mockDrawApi)

      const dragHandles = wrapper.findAll('[data-testid^="drag-handle-pool-"]')
      expect(dragHandles.length).toBeGreaterThan(0)

      const firstHandle = wrapper.find('[data-testid="drag-handle-pool-1"]')
      expect(firstHandle.exists()).toBe(true)
      expect(firstHandle.attributes('draggable')).toBe('true')
      expect(firstHandle.attributes('aria-label')).toContain('Drag to reorder')
    })

    it('reorders bib pools via drag and drop', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({
          data: [
            {
              id: 'block-1',
              name: 'Morning Session',
              start_time: '2026-03-02T09:00:00Z',
              event_interval_seconds: 120,
              crew_interval_seconds: 30,
              display_order: 1
            }
          ]
        }),
        listBibPools: vi.fn()
          .mockResolvedValueOnce({
            data: [
              {
                id: 'pool-1',
                name: 'Pool 1',
                block_id: 'block-1',
                allocation_mode: 'range',
                start_bib: 1,
                end_bib: 50,
                is_overflow: false,
                priority: 1
              },
              {
                id: 'pool-2',
                name: 'Pool 2',
                block_id: 'block-1',
                allocation_mode: 'range',
                start_bib: 51,
                end_bib: 100,
                is_overflow: false,
                priority: 2
              }
            ]
          })
          .mockResolvedValueOnce({
            data: [
              {
                id: 'pool-2',
                name: 'Pool 2',
                block_id: 'block-1',
                allocation_mode: 'range',
                start_bib: 51,
                end_bib: 100,
                is_overflow: false,
                priority: 1
              },
              {
                id: 'pool-1',
                name: 'Pool 1',
                block_id: 'block-1',
                allocation_mode: 'range',
                start_bib: 1,
                end_bib: 50,
                is_overflow: false,
                priority: 2
              }
            ]
          }),
        reorderBibPools: vi.fn().mockResolvedValue({ data: [] })
      }

      const wrapper = await mountPage(mockDrawApi)

      // Simulate drag start on pool-1
      const dragHandle1 = wrapper.find('[data-testid="drag-handle-pool-1"]')
      await dragHandle1.trigger('dragstart', {
        dataTransfer: { setData: vi.fn(), effectAllowed: '' }
      })

      // Simulate drop on pool-2
      const poolItem2 = wrapper.find('[data-testid="bib-pool-item-pool-2"]')
      await poolItem2.trigger('dragover', { preventDefault: vi.fn() })
      await poolItem2.trigger('drop', {
        preventDefault: vi.fn(),
        dataTransfer: { getData: vi.fn(() => 'pool-1') }
      })

      await flushPromises()

      expect(mockDrawApi.reorderBibPools).toHaveBeenCalledWith(REGATTA_ID, {
        items: [
          { bib_pool_id: 'pool-2', priority: 1 },
          { bib_pool_id: 'pool-1', priority: 2 }
        ]
      })

      expect(mockDrawApi.listBibPools).toHaveBeenCalledTimes(2)
    })

    it('supports keyboard-based reordering with arrow keys', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({
          data: [
            {
              id: 'block-1',
              name: 'Morning Session',
              start_time: '2026-03-02T09:00:00Z',
              event_interval_seconds: 120,
              crew_interval_seconds: 30,
              display_order: 1
            }
          ]
        }),
        listBibPools: vi.fn()
          .mockResolvedValueOnce({
            data: [
              {
                id: 'pool-1',
                name: 'Pool 1',
                block_id: 'block-1',
                allocation_mode: 'range',
                start_bib: 1,
                end_bib: 50,
                is_overflow: false,
                priority: 1
              },
              {
                id: 'pool-2',
                name: 'Pool 2',
                block_id: 'block-1',
                allocation_mode: 'range',
                start_bib: 51,
                end_bib: 100,
                is_overflow: false,
                priority: 2
              }
            ]
          })
          .mockResolvedValueOnce({
            data: [
              {
                id: 'pool-2',
                name: 'Pool 2',
                block_id: 'block-1',
                allocation_mode: 'range',
                start_bib: 51,
                end_bib: 100,
                is_overflow: false,
                priority: 1
              },
              {
                id: 'pool-1',
                name: 'Pool 1',
                block_id: 'block-1',
                allocation_mode: 'range',
                start_bib: 1,
                end_bib: 50,
                is_overflow: false,
                priority: 2
              }
            ]
          }),
        reorderBibPools: vi.fn().mockResolvedValue({ data: [] })
      }

      const wrapper = await mountPage(mockDrawApi)

      const dragHandle1 = wrapper.find('[data-testid="drag-handle-pool-1"]')

      // Activate keyboard mode with Space
      await dragHandle1.trigger('keydown', { key: ' ' })
      await flushPromises()
      expect(dragHandle1.attributes('aria-pressed')).toBe('true')
      expect(dragHandle1.classes()).toContain('keyboard-move-active')
      expect(wrapper.find('#move-mode-status-pool-1').exists()).toBe(true)

      // Move down with ArrowDown
      await dragHandle1.trigger('keydown', { key: 'ArrowDown' })
      await flushPromises()

      // Confirm with Enter
      await dragHandle1.trigger('keydown', { key: 'Enter' })
      await flushPromises()

      expect(mockDrawApi.reorderBibPools).toHaveBeenCalledWith(REGATTA_ID, {
        items: [
          { bib_pool_id: 'pool-2', priority: 1 },
          { bib_pool_id: 'pool-1', priority: 2 }
        ]
      })
    })

    it('cancels keyboard reordering with Escape', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({
          data: [
            {
              id: 'block-1',
              name: 'Morning Session',
              start_time: '2026-03-02T09:00:00Z',
              event_interval_seconds: 120,
              crew_interval_seconds: 30,
              display_order: 1
            }
          ]
        }),
        listBibPools: vi.fn().mockResolvedValue({
          data: [
            {
              id: 'pool-1',
              name: 'Pool 1',
              block_id: 'block-1',
              allocation_mode: 'range',
              start_bib: 1,
              end_bib: 50,
              is_overflow: false,
              priority: 1
            },
            {
              id: 'pool-2',
              name: 'Pool 2',
              block_id: 'block-1',
              allocation_mode: 'range',
              start_bib: 51,
              end_bib: 100,
              is_overflow: false,
              priority: 2
            }
          ]
        }),
        reorderBibPools: vi.fn().mockResolvedValue({ data: [] })
      }

      const wrapper = await mountPage(mockDrawApi)

      const dragHandle1 = wrapper.find('[data-testid="drag-handle-pool-1"]')

      // Activate keyboard mode with Space
      await dragHandle1.trigger('keydown', { key: ' ' })
      await flushPromises()

      // Move down with ArrowDown
      await dragHandle1.trigger('keydown', { key: 'ArrowDown' })
      await flushPromises()

      // Cancel with Escape
      await dragHandle1.trigger('keydown', { key: 'Escape' })
      await flushPromises()

      // Should not call reorder API
      expect(mockDrawApi.reorderBibPools).not.toHaveBeenCalled()
    })

    it('reverts optimistic update on reorder failure', async () => {
      let rejectReorderRequest
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({
          data: [
            {
              id: 'block-1',
              name: 'Morning Session',
              start_time: '2026-03-02T09:00:00Z',
              event_interval_seconds: 120,
              crew_interval_seconds: 30,
              display_order: 1
            }
          ]
        }),
        listBibPools: vi.fn().mockResolvedValue({
          data: [
            {
              id: 'pool-1',
              name: 'Pool 1',
              block_id: 'block-1',
              allocation_mode: 'range',
              start_bib: 1,
              end_bib: 50,
              is_overflow: false,
              priority: 1
            },
            {
              id: 'pool-2',
              name: 'Pool 2',
              block_id: 'block-1',
              allocation_mode: 'range',
              start_bib: 51,
              end_bib: 100,
              is_overflow: false,
              priority: 2
            }
          ]
        }),
        reorderBibPools: vi.fn().mockImplementation(() => new Promise((_, reject) => {
          rejectReorderRequest = reject
        }))
      }

      const wrapper = await mountPage(mockDrawApi)

      // Simulate drag and drop
      const dragHandle1 = wrapper.find('[data-testid="drag-handle-pool-1"]')
      await dragHandle1.trigger('dragstart', {
        dataTransfer: { setData: vi.fn(), effectAllowed: '' }
      })

      const poolItem2 = wrapper.find('[data-testid="bib-pool-item-pool-2"]')
      await poolItem2.trigger('dragover', { preventDefault: vi.fn() })
      await poolItem2.trigger('drop', {
        preventDefault: vi.fn(),
        dataTransfer: { getData: vi.fn(() => 'pool-1') }
      })

      await wrapper.vm.$nextTick()

      // Verify optimistic reorder changed rendered order before API error rollback
      let poolsList = wrapper.findAll('[data-testid^="bib-pool-item-pool-"]')
      expect(poolsList[0].attributes('data-testid')).toBe('bib-pool-item-pool-2')
      expect(poolsList[1].attributes('data-testid')).toBe('bib-pool-item-pool-1')

      rejectReorderRequest(new Error('Network error'))
      await flushPromises()

      // Check error message displayed
      const errorBanner = wrapper.find('.error-banner')
      expect(errorBanner.exists()).toBe(true)
      expect(errorBanner.text()).toContain('Failed to reorder')

      // Verify that the order is reverted (original order maintained)
      poolsList = wrapper.findAll('[data-testid^="bib-pool-item-pool-"]')
      expect(poolsList[0].attributes('data-testid')).toBe('bib-pool-item-pool-1')
      expect(poolsList[1].attributes('data-testid')).toBe('bib-pool-item-pool-2')
    })

    it('does not show drag handle on overflow pool', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({ data: [] }),
        listBibPools: vi.fn().mockResolvedValue({
          data: [
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

      const overflowHandle = wrapper.find('[data-testid="drag-handle-pool-overflow"]')
      expect(overflowHandle.exists()).toBe(false)
    })

    it('prevents overflow pool from being included in reorder operations', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({
          data: [
            {
              id: 'block-1',
              name: 'Morning Session',
              start_time: '2026-03-02T09:00:00Z',
              event_interval_seconds: 120,
              crew_interval_seconds: 30,
              display_order: 1
            }
          ]
        }),
        listBibPools: vi.fn().mockResolvedValue({
          data: [
            {
              id: 'pool-1',
              name: 'Pool 1',
              block_id: 'block-1',
              allocation_mode: 'range',
              start_bib: 1,
              end_bib: 50,
              is_overflow: false,
              priority: 1
            },
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
        }),
        reorderBibPools: vi.fn().mockResolvedValue({ data: [] })
      }

      const wrapper = await mountPage(mockDrawApi)

      // Only regular pool should have drag handle
      expect(wrapper.find('[data-testid="drag-handle-pool-1"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="drag-handle-pool-overflow"]').exists()).toBe(false)
    })

    it('preserves block assignment constraints during reorder', async () => {
      const mockDrawApi = {
        listBlocks: vi.fn().mockResolvedValue({
          data: [
            {
              id: 'block-1',
              name: 'Morning Session',
              start_time: '2026-03-02T09:00:00Z',
              event_interval_seconds: 120,
              crew_interval_seconds: 30,
              display_order: 1
            },
            {
              id: 'block-2',
              name: 'Afternoon Session',
              start_time: '2026-03-02T14:00:00Z',
              event_interval_seconds: 120,
              crew_interval_seconds: 30,
              display_order: 2
            }
          ]
        }),
        listBibPools: vi.fn()
          .mockResolvedValueOnce({
            data: [
              {
                id: 'pool-1',
                name: 'Pool 1',
                block_id: 'block-1',
                allocation_mode: 'range',
                start_bib: 1,
                end_bib: 50,
                is_overflow: false,
                priority: 1
              },
              {
                id: 'pool-2',
                name: 'Pool 2',
                block_id: 'block-1',
                allocation_mode: 'range',
                start_bib: 51,
                end_bib: 100,
                is_overflow: false,
                priority: 2
              },
              {
                id: 'pool-3',
                name: 'Pool 3',
                block_id: 'block-2',
                allocation_mode: 'range',
                start_bib: 101,
                end_bib: 150,
                is_overflow: false,
                priority: 3
              }
            ]
          })
          .mockResolvedValueOnce({
            data: [
              {
                id: 'pool-2',
                name: 'Pool 2',
                block_id: 'block-1',
                allocation_mode: 'range',
                start_bib: 51,
                end_bib: 100,
                is_overflow: false,
                priority: 1
              },
              {
                id: 'pool-1',
                name: 'Pool 1',
                block_id: 'block-1',
                allocation_mode: 'range',
                start_bib: 1,
                end_bib: 50,
                is_overflow: false,
                priority: 2
              },
              {
                id: 'pool-3',
                name: 'Pool 3',
                block_id: 'block-2',
                allocation_mode: 'range',
                start_bib: 101,
                end_bib: 150,
                is_overflow: false,
                priority: 3
              }
            ]
          }),
        reorderBibPools: vi.fn().mockResolvedValue({ data: [] })
      }

      const wrapper = await mountPage(mockDrawApi)

      // Drag pool-1 in block-1
      const dragHandle1 = wrapper.find('[data-testid="drag-handle-pool-1"]')
      await dragHandle1.trigger('dragstart', {
        dataTransfer: { setData: vi.fn(), effectAllowed: '' }
      })

      // Drag-over on a different block should not be treated as a valid drop target
      const crossBlockDragOverEvent = { preventDefault: vi.fn() }
      const poolItem3 = wrapper.find('[data-testid="bib-pool-item-pool-3"]')
      await poolItem3.trigger('dragover', crossBlockDragOverEvent)
      expect(crossBlockDragOverEvent.preventDefault).not.toHaveBeenCalled()

      // Drop on pool-2 in block-1
      const poolItem2 = wrapper.find('[data-testid="bib-pool-item-pool-2"]')
      await poolItem2.trigger('dragover', { preventDefault: vi.fn() })
      await poolItem2.trigger('drop', {
        preventDefault: vi.fn(),
        dataTransfer: { getData: vi.fn(() => 'pool-1') }
      })

      await flushPromises()

      // Should only reorder within block-1 (pool-1 and pool-2), not pool-3
      expect(mockDrawApi.reorderBibPools).toHaveBeenCalledWith(REGATTA_ID, {
        items: expect.arrayContaining([
          { bib_pool_id: 'pool-2', priority: 1 },
          { bib_pool_id: 'pool-1', priority: 2 }
        ])
      })

      // Pool-3 should maintain its priority since it's in a different block
      const call = mockDrawApi.reorderBibPools.mock.calls[0][1]
      const pool3Item = call.items.find(item => item.bib_pool_id === 'pool-3')
      expect(pool3Item.priority).toBe(3)
    })
  })
})
