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
const evidenceWorkspace = ref(null)
const errorMessage = ref('')
const workspaceErrorMessage = ref('')
const isLoading = ref(false)
const editingMarkerId = ref(null)
const editingFrameOffset = ref('')
const linkingMarkerId = ref(null)
const linkingEntryId = ref('')
const undoStack = ref([])
const captureSession = ref(null)
const selectedMarkerId = ref(null)
const detailCenterFrame = ref(0)
const detailZoomStep = ref('medium')
const cursorTileY = ref(0)
const queueItems = ref([])
const isQueueSyncing = ref(false)
const isOnline = ref(typeof navigator === 'undefined' ? true : navigator.onLine)
const stageElement = ref(null)
const liveDragMarkerId = ref(null)
const dragState = ref(null)

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

function normalizeMarker(marker) {
  return {
    ...marker,
    local_sync_state: marker.local_sync_state ?? null,
    tile_x: Number.isFinite(Number(marker.tile_x)) ? Number(marker.tile_x) : null,
    tile_y: Number.isFinite(Number(marker.tile_y)) ? Number(marker.tile_y) : null
  }
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

function formatDuration(durationMs) {
  if (!Number.isFinite(durationMs) || durationMs < 0) {
    return '—'
  }

  const totalSeconds = Math.floor(durationMs / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60

  return [hours, minutes, seconds].map((value) => String(value).padStart(2, '0')).join(':')
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

function deriveFrameOffsetFromTimestamp(timestampMs) {
  const startTime = captureSession.value?.server_time_at_start
  const fps = captureSession.value?.fps
  if (!startTime || !Number.isFinite(timestampMs) || !Number.isFinite(Number(fps)) || Number(fps) <= 0) {
    return null
  }

  const startMs = Date.parse(startTime)
  if (!Number.isFinite(startMs)) {
    return null
  }

  return Math.max(0, Math.round(((timestampMs - startMs) / 1000) * Number(fps)))
}

function defaultCursorFrameOffset() {
  const startTimestampMs = evidenceWorkspace.value?.evidence?.span?.start_timestamp_ms
  const derived = deriveFrameOffsetFromTimestamp(startTimestampMs)
  return derived ?? 0
}

function defaultCursorTileY() {
  return evidenceSpan.value?.min_tile_y ?? 0
}

function clampFrameOffset(frameOffset) {
  if (!Number.isFinite(frameOffset)) {
    return 0
  }

  return Math.max(0, Math.round(frameOffset))
}

function clampCursorTileY(tileY) {
  const span = evidenceSpan.value
  if (!span) {
    return 0
  }

  return Math.min(span.max_tile_y, Math.max(span.min_tile_y, Math.round(tileY)))
}

function mergeMarkerSyncState(nextMarkers) {
  const syncStateById = new Map(markers.value.map((marker) => [marker.id, marker.local_sync_state ?? null]))
  return nextMarkers.map((marker) => {
    const normalizedMarker = normalizeMarker(marker)
    return {
      ...normalizedMarker,
      local_sync_state: syncStateById.get(normalizedMarker.id) ?? normalizedMarker.local_sync_state ?? null
    }
  })
}

async function loadWorkspace() {
  if (!hasCaptureSession() || isLoading.value) {
    return
  }

  workspaceErrorMessage.value = ''
  isLoading.value = true

  try {
    const response = await operatorApi.getEvidenceWorkspace(props.regattaId, {
      capture_session_id: props.captureSessionId
    })

    evidenceWorkspace.value = response
    captureSession.value = normalizeCaptureSession(response?.capture_session)
    markers.value = mergeMarkerSyncState(Array.isArray(response?.markers) ? response.markers : [])

    if (selectedMarkerId.value && !markers.value.some((marker) => marker.id === selectedMarkerId.value)) {
      selectedMarkerId.value = null
    }

    if (!selectedMarkerId.value && markers.value.length > 0) {
      const firstMarker = [...markers.value].sort((a, b) => a.frame_offset - b.frame_offset)[0]
      selectedMarkerId.value = firstMarker.id
      detailCenterFrame.value = firstMarker.frame_offset
      cursorTileY.value = firstMarker.tile_y ?? defaultCursorTileY()
    } else if (!selectedMarkerId.value) {
      detailCenterFrame.value = defaultCursorFrameOffset()
      cursorTileY.value = defaultCursorTileY()
    }
  } catch (error) {
    workspaceErrorMessage.value =
      error instanceof Error ? error.message : t('operator.capture.errors.failed_load_workspace')
  } finally {
    isLoading.value = false
  }
}

const evidence = computed(() => evidenceWorkspace.value?.evidence ?? null)
const evidenceSpan = computed(() => evidence.value?.span ?? null)
const evidenceTiles = computed(() => {
  const tiles = Array.isArray(evidence.value?.tiles) ? evidence.value.tiles : []
  return [...tiles].sort((a, b) => {
    if ((a.tile_y ?? 0) !== (b.tile_y ?? 0)) {
      return (a.tile_y ?? 0) - (b.tile_y ?? 0)
    }

    return (a.tile_x ?? 0) - (b.tile_x ?? 0)
  })
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

function evidencePercentX(timestampMs) {
  const span = evidenceSpan.value
  const xOriginTimestampMs = evidence.value?.x_origin_timestamp_ms
  const msPerPixel = evidence.value?.ms_per_pixel
  if (!span || !Number.isFinite(timestampMs) || !Number.isFinite(Number(xOriginTimestampMs)) || !Number.isFinite(Number(msPerPixel)) || Number(msPerPixel) <= 0 || span.pixel_width <= 0) {
    return null
  }

  const pixelX = (timestampMs - Number(xOriginTimestampMs)) / Number(msPerPixel)
  return Math.min(100, Math.max(0, (pixelX / span.pixel_width) * 100))
}

function evidencePercentY(tileY) {
  const span = evidenceSpan.value
  const tileSizePx = evidence.value?.tile_size_px
  if (!span || !Number.isFinite(Number(tileY)) || !Number.isFinite(Number(tileSizePx)) || Number(tileSizePx) <= 0 || span.pixel_height <= 0) {
    return 50
  }

  const relativeY = ((Number(tileY) - span.min_tile_y) * Number(tileSizePx) + Number(tileSizePx) / 2) / span.pixel_height
  return Math.min(100, Math.max(0, relativeY * 100))
}

function locateEvidenceTile(timestampMs, preferredTileY = null) {
  const span = evidenceSpan.value
  const tileSizePx = evidence.value?.tile_size_px
  const msPerPixel = evidence.value?.ms_per_pixel
  const xOriginTimestampMs = evidence.value?.x_origin_timestamp_ms
  if (!span || !Number.isFinite(timestampMs) || !Number.isFinite(Number(tileSizePx)) || Number(tileSizePx) <= 0 || !Number.isFinite(Number(msPerPixel)) || Number(msPerPixel) <= 0 || !Number.isFinite(Number(xOriginTimestampMs))) {
    return {
      tileId: null,
      tileX: null,
      tileY: null,
      uploadState: null
    }
  }

  const absoluteTileX = Math.min(
    span.max_tile_x,
    Math.max(
      span.min_tile_x,
      span.min_tile_x + Math.floor(((timestampMs - Number(xOriginTimestampMs)) / Number(msPerPixel)) / Number(tileSizePx))
    )
  )
  const absoluteTileY = Math.min(
    span.max_tile_y,
    Math.max(span.min_tile_y, Number.isFinite(Number(preferredTileY)) ? Number(preferredTileY) : span.min_tile_y)
  )

  const tile = evidenceTiles.value.find((candidate) => candidate.tile_x === absoluteTileX && candidate.tile_y === absoluteTileY)
  return {
    tileId: tile?.tile_id ?? null,
    tileX: absoluteTileX,
    tileY: absoluteTileY,
    uploadState: tile?.upload_state ?? null
  }
}

const currentEvidence = computed(() => {
  const frameOffset = Math.max(0, Math.round(detailCenterFrame.value))
  const timestampMs = deriveTimestampMs(frameOffset)
  if (!Number.isFinite(timestampMs)) {
    return null
  }

  const preferredTileY = liveDragMarkerId.value
    ? markers.value.find((marker) => marker.id === liveDragMarkerId.value)?.tile_y
    : cursorTileY.value
  const tilePlacement = locateEvidenceTile(timestampMs, preferredTileY)

  return {
    source: 'persisted',
    frameOffset,
    timestampMs,
    tileId: tilePlacement.tileId,
    tileX: tilePlacement.tileX,
    tileY: tilePlacement.tileY,
    uploadState: tilePlacement.uploadState
  }
})

const frameBounds = computed(() => {
  const frameOffsets = markers.value.map((marker) => marker.frame_offset)
  if (currentEvidence.value) {
    frameOffsets.push(currentEvidence.value.frameOffset)
  }

  if (frameOffsets.length === 0) {
    const fallbackFrame = defaultCursorFrameOffset()
    return { min: Math.max(0, fallbackFrame - detailWindowSize.value / 2), max: fallbackFrame + detailWindowSize.value / 2 }
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
const hasRenderableEvidence = computed(() => {
  return Boolean(evidenceSpan.value) && evidenceTiles.value.length > 0 && evidence.value?.availability_state !== 'unavailable'
})
const currentCursorPercent = computed(() => evidencePercentX(currentEvidence.value?.timestampMs ?? null))
const currentCursorStyle = computed(() => {
  if (currentCursorPercent.value === null) {
    return {}
  }

  return {
    left: `${currentCursorPercent.value}%`
  }
})
const canCreateMarker = computed(() => {
  return Boolean(currentEvidence.value) && evidence.value?.availability_state !== 'unavailable' && !isLoading.value
})
const evidenceAvailabilityClass = computed(() => {
  switch (evidence.value?.availability_state) {
    case 'ready':
      return 'stage-state--ready'
    case 'degraded':
      return 'stage-state--degraded'
    default:
      return 'stage-state--unavailable'
  }
})
const evidenceAvailabilityLabel = computed(() => {
  switch (evidence.value?.availability_state) {
    case 'ready':
      return t('operator.capture.evidence_ready')
    case 'degraded':
      return t('operator.capture.evidence_degraded')
    default:
      return t('operator.capture.evidence_unavailable')
  }
})
const evidenceAvailabilityMessage = computed(() => {
  switch (evidence.value?.availability_reason) {
    case 'manifest_has_no_tiles':
      return t('operator.capture.evidence_reason_manifest_has_no_tiles')
    case 'tile_upload_pending':
      return t('operator.capture.evidence_reason_tile_upload_pending')
    case 'tile_upload_failed':
      return t('operator.capture.evidence_reason_tile_upload_failed')
    case 'manifest_missing':
    default:
      return t('operator.capture.evidence_reason_manifest_missing')
  }
})
const previewStateMessage = computed(() => {
  const previewState = captureSession.value?.live_status?.preview_state
  if (previewState === 'closed') {
    return t('operator.capture.preview_state_closed')
  }

  return t('operator.capture.preview_state_unsupported')
})
const stageGridStyle = computed(() => {
  const span = evidenceSpan.value
  if (!span) {
    return {}
  }

  return {
    gridTemplateColumns: `repeat(${Math.max(1, span.tile_columns || 1)}, minmax(0, 1fr))`,
    gridTemplateRows: `repeat(${Math.max(1, span.tile_rows || 1)}, minmax(0, 1fr))`,
    aspectRatio: `${Math.max(1, span.pixel_width || 1)} / ${Math.max(1, span.pixel_height || 1)}`
  }
})

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
  const normalizedMarker = normalizeMarker(marker)
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
    markers.value[index] = normalizeMarker(nextMarker)
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

function selectMarker(marker) {
  selectedMarkerId.value = marker.id
  detailCenterFrame.value = marker.frame_offset
  cursorTileY.value = marker.tile_y ?? defaultCursorTileY()
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

function tileStateLabel(tile) {
  switch (tile.upload_state) {
    case 'ready':
      return t('operator.capture.tile_state_ready')
    case 'failed':
      return t('operator.capture.tile_state_failed')
    default:
      return t('operator.capture.tile_state_pending')
  }
}

function tilePlacementStyle(tile) {
  const span = evidenceSpan.value
  if (!span) {
    return {}
  }

  return {
    gridColumnStart: tile.tile_x - span.min_tile_x + 1,
    gridRowStart: tile.tile_y - span.min_tile_y + 1
  }
}

function overlayMarkerStyle(marker) {
  const left = evidencePercentX(marker.timestamp_ms)
  if (left === null) {
    return {}
  }

  return {
    left: `${left}%`,
    top: `${evidencePercentY(marker.tile_y)}%`
  }
}

function buildEvidencePlacement(frameOffset, tileY = cursorTileY.value) {
  const normalizedFrameOffset = clampFrameOffset(frameOffset)
  const timestampMs = deriveTimestampMs(normalizedFrameOffset)
  if (!Number.isFinite(timestampMs)) {
    return null
  }

  const normalizedTileY = clampCursorTileY(tileY)
  const tilePlacement = locateEvidenceTile(timestampMs, normalizedTileY)

  return {
    frameOffset: normalizedFrameOffset,
    timestampMs,
    tileId: tilePlacement.tileId,
    tileX: tilePlacement.tileX,
    tileY: tilePlacement.tileY ?? normalizedTileY,
    uploadState: tilePlacement.uploadState
  }
}

function buildEvidencePlacementFromPoint(clientX, clientY) {
  const stage = stageElement.value
  const span = evidenceSpan.value
  const xOriginTimestampMs = evidence.value?.x_origin_timestamp_ms
  const msPerPixel = evidence.value?.ms_per_pixel
  const tileSizePx = evidence.value?.tile_size_px
  if (!stage || !span || !Number.isFinite(Number(xOriginTimestampMs)) || !Number.isFinite(Number(msPerPixel)) || Number(msPerPixel) <= 0 || !Number.isFinite(Number(tileSizePx)) || Number(tileSizePx) <= 0) {
    return null
  }

  const rect = stage.getBoundingClientRect()
  if (!rect.width || !rect.height) {
    return null
  }

  const relativeX = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width))
  const relativeY = Math.min(1, Math.max(0, (clientY - rect.top) / rect.height))
  const pixelX = relativeX * span.pixel_width
  const pixelY = relativeY * span.pixel_height
  const timestampMs = Number(xOriginTimestampMs) + pixelX * Number(msPerPixel)
  const frameOffset = deriveFrameOffsetFromTimestamp(timestampMs)
  const tileY = span.min_tile_y + Math.floor(pixelY / Number(tileSizePx))
  if (frameOffset === null) {
    return null
  }

  return buildEvidencePlacement(frameOffset, tileY)
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

  const tilePlacement = locateEvidenceTile(timestampMs, selectedMarker.value?.tile_y)

  return {
    frame_offset: frameOffset,
    timestamp_ms: timestampMs,
    tile_id: tilePlacement.tileId,
    tile_x: tilePlacement.tileX,
    tile_y: tilePlacement.tileY
  }
}

async function createMarker() {
  if (!hasCaptureSession()) {
    errorMessage.value = t('operator.capture.errors.capture_session_required')
    return
  }

  if (!captureSession.value) {
    await loadWorkspace()
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
  const evidenceCursor = currentEvidence.value
  errorMessage.value = ''

  await queueMutation(
    {
      type: 'CREATE_MARKER',
      endpoint: `/api/v1/regattas/${props.regattaId}/operator/markers`,
      method: 'POST',
      data: {
        capture_session_id: props.captureSessionId,
        frame_offset: evidenceCursor.frameOffset,
        timestamp_ms: evidenceCursor.timestampMs,
        tile_id: evidenceCursor.tileId,
        tile_x: evidenceCursor.tileX,
        tile_y: evidenceCursor.tileY
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
        frame_offset: evidenceCursor.frameOffset,
        timestamp_ms: evidenceCursor.timestampMs,
        is_linked: false,
        is_approved: false,
        tile_id: evidenceCursor.tileId,
        tile_x: evidenceCursor.tileX,
        tile_y: evidenceCursor.tileY,
        local_sync_state: 'queued'
      })
      selectMarker({ id: tempMarkerId, frame_offset: evidenceCursor.frameOffset })
    }
  )
}

async function createMarkerFromPlacement(placement) {
  if (!placement) {
    errorMessage.value = t('operator.capture.errors.live_source_unavailable')
    return
  }

  detailCenterFrame.value = placement.frameOffset
  cursorTileY.value = placement.tileY ?? defaultCursorTileY()
  await createMarker()
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
      cursorTileY.value = payload.tile_y ?? cursorTileY.value
    }
  )
}

async function updateMarkerFromPlacement(markerId, placement) {
  const marker = markers.value.find((candidate) => candidate.id === markerId)
  if (!marker || marker.is_approved || !placement) {
    return
  }

  errorMessage.value = ''

  await queueMutation(
    {
      type: 'UPDATE_MARKER',
      endpoint: `/api/v1/regattas/${props.regattaId}/operator/markers/${markerId}`,
      method: 'PATCH',
      data: {
        frame_offset: placement.frameOffset,
        timestamp_ms: placement.timestampMs,
        tile_id: placement.tileId,
        tile_x: placement.tileX,
        tile_y: placement.tileY
      },
      conflictStrategy: 'last-write-wins',
      supportsForceOverride: false,
      metadata: {
        markerId
      }
    },
    () => {
      upsertMarker({
        ...marker,
        frame_offset: placement.frameOffset,
        timestamp_ms: placement.timestampMs,
        tile_id: placement.tileId,
        tile_x: placement.tileX,
        tile_y: placement.tileY,
        local_sync_state: 'queued'
      })
      undoStack.value.push({ markerId, oldFrameOffset: marker.frame_offset })
      detailCenterFrame.value = placement.frameOffset
      cursorTileY.value = placement.tileY ?? cursorTileY.value
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
        await loadWorkspace()
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
    await loadWorkspace()
    await syncQueuedOperations([queueId])
  }
}

async function discardQueueItem(queueId) {
  await dequeue(queueId)
  await refreshQueueItems()
  await loadWorkspace()
}

function handleOnline() {
  isOnline.value = true
  syncQueuedOperations()
}

function handleOffline() {
  isOnline.value = false
}

function beginMarkerDrag(marker, event) {
  if (marker.is_approved) {
    return
  }

  dragState.value = {
    markerId: marker.id,
    pointerId: event.pointerId,
    moved: false
  }
  liveDragMarkerId.value = marker.id
}

function handleStagePointerDown(event) {
  if (dragState.value) {
    return
  }

  const placement = buildEvidencePlacementFromPoint(event.clientX, event.clientY)
  if (!placement) {
    return
  }

  detailCenterFrame.value = placement.frameOffset
  cursorTileY.value = placement.tileY ?? defaultCursorTileY()
}

async function handleStageClick(event) {
  if (dragState.value) {
    return
  }

  const placement = buildEvidencePlacementFromPoint(event.clientX, event.clientY)
  await createMarkerFromPlacement(placement)
}

async function handleStageKeydown(event) {
  if (!hasRenderableEvidence.value) {
    return
  }

  const step = event.shiftKey ? 5 : 1

  if ((event.key === 'ArrowLeft' || event.key === 'ArrowRight') && (event.altKey || event.metaKey) && selectedMarker.value && !selectedMarker.value.is_approved) {
    event.preventDefault()
    const frameOffset = selectedMarker.value.frame_offset + (event.key === 'ArrowLeft' ? -step : step)
    const placement = buildEvidencePlacement(frameOffset, selectedMarker.value.tile_y ?? cursorTileY.value)
    await updateMarkerFromPlacement(selectedMarker.value.id, placement)
    return
  }

  if ((event.key === 'ArrowUp' || event.key === 'ArrowDown') && (event.altKey || event.metaKey) && selectedMarker.value && !selectedMarker.value.is_approved) {
    event.preventDefault()
    const tileY = (selectedMarker.value.tile_y ?? cursorTileY.value) + (event.key === 'ArrowUp' ? -1 : 1)
    const placement = buildEvidencePlacement(selectedMarker.value.frame_offset, tileY)
    await updateMarkerFromPlacement(selectedMarker.value.id, placement)
    return
  }

  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    detailCenterFrame.value = clampFrameOffset(detailCenterFrame.value - step)
    return
  }

  if (event.key === 'ArrowRight') {
    event.preventDefault()
    detailCenterFrame.value = clampFrameOffset(detailCenterFrame.value + step)
    return
  }

  if (event.key === 'ArrowUp') {
    event.preventDefault()
    cursorTileY.value = clampCursorTileY(cursorTileY.value - 1)
    return
  }

  if (event.key === 'ArrowDown') {
    event.preventDefault()
    cursorTileY.value = clampCursorTileY(cursorTileY.value + 1)
    return
  }

  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    await createMarker()
  }
}

async function handleWindowPointerMove(event) {
  if (!dragState.value) {
    return
  }

  const marker = markers.value.find((candidate) => candidate.id === dragState.value.markerId)
  if (!marker || marker.is_approved) {
    dragState.value = null
    liveDragMarkerId.value = null
    return
  }

  const placement = buildEvidencePlacementFromPoint(event.clientX, event.clientY)
  if (!placement) {
    return
  }

  dragState.value.moved = true
  upsertMarker({
    ...marker,
    frame_offset: placement.frameOffset,
    timestamp_ms: placement.timestampMs,
    tile_id: placement.tileId,
    tile_x: placement.tileX,
    tile_y: placement.tileY
  })
  detailCenterFrame.value = placement.frameOffset
  cursorTileY.value = placement.tileY ?? cursorTileY.value
}

async function handleWindowPointerUp(event) {
  if (!dragState.value) {
    return
  }

  const markerId = dragState.value.markerId
  const marker = markers.value.find((candidate) => candidate.id === markerId)
  const placement = buildEvidencePlacementFromPoint(event.clientX, event.clientY)
  const moved = dragState.value.moved

  dragState.value = null
  liveDragMarkerId.value = null

  if (!marker) {
    return
  }

  if (!moved) {
    selectMarker(marker)
    return
  }

  await updateMarkerFromPlacement(markerId, placement)
}

watch(
  () => queueSize.value,
  () => {
    refreshQueueItems()
  }
)

onMounted(async () => {
  await Promise.all([loadWorkspace(), refreshQueueItems()])

  if (typeof window !== 'undefined') {
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
    window.addEventListener('pointermove', handleWindowPointerMove)
    window.addEventListener('pointerup', handleWindowPointerUp)
  }
})

onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('online', handleOnline)
    window.removeEventListener('offline', handleOffline)
    window.removeEventListener('pointermove', handleWindowPointerMove)
    window.removeEventListener('pointerup', handleWindowPointerUp)
  }
})

defineExpose({
  deleteMarker,
  loadWorkspace,
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

    <div v-if="workspaceErrorMessage" data-testid="workspace-error-message" class="error-message">
      {{ workspaceErrorMessage }}
    </div>

    <section class="workspace-grid">
      <div class="workspace-panel evidence-stage-panel">
        <div class="panel-header">
          <h3>{{ t('operator.capture.current_evidence') }}</h3>
          <button
            type="button"
            data-testid="refresh-evidence-workspace"
            class="secondary-button"
            @click="loadWorkspace"
            :disabled="isLoading"
          >
            {{ t('operator.capture.refresh_evidence_workspace') }}
          </button>
        </div>

        <div class="stage-notices">
          <span data-testid="evidence-availability-badge" class="stage-state" :class="evidenceAvailabilityClass">
            {{ evidenceAvailabilityLabel }}
          </span>
          <span class="stage-note">
            {{ previewStateMessage }}
          </span>
          <span
            v-if="captureSession?.state === 'closed'"
            data-testid="session-gated-state"
            class="stage-note"
          >
            {{ t('operator.capture.session_closed_review_only') }}
          </span>
        </div>

        <p
          v-if="evidence?.availability_state === 'degraded'"
          data-testid="evidence-degraded-banner"
          class="stage-banner stage-banner--warning"
        >
          {{ evidenceAvailabilityMessage }}
        </p>

        <div
          v-if="hasRenderableEvidence"
          data-testid="evidence-stage-shell"
          class="evidence-stage-shell"
        >
          <div
            data-testid="evidence-stage"
            class="evidence-stage"
            ref="stageElement"
            :style="stageGridStyle"
            role="application"
            tabindex="0"
            :aria-label="t('operator.capture.evidence_stage_aria')"
            @pointerdown="handleStagePointerDown"
            @click="handleStageClick"
            @keydown="handleStageKeydown"
          >
            <div
              v-for="tile in evidenceTiles"
              :key="tile.tile_id"
              class="evidence-stage__tile"
              :class="`evidence-stage__tile--${tile.upload_state || 'pending'}`"
              :style="tilePlacementStyle(tile)"
              :data-testid="`evidence-tile-${tile.tile_id}`"
            >
              <img
                v-if="tile.upload_state === 'ready' && tile.tile_href"
                :src="tile.tile_href"
                class="evidence-stage__image"
                :alt="t('operator.capture.evidence_tile_alt', { tileId: tile.tile_id })"
              />
              <div v-else class="evidence-stage__placeholder">
                <strong>{{ tile.tile_id }}</strong>
              </div>
              <span class="evidence-stage__tile-label">
                {{ tileStateLabel(tile) }}
              </span>
            </div>

            <div class="evidence-stage__overlay">
              <div
                v-if="currentCursorPercent !== null"
                data-testid="evidence-cursor"
                class="evidence-cursor"
                :style="currentCursorStyle"
              >
                <span class="sr-only">{{ t('operator.capture.review_cursor') }}</span>
              </div>

              <button
                v-for="marker in sortedMarkers"
                :key="`stage-marker-${marker.id}`"
                type="button"
                class="evidence-overlay-marker"
                :class="{
                  'evidence-overlay-marker--selected': marker.id === selectedMarkerId,
                  'evidence-overlay-marker--linked': marker.is_linked,
                  'evidence-overlay-marker--approved': marker.is_approved
                }"
                :style="overlayMarkerStyle(marker)"
                :data-testid="`evidence-stage-marker-${marker.id}`"
                :aria-label="t('operator.capture.evidence_marker_aria', { markerId: marker.id, frameOffset: marker.frame_offset })"
                @pointerdown.stop.prevent="beginMarkerDrag(marker, $event)"
                @click.stop="selectMarker(marker)"
              >
                <span>{{ marker.frame_offset }}</span>
              </button>
            </div>
          </div>

          <div v-if="currentEvidence" data-testid="evidence-preview" class="evidence-preview">
            <span>{{ t('operator.capture.frame_preview') }}: {{ currentEvidence.frameOffset }}</span>
            <span>{{ t('operator.capture.derived_time') }}: {{ formatTimestamp(currentEvidence.timestampMs) }}</span>
            <span>{{ t('operator.capture.current_tile') }}: {{ currentEvidence.tileId || '—' }}</span>
          </div>
        </div>

        <div
          v-else
          data-testid="evidence-unavailable-state"
          class="evidence-preview evidence-preview--pending"
        >
          <strong>{{ evidenceAvailabilityLabel }}</strong>
          <span>{{ evidenceAvailabilityMessage }}</span>
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
          <div
            v-if="currentEvidence"
            data-testid="overview-cursor"
            class="overview-cursor"
            :style="markerPositionStyle({ frame_offset: currentEvidence.frameOffset })"
          ></div>
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
            @click="loadWorkspace"
            :disabled="isLoading"
          >
            {{ isLoading ? t('operator.capture.session_status_loading') : t('operator.capture.session_status_refresh') }}
          </button>
        </div>

        <div v-if="captureSession" data-testid="session-status-detail" class="session-status-detail">
          <span data-testid="session-sync-indicator" :class="captureSessionStatusClass">
            {{ captureSessionStatusText }}
          </span>
          <span>{{ t('operator.capture.session_start') }}: {{ captureSession.server_time_at_start || '—' }}</span>
          <span>{{ t('operator.capture.frames_per_second') }}: {{ captureSession.fps ?? '—' }}</span>
          <span>{{ t('operator.capture.capture_elapsed') }}: {{ formatDuration(captureSession.live_status?.elapsed_capture_ms) }}</span>
          <span>{{ t('operator.capture.preview_state') }}: {{ captureSession.live_status?.preview_state || '—' }}</span>
          <span
            v-if="captureSession.is_synced === false && captureSession.unsynced_reason"
            data-testid="session-sync-reason"
            class="session-sync-reason"
          >
            {{ captureSession.unsynced_reason }}
          </span>
          <p class="approval-scope-note">{{ t('operator.capture.approval_scope_note') }}</p>
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
.zoom-controls,
.stage-notices {
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

.evidence-stage-panel {
  grid-column: 1 / -1;
}

.panel-header {
  justify-content: space-between;
  margin-bottom: var(--rd-space-2, 0.5rem);
}

.stage-state,
.stage-note,
.marker-locked,
.marker-sync-state {
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: 0.875rem;
  font-weight: 600;
}

.stage-state--ready {
  background: #eef8f1;
  color: #0d6b3a;
}

.stage-state--degraded {
  background: #fff7d1;
  color: #7a5b00;
}

.stage-state--unavailable {
  background: #ffebee;
  color: #7f0000;
}

.stage-note {
  background: #eef5fb;
  color: #09355c;
}

.stage-banner {
  margin: 0.75rem 0 0;
  padding: var(--rd-space-2, 0.5rem);
  border-radius: 0.5rem;
}

.stage-banner--warning,
.evidence-preview--pending,
.approval-scope-note {
  background: #fff7d1;
}

.evidence-stage-shell {
  display: grid;
  gap: var(--rd-space-2, 0.5rem);
  margin-top: var(--rd-space-3, 1rem);
}

.evidence-stage {
  position: relative;
  display: grid;
  overflow: hidden;
  border-radius: 0.75rem;
  border: 1px solid var(--rd-color-border, #ccc);
  background:
    linear-gradient(180deg, rgba(9, 53, 92, 0.06), rgba(9, 53, 92, 0.14)),
    repeating-linear-gradient(90deg, rgba(255, 255, 255, 0.55), rgba(255, 255, 255, 0.55) 8px, rgba(9, 53, 92, 0.05) 8px, rgba(9, 53, 92, 0.05) 16px);
}

.evidence-stage__tile {
  position: relative;
  min-width: 0;
  min-height: 0;
  border: 1px solid rgba(9, 53, 92, 0.1);
  background: rgba(255, 255, 255, 0.65);
}

.evidence-stage__tile--pending {
  background: rgba(255, 247, 209, 0.75);
}

.evidence-stage__tile--failed {
  background: rgba(255, 235, 238, 0.85);
}

.evidence-stage__image,
.evidence-stage__placeholder {
  width: 100%;
  height: 100%;
  display: block;
}

.evidence-stage__image {
  object-fit: cover;
}

.evidence-stage__placeholder {
  display: grid;
  place-items: center;
  color: #09355c;
}

.evidence-stage__tile-label {
  position: absolute;
  left: 0.4rem;
  bottom: 0.4rem;
  padding: 0.2rem 0.45rem;
  border-radius: 999px;
  background: rgba(9, 53, 92, 0.75);
  color: #fff;
  font-size: 0.75rem;
}

.evidence-stage__overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.evidence-overlay-marker,
.evidence-cursor {
  position: absolute;
}

.evidence-overlay-marker {
  transform: translate(-50%, -50%);
  pointer-events: auto;
  min-width: 2.5rem;
  padding: 0.35rem 0.55rem;
  border: 2px solid #09355c;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.95);
  color: #09355c;
  box-shadow: 0 0.2rem 0.5rem rgba(9, 53, 92, 0.18);
}

.evidence-overlay-marker--linked {
  border-color: #0d6b3a;
}

.evidence-overlay-marker--approved {
  background: #111;
  color: #fff;
}

.evidence-overlay-marker--selected {
  transform: translate(-50%, -50%) scale(1.05);
}

.evidence-cursor {
  top: 0;
  bottom: 0;
  width: 2px;
  background: #bc2f32;
  box-shadow: 0 0 0 2px rgba(188, 47, 50, 0.16);
}

.evidence-preview {
  display: grid;
  gap: 0.25rem;
  padding: var(--rd-space-2, 0.5rem);
  border-radius: 0.5rem;
  background: #eef5fb;
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

.overview-marker,
.overview-cursor {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
}

.overview-marker {
  width: 0.9rem;
  height: 2.5rem;
  border: 0;
  border-radius: 999px;
  background: #bc2f32;
}

.overview-cursor {
  width: 0.45rem;
  height: 3rem;
  border-radius: 999px;
  background: #09355c;
  opacity: 0.35;
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

.queue-items,
.markers-list {
  display: grid;
  gap: var(--rd-space-2, 0.5rem);
}

.queue-item,
.marker-item {
  display: grid;
  gap: 0.5rem;
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

.queue-item__message {
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

.marker-locked {
  background: var(--rd-color-warning-soft, #fff7d1);
  border: 1px solid var(--rd-color-warning, #e1b100);
}

.marker-sync-state {
  background: #eef5fb;
}

.conflict-pane,
.error-message {
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

@media (max-width: 768px) {
  .capture-header {
    flex-direction: column;
  }

  .evidence-overlay-marker {
    min-width: 2rem;
    padding: 0.25rem 0.45rem;
    font-size: 0.75rem;
  }
}
</style>
