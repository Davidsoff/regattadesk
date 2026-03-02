<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { createApiClient, createDrawApi } from '../../api'
import { validateRouteParam } from './financeViewShared'

const { t } = useI18n()
const route = useRoute()

// API setup
const apiClient = createApiClient()
const drawApi = createDrawApi(apiClient)

// State
const blocks = ref([])
const bibPools = ref([])
const loading = ref(true)
const error = ref(null)

// Drag-and-drop state
const draggedBlockId = ref(null)
const dragOverBlockId = ref(null)
const keyboardReorderMode = ref(false)
const keyboardReorderBlockId = ref(null)
const keyboardReorderTargetIndex = ref(-1)
const reorderError = ref(null)

// Dialog state
const showBlockDialog = ref(false)
const showBibPoolDialog = ref(false)
const showDeleteDialog = ref(false)

// Form state
const blockForm = ref({
  id: null,
  name: '',
  start_time: '',
  event_interval_seconds: '',
  crew_interval_seconds: ''
})

const bibPoolForm = ref({
  id: null,
  name: '',
  block_id: '',
  allocation_mode: 'range',
  start_bib: '',
  end_bib: '',
  bib_numbers: '',
  is_overflow: false
})

const blockValidationErrors = ref({})
const bibPoolValidationErrors = ref({})
const bibPoolError = ref(null)
const deleteTarget = ref(null)
const deleteType = ref(null)

// Route state
const regattaId = validateRouteParam(route.params.regattaId, 'regattaId')
const hasValidRouteParams = Boolean(regattaId)
const blockDialog = ref(null)
const bibPoolDialog = ref(null)
const deleteDialog = ref(null)
const lastFocusedElement = ref(null)

const overflowPool = computed(() => {
  return bibPools.value.find(pool => pool.is_overflow)
})

const canReorderBlocks = computed(() => {
  return blocks.value.length > 1
})

const regularBibPools = computed(() => {
  return bibPools.value.filter(pool => !pool.is_overflow)
})

function getBibPoolsForBlock(blockId) {
  return regularBibPools.value.filter(pool => pool.block_id === blockId)
}

function parseInteger(value) {
  return Number.parseInt(value, 10)
}

function validateRangeAllocation(errors) {
  const startBib = parseInteger(bibPoolForm.value.start_bib)
  const endBib = parseInteger(bibPoolForm.value.end_bib)

  if (!bibPoolForm.value.start_bib) {
    errors.start_bib = t('blocks.validation.start_bib_required')
  } else if (Number.isNaN(startBib) || startBib <= 0) {
    errors.start_bib = t('blocks.validation.start_bib_positive')
  }

  if (!bibPoolForm.value.end_bib) {
    errors.end_bib = t('blocks.validation.end_bib_required')
  } else if (Number.isNaN(endBib) || endBib <= 0) {
    errors.end_bib = t('blocks.validation.end_bib_positive')
  } else if (endBib < startBib) {
    errors.end_bib = t('blocks.validation.end_bib_greater')
  }
}

function validateExplicitListAllocation(errors) {
  const bibNumbers = bibPoolForm.value.bib_numbers?.trim()

  if (bibNumbers) {
    const numbers = bibNumbers
      .split(',')
      .map(n => n.trim())
      .filter(Boolean)
    const parsedNumbers = numbers.map(Number)
    const allValid = parsedNumbers.every(n => Number.isInteger(n) && n >= 1)
    const hasDuplicates = new Set(parsedNumbers).size !== parsedNumbers.length
    if (!allValid || hasDuplicates) {
      errors.bib_numbers = t('blocks.validation.bib_numbers_format')
    }
    return
  }

  errors.bib_numbers = t('blocks.validation.bib_numbers_required')
}

// API functions
async function loadBlocks() {
  try {
    const response = await drawApi.listBlocks(regattaId)
    blocks.value = response.data || []
  } catch (err) {
    error.value = t('common.error')
    console.error('Failed to load blocks:', err)
  }
}

async function loadBibPools() {
  try {
    const response = await drawApi.listBibPools(regattaId)
    bibPools.value = response.data || []
  } catch (err) {
    error.value = t('common.error')
    console.error('Failed to load bib pools:', err)
  }
}

async function loadData() {
  if (!hasValidRouteParams) {
    error.value = t('finance.invalid_route_params')
    loading.value = false
    return
  }

  loading.value = true
  error.value = null
  await Promise.all([loadBlocks(), loadBibPools()])
  loading.value = false
}

