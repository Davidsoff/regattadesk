export function normalizeCaptureSession(session) {
  if (!session || typeof session !== 'object') {
    return null
  }

  let captureSessionId = null
  if (typeof session.capture_session_id === 'string' && session.capture_session_id.trim().length > 0) {
    captureSessionId = session.capture_session_id.trim()
  } else if (typeof session.id === 'string' && session.id.trim().length > 0) {
    captureSessionId = session.id.trim()
  }

  if (!captureSessionId) {
    return null
  }

  return {
    id: captureSessionId,
    capture_session_id: captureSessionId,
    regatta_id: session.regatta_id ?? null,
    block_id: session.block_id ?? null,
    station: session.station ?? '',
    device_id: session.device_id ?? '',
    session_type: session.session_type ?? '',
    state: session.state ?? '',
    server_time_at_start: session.server_time_at_start ?? null,
    device_monotonic_offset_ms: session.device_monotonic_offset_ms ?? null,
    fps: session.fps ?? null,
    is_synced: session.is_synced !== false,
    drift_exceeded_threshold: session.drift_exceeded_threshold === true,
    unsynced_reason: typeof session.unsynced_reason === 'string' ? session.unsynced_reason : '',
    closed_at: session.closed_at ?? null,
    close_reason: session.close_reason ?? null,
    created_at: session.created_at ?? null,
    updated_at: session.updated_at ?? null
  }
}

export function normalizeCaptureSessionList(response) {
  const sessions = Array.isArray(response) ? response : response?.capture_sessions
  if (!Array.isArray(sessions)) {
    return []
  }

  return sessions.map(normalizeCaptureSession).filter(Boolean)
}

export function summarizeCaptureSessionSyncState(session, t) {
  if (session?.drift_exceeded_threshold) {
    return t('operator.regatta.sync_attention')
  }

  if (session?.is_synced === false) {
    return t('operator.regatta.sync_pending', {
      reason:
        session.unsynced_reason && session.unsynced_reason.trim().length > 0
          ? session.unsynced_reason
          : t('operator.regatta.sync_pending_default')
    })
  }

  return t('operator.regatta.sync_synced')
}
