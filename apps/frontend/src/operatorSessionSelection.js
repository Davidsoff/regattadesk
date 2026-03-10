export const SELECTED_CAPTURE_SESSIONS_STORAGE_KEY = 'rd_operator_selected_capture_sessions'

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
  if (typeof regattaId !== 'string' || regattaId.trim().length === 0) {
    return null
  }

  const sessionId = readSelectionMap()[regattaId]
  return typeof sessionId === 'string' && sessionId.trim().length > 0 ? sessionId : null
}

export function setSelectedCaptureSessionId(regattaId, captureSessionId) {
  if (
    typeof regattaId !== 'string' ||
    regattaId.trim().length === 0 ||
    typeof captureSessionId !== 'string' ||
    captureSessionId.trim().length === 0
  ) {
    return
  }

  const selectionMap = readSelectionMap()
  selectionMap[regattaId] = captureSessionId
  writeSelectionMap(selectionMap)
}

export function clearSelectedCaptureSessionId(regattaId, captureSessionId) {
  if (typeof regattaId !== 'string' || regattaId.trim().length === 0) {
    return
  }

  const selectionMap = readSelectionMap()
  if (
    typeof captureSessionId === 'string' &&
    captureSessionId.trim().length > 0 &&
    selectionMap[regattaId] !== captureSessionId
  ) {
    return
  }

  delete selectionMap[regattaId]
  writeSelectionMap(selectionMap)
}

export function buildOperatorSessionsPath(regattaId) {
  return `/operator/regattas/${regattaId}/sessions`
}

export function buildOperatorSessionWorkspacePath(regattaId, captureSessionId) {
  return `${buildOperatorSessionsPath(regattaId)}/${captureSessionId}/line-scan`
}