// Block operations
function openAddBlockDialog() {
  lastFocusedElement.value = document.activeElement
  blockForm.value = {
    id: null,
    name: '',
    start_time: '',
    event_interval_seconds: '',
    crew_interval_seconds: ''
  }
  blockValidationErrors.value = {}
  showBlockDialog.value = true
}

function openEditBlockDialog(block) {
  lastFocusedElement.value = document.activeElement
  blockForm.value = {
    id: block.id,
    name: block.name,
    start_time: apiDateTimeToLocalInput(block.start_time),
    event_interval_seconds: block.event_interval_seconds,
    crew_interval_seconds: block.crew_interval_seconds
  }
  blockValidationErrors.value = {}
  showBlockDialog.value = true
}

function validateBlockForm() {
  const errors = {}

  if (!blockForm.value.name?.trim()) {
    errors.name = t('blocks.validation.block_name_required')
  }

  if (!blockForm.value.start_time?.trim()) {
    errors.start_time = t('blocks.validation.start_time_required')
  } else if (!localDateTimeToIso(blockForm.value.start_time)) {
    errors.start_time = t('blocks.validation.start_time_format')
  }

  const eventInterval = parseInteger(blockForm.value.event_interval_seconds)
  if (!blockForm.value.event_interval_seconds) {
    errors.event_interval_seconds = t('blocks.validation.event_interval_required')
  } else if (Number.isNaN(eventInterval) || eventInterval <= 0) {
    errors.event_interval_seconds = t('blocks.validation.event_interval_positive')
  }

  const crewInterval = parseInteger(blockForm.value.crew_interval_seconds)
  if (!blockForm.value.crew_interval_seconds) {
    errors.crew_interval_seconds = t('blocks.validation.crew_interval_required')
  } else if (Number.isNaN(crewInterval) || crewInterval <= 0) {
    errors.crew_interval_seconds = t('blocks.validation.crew_interval_positive')
  }

  blockValidationErrors.value = errors
  return Object.keys(errors).length === 0
}

async function saveBlock() {
  if (!validateBlockForm()) {
    return
  }

  try {
    const payload = {
      name: blockForm.value.name,
      start_time: localDateTimeToIso(blockForm.value.start_time),
      event_interval_seconds: parseInteger(blockForm.value.event_interval_seconds),
      crew_interval_seconds: parseInteger(blockForm.value.crew_interval_seconds)
    }

    if (blockForm.value.id) {
      await drawApi.updateBlock(regattaId, blockForm.value.id, payload)
    } else {
      await drawApi.createBlock(regattaId, payload)
    }

    closeBlockDialog()
    await loadBlocks()
  } catch (err) {
    error.value = t('common.error')
    console.error('Failed to save block:', err)
  }
}

function openDeleteBlockDialog(block) {
  lastFocusedElement.value = document.activeElement
  deleteTarget.value = block
  deleteType.value = 'block'
  showDeleteDialog.value = true
}

async function confirmDelete() {
  try {
    if (deleteType.value === 'block') {
      await drawApi.deleteBlock(regattaId, deleteTarget.value.id)
      await loadBlocks()
    } else if (deleteType.value === 'bib_pool') {
      await drawApi.deleteBibPool(regattaId, deleteTarget.value.id)
      await loadBibPools()
    }

    closeDeleteDialog()
  } catch (err) {
    error.value = t('common.error')
    console.error('Failed to delete:', err)
  }
}

// Bib Pool operations
function openAddBibPoolDialog() {
  lastFocusedElement.value = document.activeElement
  bibPoolForm.value = {
    id: null,
    name: '',
    block_id: '',
    allocation_mode: 'range',
    start_bib: '',
    end_bib: '',
    bib_numbers: '',
    is_overflow: false
  }
  bibPoolValidationErrors.value = {}
  bibPoolError.value = null
  showBibPoolDialog.value = true
}

function openEditBibPoolDialog(pool) {
  lastFocusedElement.value = document.activeElement
  bibPoolForm.value = {
    id: pool.id,
    name: pool.name,
    block_id: pool.block_id || '',
    allocation_mode: pool.allocation_mode,
    start_bib: pool.start_bib || '',
    end_bib: pool.end_bib || '',
    bib_numbers: pool.bib_numbers ? pool.bib_numbers.join(', ') : '',
    is_overflow: pool.is_overflow
  }
  bibPoolValidationErrors.value = {}
  bibPoolError.value = null
  showBibPoolDialog.value = true
}

