<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { createApiClient, createOperatorApi } from '../../api'
import { useOfflineQueue } from '../../composables/useOfflineQueue'
import { useOfflineSync } from '../../composables/useOfflineSync'
import { useOperatorTheme } from '../../composables/useOperatorTheme'
import { normalizeCaptureSession, summarizeCaptureSessionSyncState } from '../../operatorCaptureSessions'
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

const emit = defineEmits(['queue-state-change'])

const { t } = useI18n()

const markers = ref([])
const errorMessage = ref('')
const isLoading = ref(false)
const editingMarkerId = ref(null)
const editingFrameOffset = ref('')
const linkingMarkerId = ref(null)
const linkingEntryId = ref('')
const undoStack = ref([])
const captureSession = ref(null)
const captureSessionErrorMessage = ref('')
const isSessionLoading = ref(false)
const selectedMarkerId = ref(null)
const detailCenterFrame = ref(0)
const detailZoomStep = ref('medium')
const attachedEvidence = ref(null)
const devEvidenceFrameOffset = ref('0')
const devEvidenceTileId = ref('')
const devEvidenceTileX = ref('')
const devEvidenceTileY = ref('')
const queueItems = ref([])
const isQueueSyncing = ref(false)
const isOnline = ref(typeof navigator === 'undefined' ? true : navigator.onLine)

const { isHighContrast, toggleContrast } = useOperatorTheme()
const { queueSize, enqueue, dequeue, getQueue, updateQueueItem } = useOfflineQueue()
const { syncQueue } = useOfflineSync()

const operatorToken = computed(() => resolveOperatorToken())
const operatorApi = createOperatorApi(createApiClient(), {
  getOperatorToken: () => operatorToken.value
})

const zoomWindowSizes = {
  wide: 1200,
  medium: 480,
  fine: 180
}

function normalizeMarkerListResponse(result) {
  if (Array.isArray(result)) {
    return result
  }

  if (Array.isArray(result?.data)) {
    return result.data
  }

  return []
}

function parseNonNegativeInteger(value) {
  if (typeof value === 'number' && Number.isInteger(value) && value >= 0) {
    return value
  }

  if (typeof value !== 'string' || value.trim().length === 0) {
    return null
  }

  const parsed = Number.parseInt(value, 10)
  if (!Number.isInteger(parsed) || parsed < 0) {
    return null
  }

  return parsed
}

function formatTimestamp(timestampMs) {
  if (!Number.isFinite(timestampMs)) {
    return '—'
  }

  return new Date(timestampMs).toISOString()
}

function createTempMarkerId() {
  return `temp-marker-${Date.now()}-${Math.round(Math.random() * 10000)}`
}

function hasCaptureSession() {
  return typeof props.captureSessionId === 'string' && props.captureSessionId.trim().length > 0
}

function deriveTimestampMs(frameOffset) {
  const startTime = captureSession.value?.server_time_at_start
  const fps = captureSession.value?.fps
  if (!startTime || !Number.isFinite(Number(fps)) || Number(fps) <= 0) {
    return null
  }

  const startMs = Date.parse(startTime)
  if (!Number.isFinite(startMs)) {
    return null
  }

  return Math.round(startMs + (frameOffset / Number(fps)) * 1000)
}

function readAttachedEvidenceFrame() {
  const frame = globalThis.__REGATTADESK_CAPTURE_FRAME__
  const frameOffset = parseNonNegativeInteger(frame?.frame_offset)
  if (frameOffset === null) {
    attachedEvidence.value = null
    return
  }

  attachedEvidence.value = {
    source: 'attached',
    frameOffset,
    timestampMs: Number.isFinite(frame?.timestamp_ms) ? frame.timestamp_ms : deriveTimestampMs(frameOffset),
    tileId: typeof frame?.tile_id === 'string' && frame.tile_id.trim().length > 0 ? frame.tile_id.trim() : null,
    tileX: parseNonNegativeInteger(frame?.tile_x),
    tileY: parseNonNegativeInteger(frame?.tile_y)
  }

  if (!selectedMarkerId.value) {
    detailCenterFrame.value = frameOffset
  }
}

const currentEvidence = computed(() => {
  if (attachedEvidence.value) {
    return attachedEvidence.value
  }

  const frameOffset = parseNonNegativeInteger(devEvidenceFrameOffset.value)
  if (frameOffset === null) {
    return null
  }

  const timestampMs = deriveTimestampMs(frameOffset)
  if (!Number.isFinite(timestampMs)) {
    return null
  }

  return {
    source: 'development',
    frameOffset,
    timestampMs,
    tileId: devEvidenceTileId.value.trim() || null,
    tileX: parseNonNegativeInteger(devEvidenceTileX.value),
    tileY: parseNonNegativeInteger(devEvidenceTileY.value)
  }
})

const sortedMarkers = computed(() => {
  return [...markers.value].sort((a, b) => {
    if (a.is_linked !== b.is_linked) {
      return a.is_linked ? 1 : -1
    }

    return a.frame_offset - b.frame_offset
  })
})

const selectedMarker = computed(() => {
  return markers.value.find((marker) => marker.id === selectedMarkerId.value) ?? null
})

const detailWindowSize = computed(() => {
  return zoomWindowSizes[detailZoomStep.value] ?? zoomWindowSizes.medium
})

const frameBounds = computed(() => {
  const frameOffsets = markers.value.map((marker) => marker.frame_offset)
  if (currentEvidence.value) {
    frameOffsets.push(currentEvidence.value.frameOffset)
  }

  if (frameOffsets.length === 0) {
    return { min: 0, max: detailWindowSize.value }
  }

  const min = Math.max(0, Math.min(...frameOffsets) - 120)
  const max = Math.max(min + 1, Math.max(...frameOffsets) + 120)
  return { min, max }
})

