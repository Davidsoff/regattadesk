<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError, createApiClient, createOperatorApi } from '../../api'
import { resolveOperatorToken } from '../../operatorContext.js'

const props = defineProps({
  captureSessionId: {
    type: String,
    required: true
  },
  regattaId: {
    type: String,
    required: true
  }
})

const { t } = useI18n()

const markers = ref([])
const errorMessage = ref('')
const isLoading = ref(false)
const editingMarkerId = ref(null)
const editingFrameOffset = ref('')
const linkingMarkerId = ref(null)
const linkingEntryId = ref('')
const undoStack = ref([])

const operatorApi = createOperatorApi(createApiClient(), {
  getOperatorToken: () => resolveOperatorToken()
})

const sortedMarkers = computed(() => {
  // Sort: unlinked markers first, then linked, then by frame_offset
  return [...markers.value].sort((a, b) => {
    if (a.is_linked !== b.is_linked) {
      return a.is_linked ? 1 : -1
    }
    return a.frame_offset - b.frame_offset
  })
})

const hasMarkers = computed(() => markers.value.length > 0)

function hasCaptureSession() {
  return typeof props.captureSessionId === 'string' && props.captureSessionId.trim().length > 0
}

function resolveTileMetadata(frameOffset) {
  const frame = globalThis.__REGATTADESK_CAPTURE_FRAME__
  if (
    frame &&
    typeof frame.tile_id === 'string' &&
    Number.isInteger(frame.tile_x) &&
    Number.isInteger(frame.tile_y)
  ) {
    return {
      tile_id: frame.tile_id,
      tile_x: frame.tile_x,
      tile_y: frame.tile_y
    }
  }

  return {
    tile_id: `${props.captureSessionId}-${frameOffset}`,
    tile_x: frameOffset % 1024,
    tile_y: Math.floor(frameOffset / 1024)
  }
}

async function loadMarkers() {
  if (!hasCaptureSession()) {
    errorMessage.value = t('operator.capture.errors.capture_session_required')
    return
  }

  if (isLoading.value) {
    return
  }

  errorMessage.value = ''
  isLoading.value = true

  try {
    const result = await operatorApi.listMarkers(props.regattaId, {
      capture_session_id: props.captureSessionId
    })
    markers.value = result || []
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('operator.capture.errors.failed_load_markers')
  } finally {
    isLoading.value = false
  }
}

async function createMarker() {
  if (!hasCaptureSession()) {
    errorMessage.value = t('operator.capture.errors.capture_session_required')
    return
  }

  if (isLoading.value) {
    return
  }

  errorMessage.value = ''
  isLoading.value = true

  try {
    const frameOffset = Date.now() % 100000
    const tileMetadata = resolveTileMetadata(frameOffset)
    const newMarker = await operatorApi.createMarker(props.regattaId, {
      capture_session_id: props.captureSessionId,
      frame_offset: frameOffset,
      timestamp_ms: Date.now(),
      tile_id: tileMetadata.tile_id,
      tile_x: tileMetadata.tile_x,
      tile_y: tileMetadata.tile_y
    })
    markers.value.push(newMarker)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('operator.capture.errors.failed_create_marker')
  } finally {
    isLoading.value = false
  }
}

async function deleteMarker(markerId) {
  if (isLoading.value) {
    return
  }

  const marker = markers.value.find((m) => m.id === markerId)
  if (marker?.is_approved) {
    errorMessage.value = t('operator.capture.errors.marker_approved')
    return
  }

  errorMessage.value = ''
  isLoading.value = true

  try {
    await operatorApi.deleteMarker(props.regattaId, markerId)
    markers.value = markers.value.filter((m) => m.id !== markerId)
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      errorMessage.value =
        error.code === 'MARKER_APPROVED' ? t('operator.capture.errors.marker_approved') : error.message
    } else {
      errorMessage.value = error instanceof Error ? error.message : t('operator.capture.errors.failed_delete_marker')
    }
  } finally {
    isLoading.value = false
  }
}

function startEditing(markerId) {
  const marker = markers.value.find((m) => m.id === markerId)
  if (marker && !marker.is_approved) {
    editingMarkerId.value = markerId
    editingFrameOffset.value = String(marker.frame_offset)
  }
}