function validateBibPoolForm() {
  const errors = {}

  if (!bibPoolForm.value.name?.trim()) {
    errors.name = t('blocks.validation.pool_name_required')
  }

  if (!bibPoolForm.value.allocation_mode) {
    errors.allocation_mode = t('blocks.validation.allocation_mode_required')
  }

  if (!bibPoolForm.value.is_overflow && !bibPoolForm.value.block_id) {
    errors.block_id = t('blocks.validation.block_assignment_required')
  }

  if (bibPoolForm.value.allocation_mode === 'range') {
    validateRangeAllocation(errors)
  } else if (bibPoolForm.value.allocation_mode === 'explicit_list') {
    validateExplicitListAllocation(errors)
  }

  bibPoolValidationErrors.value = errors
  return Object.keys(errors).length === 0
}

async function saveBibPool() {
  if (!validateBibPoolForm()) {
    return
  }

  bibPoolError.value = null

  try {
    const payload = {
      name: bibPoolForm.value.name,
      allocation_mode: bibPoolForm.value.allocation_mode,
      is_overflow: bibPoolForm.value.is_overflow
    }

    if (!bibPoolForm.value.is_overflow && bibPoolForm.value.block_id) {
      payload.block_id = bibPoolForm.value.block_id
    }

    if (bibPoolForm.value.allocation_mode === 'range') {
      payload.start_bib = parseInteger(bibPoolForm.value.start_bib)
      payload.end_bib = parseInteger(bibPoolForm.value.end_bib)
    } else {
      payload.bib_numbers = bibPoolForm.value.bib_numbers
        .split(',')
        .map(n => parseInteger(n.trim()))
        .filter(n => !Number.isNaN(n))
    }

    if (bibPoolForm.value.id) {
      await drawApi.updateBibPool(regattaId, bibPoolForm.value.id, payload)
    } else {
      await drawApi.createBibPool(regattaId, payload)
    }

    closeBibPoolDialog()
    await loadBibPools()
  } catch (err) {
    if (err.code === 'BIB_POOL_VALIDATION_ERROR') {
      bibPoolError.value = {
        message: err.message,
        overlapping_bibs: err.details?.overlapping_bibs || [],
        conflicting_pool: err.details?.conflicting_pool_name || ''
      }
    } else {
      error.value = t('common.error')
      console.error('Failed to save bib pool:', err)
    }
  }
}

function openDeleteBibPoolDialog(pool) {
  lastFocusedElement.value = document.activeElement
  deleteTarget.value = pool
  deleteType.value = 'bib_pool'
  showDeleteDialog.value = true
}

function formatBibPoolDisplay(pool) {
  if (pool.allocation_mode === 'range') {
    return `${pool.start_bib} - ${pool.end_bib}`
  } else {
    return pool.bib_numbers.join(', ')
  }
}

function apiDateTimeToLocalInput(value) {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }

  const year = String(date.getFullYear())
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

function localDateTimeToIso(value) {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }

  return date.toISOString()
}

function formatStartTimeDisplay(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleString()
}

function closeBlockDialog() {
  showBlockDialog.value = false
}

function closeBibPoolDialog() {
  showBibPoolDialog.value = false
}

function closeDeleteDialog() {
  showDeleteDialog.value = false
  deleteTarget.value = null
  deleteType.value = null
}

function getActiveDialog() {
  if (showBlockDialog.value) {
    return blockDialog.value
  }
  if (showBibPoolDialog.value) {
    return bibPoolDialog.value
  }
  if (showDeleteDialog.value) {
    return deleteDialog.value
  }
  return null
}

function dialogFocusableElements(dialog) {
  if (!dialog) {
    return []
  }

  return [...dialog.querySelectorAll('button, input, select, textarea, [tabindex]:not([tabindex="-1"])')]
    .filter(element => !element.hasAttribute('disabled'))
}

function onDialogKeydown(event) {
  const activeDialog = getActiveDialog()
  if (!activeDialog) {
    return
  }

  if (event.key === 'Escape') {
    event.preventDefault()
    if (showBlockDialog.value) {
      closeBlockDialog()
    } else if (showBibPoolDialog.value) {
      closeBibPoolDialog()
    } else if (showDeleteDialog.value) {
      closeDeleteDialog()
    }
    return
  }

  if (event.key !== 'Tab') {
    return
  }

  const focusable = dialogFocusableElements(activeDialog)
  if (!focusable.length) {
    event.preventDefault()
    return
  }

  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  const active = document.activeElement

  if (event.shiftKey) {
    if (active === first || !activeDialog.contains(active)) {
      event.preventDefault()
      last.focus()
    }
    return
  }

  if (active === last) {
    event.preventDefault()
    first.focus()
  }
}