const visibleMarkers = computed(() => {
  const halfWindow = detailWindowSize.value / 2
  return sortedMarkers.value.filter((marker) => {
    return Math.abs(marker.frame_offset - detailCenterFrame.value) <= halfWindow
  })
})

const captureSessionStatusText = computed(() => {
  if (!captureSession.value) {
    return ''
  }

  return summarizeCaptureSessionSyncState(captureSession.value, t)
})

const captureSessionStatusClass = computed(() => {
  if (!captureSession.value) {
    return 'sync-ok'
  }

  if (captureSession.value.drift_exceeded_threshold) {
    return 'sync-attention'
  }

  return captureSession.value.is_synced === false ? 'sync-pending' : 'sync-ok'
})

const pendingConflicts = computed(() => {
  return queueItems.value.filter((item) => item.status === 'conflict')
})

const hasMarkers = computed(() => markers.value.length > 0)
const hasAttachedEvidence = computed(() => Boolean(attachedEvidence.value))
const canCreateMarker = computed(() => Boolean(currentEvidence.value) && !isLoading.value)

function buildQueueSummary(items) {
  return {
    queuedCount: items.length,
    failedCount: items.filter((item) => item.status === 'failed').length,
    conflictCount: items.filter((item) => item.status === 'conflict').length
  }
}

async function refreshQueueItems() {
  const queue = await getQueue()
  queueItems.value = [...queue].sort((a, b) => a.timestamp - b.timestamp)
  emit('queue-state-change', buildQueueSummary(queueItems.value))
}

function upsertMarker(marker) {
  const normalizedMarker = {
    ...marker,
    local_sync_state: marker.local_sync_state ?? null
  }
  const index = markers.value.findIndex((candidate) => candidate.id === normalizedMarker.id)
  if (index === -1) {
    markers.value.push(normalizedMarker)
    return
  }

  markers.value[index] = {
    ...markers.value[index],
    ...normalizedMarker
  }
}

function replaceMarkerId(previousId, nextMarker) {
  const index = markers.value.findIndex((marker) => marker.id === previousId)
  if (index === -1) {
    upsertMarker(nextMarker)
  } else {
    markers.value[index] = nextMarker
  }

  if (selectedMarkerId.value === previousId) {
    selectedMarkerId.value = nextMarker.id
  }
}

function markMarkerSyncState(markerId, syncState) {
  const marker = markers.value.find((candidate) => candidate.id === markerId)
  if (!marker) {
    return
  }

  marker.local_sync_state = syncState
}

function removeMarker(markerId) {
  markers.value = markers.value.filter((marker) => marker.id !== markerId)
  if (selectedMarkerId.value === markerId) {
    selectedMarkerId.value = null
  }
}