async function updateMarker(markerId) {
  if (isLoading.value) {
    return
  }

  const marker = markers.value.find((m) => m.id === markerId)
  if (!marker) {
    return
  }

  const oldFrameOffset = marker.frame_offset // Save for undo

  errorMessage.value = ''
  isLoading.value = true

  try {
    const updated = await operatorApi.updateMarker(props.regattaId, markerId, {
      frame_offset: Number.parseInt(editingFrameOffset.value, 10)
    })

    const index = markers.value.findIndex((m) => m.id === markerId)
    if (index !== -1) {
      markers.value[index] = updated
    }

    // Save state for undo only after successful update
    undoStack.value.push({
      markerId,
      oldFrameOffset
    })

    editingMarkerId.value = null
    editingFrameOffset.value = ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('operator.capture.errors.failed_update_marker')
  } finally {
    isLoading.value = false
  }
}

function startLinking(markerId) {
  linkingMarkerId.value = markerId
  linkingEntryId.value = ''
}

async function linkMarker(markerId) {
  if (isLoading.value) {
    return
  }

  const normalizedEntryId = linkingEntryId.value.trim()
  if (!normalizedEntryId) {
    errorMessage.value = t('operator.capture.errors.entry_id_required')
    return
  }

  if (!/^[A-Za-z0-9-]{1,64}$/.test(normalizedEntryId)) {
    errorMessage.value = t('operator.capture.errors.entry_id_invalid')
    return
  }

  errorMessage.value = ''
  isLoading.value = true

  try {
    const linked = await operatorApi.linkMarker(props.regattaId, markerId, {
      entry_id: normalizedEntryId
    })

    const index = markers.value.findIndex((m) => m.id === markerId)
    if (index !== -1) {
      markers.value[index] = linked
    }

    linkingMarkerId.value = null
    linkingEntryId.value = ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('operator.capture.errors.failed_link_marker')
  } finally {
    isLoading.value = false
  }
}

