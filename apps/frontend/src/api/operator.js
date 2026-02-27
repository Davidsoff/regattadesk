/**
 * Operator API module for BC06 operator capture and line-scan handoff flows.
 */
function withOperatorToken(operatorToken) {
  return {
    headers: {
      x_operator_token: operatorToken
    }
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
    }
  }
}