async function loadMarkers() {
  if (!hasCaptureSession() || isLoading.value) {
    return
  }

  errorMessage.value = ''
  isLoading.value = true

  try {
    const result = await operatorApi.listMarkers(props.regattaId, {
      capture_session_id: props.captureSessionId
    })
    markers.value = normalizeMarkerListResponse(result)

    if (selectedMarkerId.value && !markers.value.some((marker) => marker.id === selectedMarkerId.value)) {
      selectedMarkerId.value = null
    }

    if (!selectedMarkerId.value && markers.value.length > 0) {
      selectedMarkerId.value = sortedMarkers.value[0].id
      detailCenterFrame.value = sortedMarkers.value[0].frame_offset
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('operator.capture.errors.failed_load_markers')
  } finally {
    isLoading.value = false
  }
}

async function loadCaptureSession() {
  if (!hasCaptureSession() || isSessionLoading.value) {
    return
  }

  captureSessionErrorMessage.value = ''
  isSessionLoading.value = true

  try {
    captureSession.value = normalizeCaptureSession(
      await operatorApi.getCaptureSession(props.regattaId, props.captureSessionId)
    )

    if (!captureSession.value) {
      captureSessionErrorMessage.value = t('operator.capture.errors.failed_load_session_status')
      return
    }

    if (!selectedMarkerId.value && currentEvidence.value) {
      detailCenterFrame.value = currentEvidence.value.frameOffset
    }
  } catch {
    captureSessionErrorMessage.value = t('operator.capture.errors.failed_load_session_status')
  } finally {
    isSessionLoading.value = false
  }
}

function selectMarker(marker) {
  selectedMarkerId.value = marker.id
  detailCenterFrame.value = marker.frame_offset
}

function focusFirstUnlinkedMarker() {
  const marker = sortedMarkers.value.find((candidate) => !candidate.is_linked)
  if (!marker) {
    return
  }

  selectMarker(marker)
}

function markerPositionStyle(marker) {
  const span = Math.max(1, frameBounds.value.max - frameBounds.value.min)
  const percent = ((marker.frame_offset - frameBounds.value.min) / span) * 100
  return {
    left: `${Math.min(100, Math.max(0, percent))}%`
  }
}

function detailWindowStyle() {
  const span = Math.max(1, frameBounds.value.max - frameBounds.value.min)
  const widthPercent = (detailWindowSize.value / span) * 100
  const centerPercent = ((detailCenterFrame.value - frameBounds.value.min) / span) * 100
  const leftPercent = centerPercent - widthPercent / 2

  return {
    width: `${Math.min(100, Math.max(8, widthPercent))}%`,
    left: `${Math.min(100, Math.max(0, leftPercent))}%`
  }
}

function describeQueueItem(item) {
  switch (item.type) {
    case 'CREATE_MARKER':
      return t('operator.capture.queue_action_create')
    case 'UPDATE_MARKER':
      return t('operator.capture.queue_action_update')
    case 'LINK_MARKER':
      return t('operator.capture.queue_action_link')
    case 'UNLINK_MARKER':
      return t('operator.capture.queue_action_unlink')
    case 'DELETE_MARKER':
      return t('operator.capture.queue_action_delete')
    case 'APPROVE_MARKER':
      return t('operator.capture.queue_action_approve')
    default:
      return item.type
  }
}

function queueStatusLabel(status) {
  switch (status) {
    case 'syncing':
      return t('operator.capture.queue_status_syncing')
    case 'failed':
      return t('operator.capture.queue_status_failed')
    case 'conflict':
      return t('operator.capture.queue_status_conflict')
    default:
      return t('operator.capture.queue_status_queued')
  }
}

function conflictHelperText(item) {
  if (item.limitation === 'backend-no-force-override') {
    return t('operator.capture.queue_policy_lww_unavailable')
  }

  if (item.limitation === 'missing-server-timestamp') {
    return t('operator.capture.queue_policy_missing_server_timestamp')
  }

  return t('operator.capture.queue_policy_manual')
}

async function queueMutation(operation, applyOptimistic) {
  if (!operatorToken.value) {
    errorMessage.value = t('operator.capture.errors.operator_token_required')
    return
  }

  const queueId = await enqueue({
    ...operation,
    timestamp: Date.now(),
    status: 'queued',
    attempts: 0,
    maxRetries: 10,
    headers: {
      'X-Operator-Token': operatorToken.value
    }
  })

  applyOptimistic(queueId)
  await refreshQueueItems()

  if (isOnline.value) {
    await syncQueuedOperations([queueId])
  }
}

function buildMarkerPatchPayload(frameOffset) {
  const timestampMs = deriveTimestampMs(frameOffset)
  if (!Number.isFinite(timestampMs)) {
    return null
  }

  return {
    frame_offset: frameOffset,
    timestamp_ms: timestampMs
  }
}

async function createMarker() {
  if (!hasCaptureSession()) {
    errorMessage.value = t('operator.capture.errors.capture_session_required')
    return
  }

  if (!captureSession.value) {
    await loadCaptureSession()
  }

  if (!captureSession.value?.server_time_at_start || !captureSession.value?.fps) {
    errorMessage.value = t('operator.capture.errors.capture_session_timing_missing')
    return
  }

  if (!currentEvidence.value) {
    errorMessage.value = t('operator.capture.errors.live_source_unavailable')
    return
  }

  const tempMarkerId = createTempMarkerId()
  const evidence = currentEvidence.value
  errorMessage.value = ''

  await queueMutation(
    {
      type: 'CREATE_MARKER',
      endpoint: `/api/v1/regattas/${props.regattaId}/operator/markers`,
      method: 'POST',
      data: {
        capture_session_id: props.captureSessionId,
        frame_offset: evidence.frameOffset,
        timestamp_ms: evidence.timestampMs,
        tile_id: evidence.tileId,
        tile_x: evidence.tileX,
        tile_y: evidence.tileY
      },
      conflictStrategy: 'manual',
      metadata: {
        tempMarkerId
      }
    },
    () => {
      upsertMarker({
        id: tempMarkerId,
        capture_session_id: props.captureSessionId,
        entry_id: null,
        frame_offset: evidence.frameOffset,
        timestamp_ms: evidence.timestampMs,
        is_linked: false,
        is_approved: false,
        tile_id: evidence.tileId,
        tile_x: evidence.tileX,
        tile_y: evidence.tileY,
        local_sync_state: 'queued'
      })
      selectMarker({ id: tempMarkerId, frame_offset: evidence.frameOffset })
    }
  )
}

async function deleteMarker(markerId) {
  const marker = markers.value.find((candidate) => candidate.id === markerId)
  if (!marker) {
    errorMessage.value = t('operator.capture.errors.marker_not_found')
    return
  }

  if (marker.is_approved) {
    errorMessage.value = t('operator.capture.errors.marker_approved')
    return
  }

  errorMessage.value = ''

  await queueMutation(
    {
      type: 'DELETE_MARKER',
      endpoint: `/api/v1/regattas/${props.regattaId}/operator/markers/${markerId}`,
      method: 'DELETE',
      conflictStrategy: 'manual',
      metadata: {
        markerId
      }
    },
    () => {
      removeMarker(markerId)
    }
  )
}

function startEditing(markerId) {
  const marker = markers.value.find((candidate) => candidate.id === markerId)
  if (!marker || marker.is_approved) {
    return
  }

  editingMarkerId.value = markerId
  editingFrameOffset.value = String(marker.frame_offset)
}

async function updateMarker(markerId) {
  const marker = markers.value.find((candidate) => candidate.id === markerId)
  if (!marker) {
    errorMessage.value = t('operator.capture.errors.marker_not_found')
    return
  }

  if (marker.is_approved) {
    errorMessage.value = t('operator.capture.errors.marker_approved')
    return
  }

  const frameOffset = parseNonNegativeInteger(editingFrameOffset.value)
  if (frameOffset === null) {
    errorMessage.value = t('operator.capture.errors.frame_offset_invalid')
    return
  }

  const payload = buildMarkerPatchPayload(frameOffset)
  if (!payload) {
    errorMessage.value = t('operator.capture.errors.capture_session_timing_missing')
    return
  }

  const oldFrameOffset = marker.frame_offset
  errorMessage.value = ''

  await queueMutation(
    {
      type: 'UPDATE_MARKER',
      endpoint: `/api/v1/regattas/${props.regattaId}/operator/markers/${markerId}`,
      method: 'PATCH',
      data: payload,
      conflictStrategy: 'last-write-wins',
      supportsForceOverride: false,
      metadata: {
        markerId
      }
    },
    () => {
      upsertMarker({
        ...marker,
        ...payload,
        local_sync_state: 'queued'
      })
      undoStack.value.push({ markerId, oldFrameOffset })
      editingMarkerId.value = null
      editingFrameOffset.value = ''
      detailCenterFrame.value = frameOffset
    }
  )
}

function startLinking(markerId) {
  const marker = markers.value.find((candidate) => candidate.id === markerId)
  if (!marker || marker.is_approved) {
    errorMessage.value = t('operator.capture.errors.marker_approved')
    return
  }

  linkingMarkerId.value = markerId
  linkingEntryId.value = marker.entry_id || ''
}

async function linkMarker(markerId) {
  const marker = markers.value.find((candidate) => candidate.id === markerId)
  if (!marker) {
    errorMessage.value = t('operator.capture.errors.marker_not_found')
    return
  }

  if (marker.is_approved) {
    errorMessage.value = t('operator.capture.errors.marker_approved')
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

  await queueMutation(
    {
      type: 'LINK_MARKER',
      endpoint: `/api/v1/regattas/${props.regattaId}/operator/markers/${markerId}/link`,
      method: 'POST',
      data: {
        entry_id: normalizedEntryId
      },
      conflictStrategy: 'manual',
      metadata: {
        markerId
      }
    },
    () => {
      upsertMarker({
        ...marker,
        entry_id: normalizedEntryId,
        is_linked: true,
        local_sync_state: 'queued'
      })
      linkingMarkerId.value = null
      linkingEntryId.value = ''
    }
  )
}

async function unlinkMarker(markerId) {
  const marker = markers.value.find((candidate) => candidate.id === markerId)
  if (!marker) {
    errorMessage.value = t('operator.capture.errors.marker_not_found')
    return
  }

  if (marker.is_approved) {
    errorMessage.value = t('operator.capture.errors.marker_approved')
    return
  }

  errorMessage.value = ''

  await queueMutation(
    {
      type: 'UNLINK_MARKER',
      endpoint: `/api/v1/regattas/${props.regattaId}/operator/markers/${markerId}/unlink`,
      method: 'POST',
      data: {},
      conflictStrategy: 'last-write-wins',
      supportsForceOverride: false,
      metadata: {
        markerId
      }
    },
    () => {
      upsertMarker({
        ...marker,
        entry_id: null,
        is_linked: false,
        local_sync_state: 'queued'
      })
    }
  )
}

async function approveMarker(markerId) {
  const marker = markers.value.find((candidate) => candidate.id === markerId)
  if (!marker) {
    errorMessage.value = t('operator.capture.errors.marker_not_found')
    return
  }

  if (marker.is_approved) {
    return
  }

  errorMessage.value = ''

  await queueMutation(
    {
      type: 'APPROVE_MARKER',
      endpoint: `/api/v1/regattas/${props.regattaId}/operator/markers/${markerId}`,
      method: 'PATCH',
      data: {
        is_approved: true
      },
      conflictStrategy: 'manual',
      metadata: {
        markerId
      }
    },
    () => {
      upsertMarker({
        ...marker,
        is_approved: true,
        local_sync_state: 'queued'
      })
    }
  )
}

async function undoLastChange() {
  if (undoStack.value.length === 0) {
    return
  }

  const lastChange = undoStack.value.pop()
  const marker = markers.value.find((candidate) => candidate.id === lastChange.markerId)
  if (!marker || marker.is_approved) {
    return
  }

  editingMarkerId.value = lastChange.markerId
  editingFrameOffset.value = String(lastChange.oldFrameOffset)
  await updateMarker(lastChange.markerId)
}

function prepareQueuedOperation(item, resolvedTempIds) {
  const preparedItem = {
    ...item,
    headers: {
      ...(item.headers ?? {})
    }
  }

  const markerId = item.metadata?.markerId
  if (typeof markerId === 'string' && resolvedTempIds.has(markerId)) {
    preparedItem.endpoint = preparedItem.endpoint.replace(markerId, resolvedTempIds.get(markerId))
  }

  return preparedItem
}

function handleSyncedOperation(item, syncedData, resolvedTempIds) {
  if (item.type === 'CREATE_MARKER') {
    const tempMarkerId = item.metadata?.tempMarkerId
    if (typeof tempMarkerId === 'string' && syncedData?.id) {
      resolvedTempIds.set(tempMarkerId, syncedData.id)
      replaceMarkerId(tempMarkerId, syncedData)
      return
    }
  }

  if (item.type === 'DELETE_MARKER') {
    removeMarker(item.metadata?.markerId)
    return
  }

  if (syncedData?.id) {
    upsertMarker(syncedData)
  }
}

async function syncQueuedOperations(targetIds = null) {
  if (isQueueSyncing.value) {
    return
  }

  const resolvedTempIds = new Map()
  const queue = await getQueue()
  const candidates = [...queue]
    .filter((item) => !targetIds || targetIds.includes(item.id))
    .sort((a, b) => a.timestamp - b.timestamp)

  if (candidates.length === 0) {
    await refreshQueueItems()
    return
  }

  isQueueSyncing.value = true
  errorMessage.value = ''

  try {
    for (const item of candidates) {
      await updateQueueItem(item.id, { status: 'syncing', lastError: null })
      markMarkerSyncState(item.metadata?.markerId ?? item.metadata?.tempMarkerId, 'syncing')
      await refreshQueueItems()

      const preparedItem = prepareQueuedOperation(item, resolvedTempIds)
      const result = await syncQueue([preparedItem], { enableRetry: true })

      if (result.synced.length > 0) {
        handleSyncedOperation(item, result.synced[0].data, resolvedTempIds)
        await dequeue(item.id)
        continue
      }

      if (result.discarded.length > 0) {
        await dequeue(item.id)
        await loadMarkers()
        continue
      }

      if (result.conflicts.length > 0) {
        const conflict = result.conflicts[0]
        await updateQueueItem(item.id, {
          status: 'conflict',
          lastError: conflict.conflictMessage || conflict.conflictCode || t('operator.capture.conflict_detected'),
          conflictCode: conflict.conflictCode ?? null,
          conflictMessage: conflict.conflictMessage ?? null,
          limitation: conflict.limitation ?? null,
          policy: conflict.policy ?? item.conflictStrategy ?? 'manual'
        })
        markMarkerSyncState(item.metadata?.markerId ?? item.metadata?.tempMarkerId, 'conflict')
        continue
      }

      if (result.failed.length > 0) {
        const failure = result.failed[0]
        await updateQueueItem(item.id, {
          status: 'failed',
          attempts: (item.attempts ?? 0) + 1,
          lastError: failure.error
        })
        markMarkerSyncState(item.metadata?.markerId ?? item.metadata?.tempMarkerId, 'failed')
        if (!failure.status || failure.status >= 500) {
          break
        }
      }
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('operator.capture.errors.failed_sync_queue')
  } finally {
    isQueueSyncing.value = false
    await refreshQueueItems()
  }
}

async function retryQueueItem(queueId) {
  await updateQueueItem(queueId, { status: 'queued', lastError: null, limitation: null })
  await refreshQueueItems()
  if (isOnline.value) {
    await loadMarkers()
    await syncQueuedOperations([queueId])
  }
}

async function discardQueueItem(queueId) {
  await dequeue(queueId)
  await refreshQueueItems()
  await loadMarkers()
}

function handleOnline() {
  isOnline.value = true
  syncQueuedOperations()
}

function handleOffline() {
  isOnline.value = false
}

watch(
  () => currentEvidence.value?.frameOffset,
  (frameOffset) => {
    if (Number.isFinite(frameOffset) && !selectedMarkerId.value) {
      detailCenterFrame.value = frameOffset
    }
  },
  { immediate: true }
)

watch(
  () => queueSize.value,
  () => {
    refreshQueueItems()
  }
)

onMounted(async () => {
  readAttachedEvidenceFrame()
  await Promise.all([loadCaptureSession(), loadMarkers(), refreshQueueItems()])

  if (typeof window !== 'undefined') {
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
  }
})

onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('online', handleOnline)
    window.removeEventListener('offline', handleOffline)
  }
})