watch(() => bibPoolForm.value.is_overflow, (isOverflow) => {
  if (isOverflow) {
    bibPoolForm.value.block_id = ''
  }
})

watch(
  () => showBlockDialog.value || showBibPoolDialog.value || showDeleteDialog.value,
  async (isOpen) => {
    if (isOpen) {
      await nextTick()
      const activeDialog = getActiveDialog()
      const focusable = dialogFocusableElements(activeDialog)
      ;(focusable[0] || activeDialog)?.focus()
      return
    }

    await nextTick()
    lastFocusedElement.value?.focus?.()
  }
)

// Drag-and-drop handlers
function onDragStart(event, blockId) {
  draggedBlockId.value = blockId
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', blockId)
}

function onDragOver(event, blockId) {
  event.preventDefault()
  dragOverBlockId.value = blockId
}

function onDragLeave(event) {
  const currentTarget = event?.currentTarget
  const nextTarget = event?.relatedTarget

  if (!currentTarget || !nextTarget || !currentTarget.contains(nextTarget)) {
    dragOverBlockId.value = null
  }
}

function onDrop(event, targetBlockId) {
  event.preventDefault()
  dragOverBlockId.value = null

  const sourceBlockId = event.dataTransfer.getData('text/plain') || draggedBlockId.value
  if (!sourceBlockId || sourceBlockId === targetBlockId) {
    return
  }

  reorderBlocksAfterDrop(sourceBlockId, targetBlockId)
}

function onDragEnd() {
  draggedBlockId.value = null
  dragOverBlockId.value = null
}

async function reorderBlocksAfterDrop(sourceBlockId, targetBlockId) {
  const currentBlocks = [...blocks.value]
  const sourceIndex = currentBlocks.findIndex(b => b.id === sourceBlockId)
  const targetIndex = currentBlocks.findIndex(b => b.id === targetBlockId)

  if (sourceIndex === -1 || targetIndex === -1 || sourceIndex === targetIndex) {
    return
  }

  await applyReorderAndPersist(sourceIndex, targetIndex)
}

// Keyboard reordering handlers
function onKeyboardReorderStart(event, blockId, index) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    keyboardReorderMode.value = true
    keyboardReorderBlockId.value = blockId
    keyboardReorderTargetIndex.value = index
  }
}

function onKeyboardReorderMove(event, blockId, currentIndex) {
  if (!keyboardReorderMode.value || keyboardReorderBlockId.value !== blockId) {
    return
  }

  if (event.key === 'ArrowUp' && keyboardReorderTargetIndex.value > 0) {
    event.preventDefault()
    keyboardReorderTargetIndex.value = keyboardReorderTargetIndex.value - 1
  } else if (event.key === 'ArrowDown' && keyboardReorderTargetIndex.value < blocks.value.length - 1) {
    event.preventDefault()
    keyboardReorderTargetIndex.value = keyboardReorderTargetIndex.value + 1
  } else if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    commitKeyboardReorder(currentIndex)
  } else if (event.key === 'Escape') {
    event.preventDefault()
    cancelKeyboardReorder()
  }
}

async function commitKeyboardReorder(sourceIndex) {
  const targetIndex = keyboardReorderTargetIndex.value
  
  // Check if we're in reorder mode and if the position actually changed
  if (!keyboardReorderMode.value || sourceIndex === targetIndex) {
    keyboardReorderMode.value = false
    keyboardReorderBlockId.value = null
    keyboardReorderTargetIndex.value = -1
    return
  }

  // Clear reorder mode state
  keyboardReorderMode.value = false
  keyboardReorderBlockId.value = null
  keyboardReorderTargetIndex.value = -1

  await applyReorderAndPersist(sourceIndex, targetIndex)
}

async function applyReorderAndPersist(sourceIndex, targetIndex) {
  const currentBlocks = [...blocks.value]
  const reorderedBlocks = [...currentBlocks]
  const [movedBlock] = reorderedBlocks.splice(sourceIndex, 1)
  reorderedBlocks.splice(targetIndex, 0, movedBlock)

  blocks.value = reorderedBlocks

  const payload = {
    items: reorderedBlocks.map((block, index) => ({
      block_id: block.id,
      display_order: index + 1
    }))
  }

  try {
    reorderError.value = null
    await drawApi.reorderBlocks(regattaId, payload)
    await loadBlocks()
  } catch (err) {
    reorderError.value = t('blocks.reorder_error')
    blocks.value = currentBlocks
    console.error('Failed to reorder blocks:', err)
  }
}

