export const SELECTED_CAPTURE_SESSIONS_STORAGE_KEY = 'rd_operator_selected_capture_sessions'

function normalizeStorageKeyPart(value) {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : null
}

function getStorage() {
  const storage = globalThis.window?.localStorage ?? globalThis.localStorage
  if (
    storage &&
    typeof storage.getItem === 'function' &&
    typeof storage.setItem === 'function'
  ) {
    return storage
  }

  return null
}

function readSelectionMap() {
  const rawValue = getStorage()?.getItem(SELECTED_CAPTURE_SESSIONS_STORAGE_KEY)
  if (!rawValue) {
    return {}
  }

  try {
    const parsed = JSON.parse(rawValue)
    return typeof parsed === 'object' && parsed !== null ? parsed : {}
  } catch {
    return {}
  }
}

function writeSelectionMap(selectionMap) {
  getStorage()?.setItem(SELECTED_CAPTURE_SESSIONS_STORAGE_KEY, JSON.stringify(selectionMap))
}

export function getSelectedCaptureSessionId(regattaId) {
  const normalizedRegattaId = normalizeStorageKeyPart(regattaId)
  if (!normalizedRegattaId) {
    return null
  }

  return normalizeStorageKeyPart(readSelectionMap()[normalizedRegattaId])
}

export function setSelectedCaptureSessionId(regattaId, captureSessionId) {
  const normalizedRegattaId = normalizeStorageKeyPart(regattaId)
  const normalizedCaptureSessionId = normalizeStorageKeyPart(captureSessionId)
  if (!normalizedRegattaId || !normalizedCaptureSessionId) {
    return
  }

  const selectionMap = readSelectionMap()
  selectionMap[normalizedRegattaId] = normalizedCaptureSessionId
  writeSelectionMap(selectionMap)
}

export function clearSelectedCaptureSessionId(regattaId, captureSessionId) {
  const normalizedRegattaId = normalizeStorageKeyPart(regattaId)
  if (!normalizedRegattaId) {
    return
  }

  const selectionMap = readSelectionMap()
  const normalizedCaptureSessionId = normalizeStorageKeyPart(captureSessionId)
  if (
    normalizedCaptureSessionId &&
    normalizeStorageKeyPart(selectionMap[normalizedRegattaId]) !== normalizedCaptureSessionId
  ) {
    return
  }

  delete selectionMap[normalizedRegattaId]
  writeSelectionMap(selectionMap)
}

export function buildOperatorSessionsPath(regattaId) {
  return `/operator/regattas/${regattaId}/sessions`
}

export function buildOperatorSessionWorkspacePath(regattaId, captureSessionId) {
  return `${buildOperatorSessionsPath(regattaId)}/${captureSessionId}/line-scan`
}