defineExpose({
  deleteMarker,
  loadCaptureSession,
  retryQueueItem,
  discardQueueItem,
  syncQueuedOperations,
  queueItems,
  pendingConflicts
})
</script>

<template>
  <div class="line-scan-capture">
    <div class="capture-header">
      <div>
        <h2>{{ t('operator.capture.title') }}</h2>
        <p class="capture-subtitle">{{ t('operator.capture.evidence_workspace') }}</p>
      </div>

      <div class="capture-controls">
        <button
          type="button"
          data-testid="create-marker-button"
          @click="createMarker"
          :disabled="!canCreateMarker"
        >
          {{ t('operator.capture.capture_selected_frame') }}
        </button>

        <button
          v-if="undoStack.length > 0"
          type="button"
          data-testid="undo-last-change"
          @click="undoLastChange"
        >
          {{ t('operator.capture.undo') }}
        </button>

        <button
          type="button"
          data-testid="toggle-high-contrast"
          @click="toggleContrast"
          class="contrast-toggle"
        >
          {{ isHighContrast ? t('operator.capture.high_contrast_on') : t('operator.capture.high_contrast_off') }}
        </button>
      </div>
    </div>

    <div v-if="errorMessage" data-testid="error-message" class="error-message">
      {{ errorMessage }}
    </div>

    <section class="workspace-grid">
      <div class="workspace-panel evidence-panel">
        <div class="panel-header">
          <h3>{{ t('operator.capture.current_evidence') }}</h3>
          <button
            type="button"
            data-testid="refresh-live-source"
            class="secondary-button"
            @click="readAttachedEvidenceFrame"
          >
            {{ t('operator.capture.refresh_live_source') }}
          </button>
        </div>

        <p data-testid="evidence-source-mode" class="source-mode">
          {{ hasAttachedEvidence ? t('operator.capture.evidence_source_live') : t('operator.capture.evidence_source_dev') }}
        </p>

        <p v-if="!hasAttachedEvidence" data-testid="dev-evidence-banner" class="dev-evidence-banner">
          {{ t('operator.capture.dev_source_hint') }}
        </p>

        <div class="evidence-fields">
          <label>
            <span>{{ t('operator.capture.dev_frame_offset') }}</span>
            <input
              type="number"
              data-testid="dev-frame-offset-input"
              v-model="devEvidenceFrameOffset"
              min="0"
            />
          </label>

          <label>
            <span>{{ t('operator.capture.dev_tile_id') }}</span>
            <input
              type="text"
              data-testid="dev-tile-id-input"
              v-model="devEvidenceTileId"
            />
          </label>

          <label>
            <span>{{ t('operator.capture.dev_tile_x') }}</span>
            <input
              type="number"
              data-testid="dev-tile-x-input"
              v-model="devEvidenceTileX"
              min="0"
            />
          </label>

          <label>
            <span>{{ t('operator.capture.dev_tile_y') }}</span>
            <input
              type="number"
              data-testid="dev-tile-y-input"
              v-model="devEvidenceTileY"
              min="0"
            />
          </label>
        </div>

        <div v-if="currentEvidence" data-testid="evidence-preview" class="evidence-preview">
          <span>{{ t('operator.capture.frame_preview') }}: {{ currentEvidence.frameOffset }}</span>
          <span>{{ t('operator.capture.derived_time') }}: {{ formatTimestamp(currentEvidence.timestampMs) }}</span>
          <span>{{ t('operator.capture.capture_ready') }}</span>
        </div>
        <div v-else class="evidence-preview evidence-preview--pending">
          {{ t('operator.capture.capture_pending_source') }}
        </div>
      </div>

      <div class="workspace-panel overview-panel">
        <div class="panel-header">
          <h3>{{ t('operator.capture.overview_strip') }}</h3>
          <button
            type="button"
            data-testid="focus-unlinked-marker"
            class="secondary-button"
            @click="focusFirstUnlinkedMarker"
            :disabled="!sortedMarkers.some((marker) => !marker.is_linked)"
          >
            {{ t('operator.capture.focus_unlinked') }}
          </button>
        </div>

        <div data-testid="overview-strip" class="overview-strip">
          <div class="overview-window" :style="detailWindowStyle()"></div>
          <button
            v-for="marker in sortedMarkers"
            :key="`overview-${marker.id}`"
            type="button"
            class="overview-marker"
            :class="{
              'overview-marker--linked': marker.is_linked,
              'overview-marker--approved': marker.is_approved,
              'overview-marker--selected': marker.id === selectedMarkerId
            }"
            :style="markerPositionStyle(marker)"
            :data-testid="`overview-marker-${marker.id}`"
            @click="selectMarker(marker)"
          >
            <span class="sr-only">{{ t('operator.capture.review_marker') }}</span>
          </button>
        </div>

        <label class="overview-slider">
          <span>{{ t('operator.capture.detail_window') }}: {{ detailCenterFrame }}</span>
          <input
            type="range"
            data-testid="overview-center-slider"
            :min="frameBounds.min"
            :max="frameBounds.max"
            :value="detailCenterFrame"
            @input="detailCenterFrame = Number($event.target.value)"
          />
        </label>

        <div class="zoom-controls">
          <button type="button" data-testid="zoom-wide" class="secondary-button" @click="detailZoomStep = 'wide'">
            {{ t('operator.capture.zoom_wide') }}
          </button>
          <button type="button" data-testid="zoom-medium" class="secondary-button" @click="detailZoomStep = 'medium'">
            {{ t('operator.capture.zoom_medium') }}
          </button>
          <button type="button" data-testid="zoom-fine" class="secondary-button" @click="detailZoomStep = 'fine'">
            {{ t('operator.capture.zoom_fine') }}
          </button>
        </div>
      </div>

      <div class="workspace-panel session-status-pane">
        <div class="panel-header">
          <h3>{{ t('operator.capture.session_status') }}</h3>
          <button
            type="button"
            data-testid="load-session-status"
            class="secondary-button"
            @click="loadCaptureSession"
            :disabled="isSessionLoading"
          >
            {{ isSessionLoading ? t('operator.capture.session_status_loading') : t('operator.capture.session_status_refresh') }}
          </button>
        </div>

        <div v-if="captureSession" data-testid="session-status-detail" class="session-status-detail">
          <span data-testid="session-sync-indicator" :class="captureSessionStatusClass">
            {{ captureSessionStatusText }}
          </span>
          <span>{{ t('operator.capture.session_start') }}: {{ captureSession.server_time_at_start || '—' }}</span>
          <span>{{ t('operator.capture.frames_per_second') }}: {{ captureSession.fps ?? '—' }}</span>
          <span
            v-if="captureSession.is_synced === false && captureSession.unsynced_reason"
            data-testid="session-sync-reason"
            class="session-sync-reason"
          >
            {{ captureSession.unsynced_reason }}
          </span>
          <p class="approval-scope-note">{{ t('operator.capture.approval_scope_note') }}</p>
        </div>

        <div
          v-if="captureSessionErrorMessage"
          data-testid="session-status-error"
          class="session-status-error"
        >
          {{ captureSessionErrorMessage }}
        </div>
      </div>
    </section>

    <section class="workspace-grid workspace-grid--bottom">
      <div class="workspace-panel detail-panel">
        <div class="panel-header">
          <h3>{{ t('operator.capture.detail_window') }}</h3>
          <span v-if="selectedMarker">{{ t('operator.capture.selected_marker') }}: {{ selectedMarker.id }}</span>
        </div>

        <div data-testid="detail-window" class="detail-window">
          <div v-if="visibleMarkers.length === 0" class="empty-state">
            {{ t('operator.capture.no_visible_markers') }}
          </div>

          <button
            v-for="marker in visibleMarkers"
            :key="`detail-${marker.id}`"
            type="button"
            class="detail-marker"
            :class="{
              'detail-marker--selected': marker.id === selectedMarkerId,
              'detail-marker--linked': marker.is_linked,
              'detail-marker--approved': marker.is_approved
            }"
            :data-testid="`detail-marker-${marker.id}`"
            @click="selectMarker(marker)"
          >
            <strong>{{ marker.frame_offset }}</strong>
            <span>{{ formatTimestamp(marker.timestamp_ms) }}</span>
            <span v-if="marker.entry_id">{{ t('operator.capture.bib_number') }} {{ marker.entry_id }}</span>
          </button>
        </div>
      </div>

      <div class="workspace-panel queue-panel">
        <div class="panel-header">
          <h3>{{ t('operator.capture.queue_title') }}</h3>
          <button
            type="button"
            data-testid="sync-queue-button"
            class="secondary-button"
            @click="syncQueuedOperations()"
            :disabled="isQueueSyncing || queueItems.length === 0"
          >
            {{ t('operator.capture.queue_sync_now') }}
          </button>
        </div>

        <div v-if="queueItems.length === 0" data-testid="queue-empty-state" class="empty-state">
          {{ t('operator.capture.queue_empty') }}
        </div>

        <div v-else class="queue-items">
          <div
            v-for="item in queueItems"
            :key="item.id"
            class="queue-item"
            :data-testid="`queue-item-${item.id}`"
          >
            <div class="queue-item__summary">
              <strong>{{ describeQueueItem(item) }}</strong>
              <span>{{ queueStatusLabel(item.status) }}</span>
            </div>
            <p v-if="item.lastError" class="queue-item__message">{{ item.lastError }}</p>
            <p v-if="item.status === 'conflict'" class="queue-item__message">{{ conflictHelperText(item) }}</p>
            <div class="queue-item__actions">
              <button
                type="button"
                class="secondary-button"
                :data-testid="`queue-retry-${item.id}`"
                @click="retryQueueItem(item.id)"
              >
                {{ t('operator.capture.queue_retry') }}
              </button>
              <button
                type="button"
                class="secondary-button"
                :data-testid="`queue-discard-${item.id}`"
                @click="discardQueueItem(item.id)"
              >
                {{ t('operator.capture.queue_discard') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <div data-testid="capture-markers-list" class="markers-list">
      <div v-if="!hasMarkers" class="empty-state">
        {{ t('operator.capture.no_markers') }}
      </div>

      <div
        v-for="marker in sortedMarkers"
        :key="marker.id"
        :data-testid="`marker-item-${marker.id}`"
        class="marker-item"
        :class="{
          'marker-approved': marker.is_approved,
          'marker-selected': marker.id === selectedMarkerId
        }"
      >
        <div class="marker-info">
          <button type="button" class="marker-review" :data-testid="`review-marker-${marker.id}`" @click="selectMarker(marker)">
            {{ t('operator.capture.review_marker') }}
          </button>
          <span>{{ t('operator.capture.frame_offset') }}: {{ marker.frame_offset }}</span>
          <span>{{ t('operator.capture.derived_time') }}: {{ formatTimestamp(marker.timestamp_ms) }}</span>
          <span v-if="marker.is_linked && marker.entry_id">
            {{ t('operator.capture.bib_number') }}: {{ marker.entry_id }}
          </span>
          <span v-if="marker.is_approved" :data-testid="`marker-locked-${marker.id}`" class="marker-locked">
            {{ t('operator.capture.locked') }}
          </span>
          <span v-if="marker.local_sync_state" class="marker-sync-state">
            {{ queueStatusLabel(marker.local_sync_state) }}
          </span>
        </div>

        <div class="marker-actions">
          <div v-if="editingMarkerId === marker.id" class="edit-controls">
            <input
              type="number"
              data-testid="marker-frame-input"
              v-model="editingFrameOffset"
              min="0"
            />
            <button
              type="button"
              :data-testid="`update-marker-${marker.id}`"
              @click="updateMarker(marker.id)"
            >
              Save
            </button>
            <button type="button" @click="editingMarkerId = null">Cancel</button>
          </div>

          <button
            v-else-if="!marker.is_approved"
            type="button"
            :data-testid="`edit-marker-${marker.id}`"
            @click="startEditing(marker.id)"
          >
            Edit
          </button>

          <div v-if="linkingMarkerId === marker.id" class="link-controls">
            <input
              type="text"
              data-testid="link-entry-input"
              v-model="linkingEntryId"
              placeholder="Entry ID"
            />
            <button
              type="button"
              data-testid="link-entry-submit"
              @click="linkMarker(marker.id)"
            >
              {{ t('operator.capture.link_to_bib') }}
            </button>
            <button type="button" @click="linkingMarkerId = null">Cancel</button>
          </div>

          <button
            v-else-if="!marker.is_linked && !marker.is_approved"
            type="button"
            :data-testid="`link-marker-${marker.id}`"
            @click="startLinking(marker.id)"
          >
            {{ t('operator.capture.link_to_bib') }}
          </button>

          <button
            v-if="marker.is_linked && !marker.is_approved"
            type="button"
            :data-testid="`unlink-marker-${marker.id}`"
            @click="unlinkMarker(marker.id)"
          >
            {{ t('operator.capture.unlink') }}
          </button>

          <button
            v-if="marker.is_linked && !marker.is_approved"
            type="button"
            :data-testid="`approve-marker-${marker.id}`"
            @click="approveMarker(marker.id)"
          >
            {{ t('operator.capture.approve_marker') }}
          </button>

          <button
            type="button"
            :data-testid="`delete-marker-${marker.id}`"
            @click="deleteMarker(marker.id)"
            :disabled="marker.is_approved"
          >
            {{ t('operator.capture.delete_marker') }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="pendingConflicts.length > 0" data-testid="conflict-resolution-pane" class="conflict-pane">
      <h3>{{ t('operator.capture.conflicts_pending_title') }}</h3>
      <div
        v-for="item in pendingConflicts"
        :key="item.id"
        class="conflict-item"
        :data-testid="`conflict-item-${item.id}`"
      >
        <span class="conflict-label">{{ describeQueueItem(item) }}</span>
        <p class="queue-item__message">{{ conflictHelperText(item) }}</p>
        <div class="conflict-actions">
          <button type="button" :data-testid="`conflict-retry-${item.id}`" @click="retryQueueItem(item.id)">
            {{ t('operator.capture.queue_retry') }}
          </button>
          <button type="button" :data-testid="`conflict-discard-${item.id}`" @click="discardQueueItem(item.id)">
            {{ t('operator.capture.queue_discard') }}
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

.capture-header {
  display: flex;
  justify-content: space-between;
  gap: var(--rd-space-3, 1rem);
  align-items: flex-start;
  margin-bottom: var(--rd-space-3, 1rem);
}

.capture-subtitle {
  margin-top: 0.25rem;
  max-width: 52rem;
}

.capture-controls,
.panel-header,
.queue-item__actions,
.marker-actions,
.edit-controls,
.link-controls,
.zoom-controls {
  display: flex;
  flex-wrap: wrap;
  gap: var(--rd-space-2, 0.5rem);
  align-items: center;
}

.workspace-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(18rem, 1fr));
  gap: var(--rd-space-3, 1rem);
  margin-bottom: var(--rd-space-3, 1rem);
}