function handleDragHandleKeydown(event, blockId, index) {
  if (keyboardReorderMode.value && keyboardReorderBlockId.value === blockId) {
    onKeyboardReorderMove(event, blockId, index)
  } else {
    onKeyboardReorderStart(event, blockId, index)
  }
}

function cancelKeyboardReorder() {
  keyboardReorderMode.value = false
  keyboardReorderBlockId.value = null
  keyboardReorderTargetIndex.value = -1
}

// Lifecycle
onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="blocks-management">
    <h2>{{ t('blocks.title') }}</h2>
    <p class="subtitle">{{ t('blocks.subtitle') }}</p>

    <div v-if="error" class="error-banner" role="alert">
      {{ error }}
    </div>

    <div v-if="reorderError" class="error-banner" role="alert">
      {{ reorderError }}
    </div>

    <div v-if="loading" class="loading">{{ t('common.loading') }}</div>

    <div v-if="!loading">
      <!-- Blocks Section -->
      <section class="blocks-section">
        <div class="section-header">
          <h3>{{ t('blocks.blocks_section') }}</h3>
          <button
            type="button"
            data-testid="add-block-button"
            @click="openAddBlockDialog"
          >
            {{ t('blocks.add_block') }}
          </button>
        </div>

        <div
          v-if="canReorderBlocks"
          id="blocks-reorder-instructions"
          data-testid="reorder-instructions"
          class="reorder-instructions"
        >
          {{ t('blocks.reorder_instructions') }}
        </div>

        <div v-if="blocks.length === 0" data-testid="no-blocks-message">
          {{ t('blocks.no_blocks') }}
        </div>

        <div v-else data-testid="blocks-list">
          <div
            v-for="(block, index) in blocks"
            :key="block.id"
            :data-testid="`block-item-${block.id}`"
            class="block-item"
            :class="{ 'drag-over': dragOverBlockId === block.id }"
            @dragover="onDragOver($event, block.id)"
            @dragleave="onDragLeave($event)"
            @drop="onDrop($event, block.id)"
          >
            <div class="block-header">
              <div class="block-title-row">
                <button
                  v-if="canReorderBlocks"
                  type="button"
                  class="drag-handle"
                  :data-testid="`drag-handle-${block.id}`"
                  :aria-label="t('blocks.drag_handle')"
                  :aria-pressed="keyboardReorderMode && keyboardReorderBlockId === block.id ? 'true' : 'false'"
                  aria-describedby="blocks-reorder-instructions"
                  :title="t('blocks.reorder_keyboard_hint')"
                  draggable="true"
                  @dragstart="onDragStart($event, block.id)"
                  @dragend="onDragEnd"
                  @keydown="handleDragHandleKeydown($event, block.id, index)"
                >
                  <span aria-hidden="true">⋮⋮</span>
                </button>
                <h4>{{ block.name }}</h4>
              </div>
              <div class="block-actions">
                <button
                  type="button"
                  :data-testid="`edit-block-${block.id}`"
                  @click="openEditBlockDialog(block)"
                >
                  {{ t('common.edit') }}
                </button>
                <button
                  type="button"
                  :data-testid="`delete-block-${block.id}`"
                  @click="openDeleteBlockDialog(block)"
                >
                  {{ t('common.delete') }}
                </button>
              </div>
            </div>
            <div class="block-details">
              <div><strong>{{ t('blocks.block.start_time') }}:</strong> {{ formatStartTimeDisplay(block.start_time) }}</div>
              <div><strong>{{ t('blocks.block.event_interval') }}:</strong> {{ block.event_interval_seconds }}s</div>
              <div><strong>{{ t('blocks.block.crew_interval') }}:</strong> {{ block.crew_interval_seconds }}s</div>
            </div>

            <!-- Bib Pools for this block -->
            <div :data-testid="`bib-pools-${block.id}`" class="block-bib-pools">
              <h5>{{ t('blocks.bib_pools_section') }}</h5>
              <div v-if="getBibPoolsForBlock(block.id).length === 0">
                {{ t('blocks.no_bib_pools') }}
              </div>
              <div
                v-for="pool in getBibPoolsForBlock(block.id)"
                :key="pool.id"
                :data-testid="`bib-pool-item-${pool.id}`"
                class="bib-pool-item"
              >
                <div class="pool-header">
                  <span class="pool-name">{{ pool.name }}</span>
                  <span class="pool-range">{{ formatBibPoolDisplay(pool) }}</span>
                  <div class="pool-actions">
                    <button
                      type="button"
                      :data-testid="`edit-bib-pool-${pool.id}`"
                      @click="openEditBibPoolDialog(pool)"
                    >
                      {{ t('common.edit') }}
                    </button>
                    <button
                      type="button"
                      :data-testid="`delete-bib-pool-${pool.id}`"
                      @click="openDeleteBibPoolDialog(pool)"
                    >
                      {{ t('common.delete') }}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Overflow Pool Section -->
      <section class="overflow-section" data-testid="overflow-pool-section">
        <div class="section-header">
          <h3>{{ t('blocks.overflow_pool_section') }}</h3>
          <button
            type="button"
            data-testid="add-bib-pool-button"
            @click="openAddBibPoolDialog"
          >
            {{ t('blocks.add_bib_pool') }}
          </button>
        </div>

        <div v-if="!overflowPool">
          {{ t('blocks.no_overflow_pool') }}
        </div>

        <div
          v-if="overflowPool"
          :data-testid="`bib-pool-item-${overflowPool.id}`"
          class="bib-pool-item"
        >
          <div class="pool-header">
            <span class="pool-name">{{ overflowPool.name }}</span>
            <span class="pool-range">{{ formatBibPoolDisplay(overflowPool) }}</span>
            <div class="pool-actions">
              <button
                type="button"
                :data-testid="`edit-bib-pool-${overflowPool.id}`"
                @click="openEditBibPoolDialog(overflowPool)"
              >
                {{ t('common.edit') }}
              </button>
              <button
                type="button"
                :data-testid="`delete-bib-pool-${overflowPool.id}`"
                @click="openDeleteBibPoolDialog(overflowPool)"
              >
                {{ t('common.delete') }}
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- Block Dialog -->
    <dialog
      v-if="showBlockDialog"
      open
      ref="blockDialog"
      data-testid="block-dialog"
      aria-modal="true"
      class="modal-dialog"
      @keydown="onDialogKeydown"
    >
      <h3>{{ blockForm.id ? t('blocks.edit_block') : t('blocks.add_block') }}</h3>

      <form @submit.prevent="saveBlock">
        <div class="form-group">
          <label for="block_name">{{ t('blocks.block.name') }}</label>
          <input
            id="block_name"
            v-model="blockForm.name"
            name="block_name"
            type="text"
          />
          <span v-if="blockValidationErrors.name" class="error">{{ blockValidationErrors.name }}</span>
        </div>

        <div class="form-group">
          <label for="start_time">{{ t('blocks.block.start_time') }}</label>
          <input
            id="start_time"
            v-model="blockForm.start_time"
            name="start_time"
            type="datetime-local"
          />
          <span v-if="blockValidationErrors.start_time" class="error">{{ blockValidationErrors.start_time }}</span>
        </div>

        <div class="form-group">
          <label for="event_interval_seconds">{{ t('blocks.block.event_interval') }}</label>
          <input
            id="event_interval_seconds"
            v-model="blockForm.event_interval_seconds"
            name="event_interval_seconds"
            type="number"
          />
          <span v-if="blockValidationErrors.event_interval_seconds" class="error">{{ blockValidationErrors.event_interval_seconds }}</span>
        </div>

        <div class="form-group">
          <label for="crew_interval_seconds">{{ t('blocks.block.crew_interval') }}</label>
          <input
            id="crew_interval_seconds"
            v-model="blockForm.crew_interval_seconds"
            name="crew_interval_seconds"
            type="number"
          />
          <span v-if="blockValidationErrors.crew_interval_seconds" class="error">{{ blockValidationErrors.crew_interval_seconds }}</span>
        </div>

        <div v-if="Object.keys(blockValidationErrors).length > 0" data-testid="block-validation-errors" class="validation-errors">
          <p>{{ t('common.error') }}: {{ Object.values(blockValidationErrors)[0] }}</p>
        </div>

        <div class="dialog-actions">
          <button type="submit" data-testid="save-block-button">{{ t('common.save') }}</button>
          <button type="button" @click="closeBlockDialog">{{ t('common.cancel') }}</button>
        </div>
      </form>
    </dialog>

    <!-- Bib Pool Dialog -->
    <dialog
      v-if="showBibPoolDialog"
      open
      ref="bibPoolDialog"
      data-testid="bib-pool-dialog"
      aria-modal="true"
      class="modal-dialog"
      @keydown="onDialogKeydown"
    >
      <h3>{{ bibPoolForm.id ? t('blocks.edit_bib_pool') : t('blocks.add_bib_pool') }}</h3>

      <form @submit.prevent="saveBibPool">
        <div class="form-group">
          <label for="pool_name">{{ t('blocks.bib_pool.name') }}</label>
          <input
            id="pool_name"
            v-model="bibPoolForm.name"
            name="pool_name"
            type="text"
          />
          <span v-if="bibPoolValidationErrors.name" class="error">{{ bibPoolValidationErrors.name }}</span>
        </div>

        <div class="form-group">
          <label for="block_id">{{ t('blocks.bib_pool.block_assignment') }}</label>
          <select
            id="block_id"
            v-model="bibPoolForm.block_id"
            name="block_id"
            :disabled="bibPoolForm.is_overflow"
          >
            <option value="">{{ t('blocks.bib_pool.unassigned_option') }}</option>
            <option v-for="block in blocks" :key="block.id" :value="block.id">
              {{ block.name }}
            </option>
          </select>
          <span v-if="bibPoolValidationErrors.block_id" class="error">{{ bibPoolValidationErrors.block_id }}</span>
        </div>

        <div class="form-group">
          <label for="is_overflow">{{ t('blocks.bib_pool.is_overflow') }}</label>
          <input
            id="is_overflow"
            v-model="bibPoolForm.is_overflow"
            name="is_overflow"
            type="checkbox"
          />
        </div>

        <div class="form-group">
          <label for="allocation_mode">{{ t('blocks.bib_pool.allocation_mode') }}</label>
          <select
            id="allocation_mode"
            v-model="bibPoolForm.allocation_mode"
            name="allocation_mode"
          >
            <option value="range">{{ t('blocks.bib_pool.allocation_mode_range') }}</option>
            <option value="explicit_list">{{ t('blocks.bib_pool.allocation_mode_explicit') }}</option>
          </select>
        </div>

        <div v-if="bibPoolForm.allocation_mode === 'range'" class="form-row">
          <div class="form-group">
            <label for="start_bib">{{ t('blocks.bib_pool.start_bib') }}</label>
            <input
              id="start_bib"
              v-model="bibPoolForm.start_bib"
              name="start_bib"
              type="number"
            />
            <span v-if="bibPoolValidationErrors.start_bib" class="error">{{ bibPoolValidationErrors.start_bib }}</span>
          </div>

          <div class="form-group">
            <label for="end_bib">{{ t('blocks.bib_pool.end_bib') }}</label>
            <input
              id="end_bib"
              v-model="bibPoolForm.end_bib"
              name="end_bib"
              type="number"
            />
            <span v-if="bibPoolValidationErrors.end_bib" class="error">{{ bibPoolValidationErrors.end_bib }}</span>
          </div>
        </div>

        <div v-if="bibPoolForm.allocation_mode === 'explicit_list'" class="form-group">
          <label for="bib_numbers">{{ t('blocks.bib_pool.bib_numbers') }}</label>
          <input
            id="bib_numbers"
            v-model="bibPoolForm.bib_numbers"
            name="bib_numbers"
            type="text"
            :placeholder="t('blocks.bib_pool.bib_numbers_placeholder')"
          />
          <span v-if="bibPoolValidationErrors.bib_numbers" class="error">{{ bibPoolValidationErrors.bib_numbers }}</span>
        </div>

        <div v-if="bibPoolError" data-testid="bib-pool-error" class="error-banner">
          <p>{{ t('blocks.validation.bib_overlap') }}: {{ bibPoolError.message }}</p>
          <p v-if="bibPoolError.overlapping_bibs.length > 0">
            {{ t('blocks.validation.bib_overlap_details', {
              bibs: bibPoolError.overlapping_bibs.join(', '),
              poolName: bibPoolError.conflicting_pool
            }) }}
          </p>
        </div>

        <div class="dialog-actions">
          <button type="submit" data-testid="save-bib-pool-button">{{ t('common.save') }}</button>
          <button type="button" @click="closeBibPoolDialog">{{ t('common.cancel') }}</button>
        </div>
      </form>
    </dialog>

    <!-- Delete Confirmation Dialog -->
    <dialog
      v-if="showDeleteDialog"
      open
      ref="deleteDialog"
      data-testid="confirm-delete-dialog"
      aria-modal="true"
      class="modal-dialog"
      @keydown="onDialogKeydown"
    >
      <h3>{{ t('common.confirm') }}</h3>
      <p>
        {{ deleteType === 'block' ? t('blocks.delete_block_confirm') : t('blocks.delete_bib_pool_confirm') }}
      </p>
      <div class="dialog-actions">
        <button type="button" data-testid="confirm-delete-button" @click="confirmDelete">
          {{ t('common.delete') }}
        </button>
        <button type="button" @click="closeDeleteDialog">{{ t('common.cancel') }}</button>
      </div>
    </dialog>
  </div>