async function unlinkMarker(markerId) {
  if (isLoading.value) {
    return
  }

  const marker = markers.value.find((m) => m.id === markerId)
  if (marker?.is_approved) {
    errorMessage.value = t('operator.capture.errors.marker_approved')
    return
  }

  errorMessage.value = ''
  isLoading.value = true

  try {
    const unlinked = await operatorApi.unlinkMarker(props.regattaId, markerId)

    const index = markers.value.findIndex((m) => m.id === markerId)
    if (index !== -1) {
      markers.value[index] = unlinked
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('operator.capture.errors.failed_unlink_marker')
  } finally {
    isLoading.value = false
  }
}

async function undoLastChange() {
  if (undoStack.value.length === 0 || isLoading.value) {
    return
  }

  const lastChange = undoStack.value.pop()
  errorMessage.value = ''
  isLoading.value = true

  try {
    const updated = await operatorApi.updateMarker(props.regattaId, lastChange.markerId, {
      frame_offset: lastChange.oldFrameOffset
    })

    const index = markers.value.findIndex((m) => m.id === lastChange.markerId)
    if (index !== -1) {
      markers.value[index] = updated
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('operator.capture.errors.failed_undo')
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  loadMarkers()
})

defineExpose({
  deleteMarker // Exposed for testing error handling
})
</script>

<template>
  <div class="line-scan-capture">
    <h2>{{ t('operator.capture.title') }}</h2>

    <div class="capture-controls">
      <button
        type="button"
        data-testid="create-marker-button"
        @click="createMarker"
        :disabled="isLoading"
      >
        {{ t('operator.capture.create_marker') }}
      </button>
      
      <button
        v-if="undoStack.length > 0"
        type="button"
        data-testid="undo-last-change"
        @click="undoLastChange"
        :disabled="isLoading"
      >
        {{ t('operator.capture.undo') }}
      </button>
    </div>

    <div v-if="errorMessage" data-testid="error-message" class="error-message">
      {{ errorMessage }}
    </div>

    <div data-testid="capture-markers-list" class="markers-list">
      <div v-if="!hasMarkers" class="empty-state">
        {{ t('operator.capture.no_markers') }}
      </div>

      <div
        v-for="marker in sortedMarkers"
        :key="marker.id"
        :data-testid="`marker-item-${marker.id}`"
        class="marker-item"
        :class="{ 'marker-approved': marker.is_approved }"
      >
        <div class="marker-info">
          <span>{{ t('operator.capture.frame_offset') }}: {{ marker.frame_offset }}</span>
          
          <span v-if="marker.is_linked && marker.entry_id">
            {{ t('operator.capture.bib_number') }}: {{ marker.entry_id }}
          </span>
          
          <span v-if="marker.is_approved" :data-testid="`marker-locked-${marker.id}`" class="marker-locked">
            {{ t('operator.capture.locked') }}
          </span>
        </div>

        <div class="marker-actions">
          <!-- Edit controls -->
          <div v-if="editingMarkerId === marker.id" class="edit-controls">
            <input
              type="number"
              data-testid="marker-frame-input"
              v-model="editingFrameOffset"
              :disabled="isLoading"
            />
            <button
              type="button"
              :data-testid="`update-marker-${marker.id}`"
              @click="updateMarker(marker.id)"
              :disabled="isLoading"
            >
              Save
            </button>
            <button
              type="button"
              @click="editingMarkerId = null"
              :disabled="isLoading"
            >
              Cancel
            </button>
          </div>
          
          <button
            v-else-if="!marker.is_approved"
            type="button"
            :data-testid="`edit-marker-${marker.id}`"
            @click="startEditing(marker.id)"
            :disabled="isLoading"
          >
            Edit
          </button>

          <!-- Link controls -->
          <div v-if="linkingMarkerId === marker.id" class="link-controls">
            <input
              type="text"
              data-testid="link-entry-input"
              v-model="linkingEntryId"
              placeholder="Entry ID"
              :disabled="isLoading"
            />
            <button
              type="button"
              data-testid="link-entry-submit"
              @click="linkMarker(marker.id)"
              :disabled="isLoading || !linkingEntryId"
            >
              {{ t('operator.capture.link_to_bib') }}
            </button>
            <button
              type="button"
              @click="linkingMarkerId = null"
              :disabled="isLoading"
            >
              Cancel
            </button>
          </div>
          
          <button
            v-else-if="!marker.is_linked"
            type="button"
            :data-testid="`link-marker-${marker.id}`"
            @click="startLinking(marker.id)"
            :disabled="isLoading"
          >
            {{ t('operator.capture.link_to_bib') }}
          </button>

          <button
            v-if="marker.is_linked && !marker.is_approved"
            type="button"
            :data-testid="`unlink-marker-${marker.id}`"
            @click="unlinkMarker(marker.id)"
            :disabled="isLoading"
          >
            {{ t('operator.capture.unlink') }}
          </button>

          <button
            type="button"
            :data-testid="`delete-marker-${marker.id}`"
            @click="deleteMarker(marker.id)"
            :disabled="isLoading || marker.is_approved"
          >
            {{ t('operator.capture.delete_marker') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.line-scan-capture {
  padding: var(--rd-space-3, 1rem);
}

.line-scan-capture h2 {
  margin-bottom: var(--rd-space-3, 1rem);
  font-size: 1.75rem;
}

.capture-controls {
  display: flex;
  gap: var(--rd-space-2, 0.5rem);
  margin-bottom: var(--rd-space-3, 1rem);
}

.error-message {
  padding: var(--rd-space-2, 0.5rem);
  margin-bottom: var(--rd-space-3, 1rem);
  border: 1px solid #b30000;
  background: #ffebee;
  color: #b30000;
}

.markers-list {
  display: flex;
  flex-direction: column;
  gap: var(--rd-space-2, 0.5rem);
}

.empty-state {
  padding: var(--rd-space-3, 1rem);
  text-align: center;
  color: var(--rd-color-text-secondary, #666);
}

.marker-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--rd-space-2, 0.5rem);
  border: 1px solid var(--rd-color-border, #ccc);
  border-radius: 4px;
}

.marker-item.marker-approved {
  background: var(--rd-color-surface-subtle, #f5f5f5);
  border-color: var(--rd-color-border-emphasis, #999);
}

.marker-info {
  display: flex;
  gap: var(--rd-space-2, 0.5rem);
  align-items: center;
}

.marker-locked {
  padding: 2px 8px;
  background: var(--rd-color-warning-soft, #fff7d1);
  border: 1px solid var(--rd-color-warning, #e1b100);
  border-radius: 4px;
  font-size: 0.875rem;
  font-weight: 600;
}

.marker-actions {
  display: flex;
  gap: var(--rd-space-2, 0.5rem);
  align-items: center;
}

.edit-controls,
.link-controls {
  display: flex;
  gap: var(--rd-space-1, 0.25rem);
  align-items: center;
}

button {
  padding: 4px 12px;
  border: 1px solid var(--rd-color-border, #ccc);
  border-radius: 4px;
  background: var(--rd-color-surface, #fff);
  cursor: pointer;
}

button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

button:hover:not(:disabled) {
  background: var(--rd-color-surface-hover, #f5f5f5);
}

input[type="number"],
input[type="text"] {
  padding: 4px 8px;
  border: 1px solid var(--rd-color-border, #ccc);
  border-radius: 4px;
  width: 120px;
}
</style>