.workspace-grid--bottom {
  align-items: start;
}

.workspace-panel {
  border: 1px solid var(--rd-color-border, #ccc);
  border-radius: 0.5rem;
  padding: var(--rd-space-3, 1rem);
  background: var(--rd-color-surface, #fff);
}

.panel-header {
  justify-content: space-between;
  margin-bottom: var(--rd-space-2, 0.5rem);
}

.overview-strip {
  position: relative;
  min-height: 4rem;
  margin-bottom: var(--rd-space-3, 1rem);
  border-radius: 999px;
  background:
    linear-gradient(90deg, rgba(9, 53, 92, 0.1), rgba(9, 53, 92, 0.2)),
    repeating-linear-gradient(90deg, transparent, transparent 24px, rgba(9, 53, 92, 0.08) 24px, rgba(9, 53, 92, 0.08) 25px);
}

.overview-window {
  position: absolute;
  top: 0.5rem;
  bottom: 0.5rem;
  border: 2px solid #09355c;
  border-radius: 999px;
  background: rgba(9, 53, 92, 0.12);
}

.overview-marker {
  position: absolute;
  top: 50%;
  width: 0.9rem;
  height: 2.5rem;
  transform: translate(-50%, -50%);
  border: 0;
  border-radius: 999px;
  background: #bc2f32;
}

.overview-marker--linked {
  background: #0d6b3a;
}

.overview-marker--approved {
  box-shadow: 0 0 0 2px #111;
}

.overview-marker--selected {
  height: 3rem;
}

.overview-slider {
  display: grid;
  gap: 0.5rem;
}

.detail-window {
  display: grid;
  gap: var(--rd-space-2, 0.5rem);
}

.detail-marker {
  display: grid;
  gap: 0.25rem;
  justify-items: start;
  padding: var(--rd-space-2, 0.5rem);
  border: 1px solid var(--rd-color-border, #ccc);
  border-radius: 0.5rem;
  background: #fff;
}

.detail-marker--selected,
.marker-selected {
  border-color: #09355c;
  box-shadow: 0 0 0 2px rgba(9, 53, 92, 0.15);
}

.detail-marker--linked {
  background: #eef8f1;
}

.detail-marker--approved {
  background: #f4f4f4;
}

.evidence-fields {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
  gap: var(--rd-space-2, 0.5rem);
  margin-bottom: var(--rd-space-2, 0.5rem);
}

.evidence-fields label,
.overview-slider,
.queue-item,
.marker-item {
  display: grid;
  gap: 0.5rem;
}

.evidence-preview {
  display: grid;
  gap: 0.25rem;
  padding: var(--rd-space-2, 0.5rem);
  border-radius: 0.5rem;
  background: #eef5fb;
}

.evidence-preview--pending,
.dev-evidence-banner,
.approval-scope-note {
  padding: var(--rd-space-2, 0.5rem);
  border-radius: 0.5rem;
  background: #fff7d1;
}

.queue-items,
.markers-list {
  display: grid;
  gap: var(--rd-space-2, 0.5rem);
}

.queue-item,
.marker-item {
  border: 1px solid var(--rd-color-border, #ccc);
  border-radius: 0.5rem;
  padding: var(--rd-space-2, 0.5rem);
}

.queue-item__summary,
.marker-info {
  display: flex;
  flex-wrap: wrap;
  gap: var(--rd-space-2, 0.5rem);
  align-items: center;
}

.queue-item__message,
.source-mode {
  margin: 0;
}

.marker-approved {
  background: var(--rd-color-surface-subtle, #f5f5f5);
}

.marker-review,
.secondary-button,
button {
  padding: 0.45rem 0.8rem;
  border: 1px solid var(--rd-color-border, #ccc);
  border-radius: 0.45rem;
  background: var(--rd-color-surface, #fff);
  cursor: pointer;
}

.marker-locked,
.marker-sync-state {
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: 0.875rem;
  font-weight: 600;
}

.marker-locked {
  background: var(--rd-color-warning-soft, #fff7d1);
  border: 1px solid var(--rd-color-warning, #e1b100);
}

.marker-sync-state {
  background: #eef5fb;
}

.conflict-pane,
.error-message,
.session-status-error {
  padding: var(--rd-space-2, 0.5rem);
  margin-top: var(--rd-space-3, 1rem);
  border: 1px solid #b30000;
  background: #ffebee;
  color: #7f0000;
}

.conflict-item {
  display: grid;
  gap: 0.5rem;
  margin-top: var(--rd-space-2, 0.5rem);
}

.empty-state {
  color: var(--rd-color-text-secondary, #666);
}

.sync-ok {
  color: #0d6b3a;
}

.sync-pending {
  color: #7a5b00;
}

.sync-attention {
  color: #b30000;
}

.session-status-detail {
  display: grid;
  gap: 0.5rem;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 720px) {
  .capture-header {
    flex-direction: column;
  }
}
</style>