</template>

<style scoped>
.blocks-management {
  padding: var(--rd-space-4);
}

.subtitle {
  color: var(--rd-text-secondary, #666);
  margin-bottom: var(--rd-space-4);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--rd-space-3);
}

.block-item {
  border: 1px solid var(--rd-border, #ddd);
  border-radius: var(--rd-radius-md, 4px);
  padding: var(--rd-space-3);
  margin-bottom: var(--rd-space-3);
}

.block-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--rd-space-2);
}

.block-title-row {
  display: flex;
  align-items: center;
  gap: var(--rd-space-2);
}

.drag-handle {
  cursor: grab;
  padding: var(--rd-space-1);
  background: transparent;
  border: 1px solid var(--rd-border, #ddd);
  border-radius: var(--rd-radius-sm, 2px);
  font-size: 1.2rem;
  line-height: 1;
  color: var(--rd-text-secondary, #666);
  transition: background-color 0.2s;
}

.drag-handle:hover {
  background: var(--rd-bg-secondary, #f5f5f5);
}

.drag-handle:active,
.drag-handle[aria-pressed="true"] {
  cursor: grabbing;
  background: var(--rd-bg-tertiary, #e0e0e0);
}

.drag-handle:focus {
  outline: 2px solid var(--rd-primary, #1976d2);
  outline-offset: 2px;
}

.block-item.drag-over {
  border: 2px dashed var(--rd-primary, #1976d2);
  background: var(--rd-bg-hover, #f0f7ff);
}

.reorder-instructions {
  font-size: 0.875rem;
  color: var(--rd-text-secondary, #666);
  margin-bottom: var(--rd-space-2);
  padding: var(--rd-space-2);
  background: var(--rd-bg-info, #e3f2fd);
  border-radius: var(--rd-radius-sm, 2px);
}

.block-actions,
.pool-actions {
  display: flex;
  gap: var(--rd-space-2);
}

.block-details {
  display: flex;
  gap: var(--rd-space-4);
  margin-bottom: var(--rd-space-3);
}

.block-bib-pools {
  margin-top: var(--rd-space-3);
  padding-top: var(--rd-space-3);
  border-top: 1px solid var(--rd-border, #eee);
}

.bib-pool-item {
  padding: var(--rd-space-2);
  background: var(--rd-bg-secondary, #f9f9f9);
  border-radius: var(--rd-radius-sm, 2px);
  margin-bottom: var(--rd-space-2);
}

.pool-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--rd-space-2);
}

.pool-name {
  font-weight: bold;
}

.pool-range {
  color: var(--rd-text-secondary, #666);
}

.modal-dialog {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: white;
  border: 1px solid var(--rd-border, #ddd);
  border-radius: var(--rd-radius-md, 4px);
  padding: var(--rd-space-4);
  max-width: 500px;
  width: 90%;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
}

.form-group {
  margin-bottom: var(--rd-space-3);
}

.form-group label {
  display: block;
  margin-bottom: var(--rd-space-1);
  font-weight: 500;
}

.form-group input,
.form-group select {
  width: 100%;
  padding: var(--rd-space-2);
  border: 1px solid var(--rd-border, #ddd);
  border-radius: var(--rd-radius-sm, 2px);
}

.form-row {
  display: flex;
  gap: var(--rd-space-3);
}

.form-row .form-group {
  flex: 1;
}

.dialog-actions {
  display: flex;
  gap: var(--rd-space-2);
  justify-content: flex-end;
  margin-top: var(--rd-space-4);
}

.error {
  color: var(--rd-error, #d32f2f);
  font-size: 0.875rem;
  margin-top: var(--rd-space-1);
  display: block;
}

.error-banner {
  background: var(--rd-error-bg, #ffebee);
  color: var(--rd-error, #d32f2f);
  padding: var(--rd-space-3);
  border-radius: var(--rd-radius-sm, 2px);
  margin-bottom: var(--rd-space-3);
}

.validation-errors {
  background: var(--rd-warning-bg, #fff3e0);
  color: var(--rd-warning, #f57c00);
  padding: var(--rd-space-2);
  border-radius: var(--rd-radius-sm, 2px);
  margin-bottom: var(--rd-space-3);
}

.loading {
  text-align: center;
  padding: var(--rd-space-4);
}
</style>
