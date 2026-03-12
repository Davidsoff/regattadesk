/**
 * Operator API module for BC06 operator capture and line-scan handoff flows.
 */
function withOperatorToken(operatorToken) {
  const headers = {}
  if (operatorToken) {
    headers.x_operator_token = operatorToken
  }

  return {
    headers
  }
}

export function createOperatorApi(client, options = {}) {
  const { getOperatorToken = () => '' } = options

  function authOptions() {
    const operatorToken = getOperatorToken()
    return withOperatorToken(operatorToken)
  }

  return {
    async requestStationHandoff(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/operator/station_handoffs`, payload, authOptions())
    },

    async getStationHandoffStatus(regattaId, handoffId) {
      return client.get(`/regattas/${regattaId}/operator/station_handoffs/${handoffId}`, authOptions())
    },

    async revealStationHandoffPin(regattaId, handoffId) {
      return client.post(
        `/regattas/${regattaId}/operator/station_handoffs/${handoffId}/reveal_pin`,
        {},
        authOptions()
      )
    },

    async completeStationHandoff(regattaId, handoffId, payload) {
      return client.post(
        `/regattas/${regattaId}/operator/station_handoffs/${handoffId}/complete`,
        payload,
        authOptions()
      )
    },

    async cancelStationHandoff(regattaId, handoffId) {
      return client.post(
        `/regattas/${regattaId}/operator/station_handoffs/${handoffId}/cancel`,
        {},
        authOptions()
      )
    },

    // Capture Session Management
    async createCaptureSession(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/operator/capture_sessions`, payload, authOptions())
    },

    async listCaptureSessions(regattaId, filters = {}) {
      const query = {}
      if (filters.station) query.station = filters.station
      if (filters.state) query.state = filters.state
      if (filters.block_id) query.block_id = filters.block_id
      if (filters.session_type) query.session_type = filters.session_type

      return client.get(`/regattas/${regattaId}/operator/capture_sessions`, { ...authOptions(), query })
    },

    async closeCaptureSession(regattaId, captureSessionId, payload) {
      return client.post(
        `/regattas/${regattaId}/operator/capture_sessions/${captureSessionId}/close`,
        payload,
        authOptions()
      )
    },

    async getCaptureSession(regattaId, captureSessionId) {
      return client.get(
        `/regattas/${regattaId}/operator/capture_sessions/${captureSessionId}`,
        authOptions()
      )
    },

    // Marker Management
    async createMarker(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/operator/markers`, payload, authOptions())
    },

    async updateMarker(regattaId, markerId, payload) {
      return client.patch(`/regattas/${regattaId}/operator/markers/${markerId}`, payload, authOptions())
    },

    async deleteMarker(regattaId, markerId) {
      return client.delete(`/regattas/${regattaId}/operator/markers/${markerId}`, authOptions())
    },

    async linkMarker(regattaId, markerId, payload) {
      return client.post(
        `/regattas/${regattaId}/operator/markers/${markerId}/link`,
        payload,
        authOptions()
      )
    },

    async unlinkMarker(regattaId, markerId) {
      return client.post(
        `/regattas/${regattaId}/operator/markers/${markerId}/unlink`,
        {},
        authOptions()
      )
    },

    async listMarkers(regattaId, filters = {}) {
      const query = {}
      if (filters.capture_session_id) query.capture_session_id = filters.capture_session_id

      return client.get(`/regattas/${regattaId}/operator/markers`, { ...authOptions(), query })
    }
  }
}
