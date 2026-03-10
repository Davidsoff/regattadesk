function normalizePdfResponse(result, tokenId) {
  if (result && typeof result === 'object' && !Array.isArray(result)) {
    return result
  }

  return {
    filename: `operator-token-${tokenId}.pdf`,
    contentType: 'application/pdf',
    size: 0,
  }
}

export function createStaffOperatorAccessApi(client) {
  return {
    async listTokens(regattaId) {
      return client.get(`/regattas/${regattaId}/operator/tokens`)
    },

    async createToken(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/operator/tokens`, payload)
    },

    async revokeToken(regattaId, tokenId) {
      return client.post(`/regattas/${regattaId}/operator/tokens/${tokenId}/revoke`, {})
    },

    async exportTokenPdf(regattaId, tokenId) {
      const result = await client.get(`/regattas/${regattaId}/operator/tokens/${tokenId}/export_pdf`)
      return normalizePdfResponse(result, tokenId)
    },

    async getStationHandoff(regattaId, handoffId) {
      return client.get(`/regattas/${regattaId}/operator/station_handoffs/${handoffId}`)
    },

    async adminRevealPin(regattaId, handoffId) {
      return client.post(`/regattas/${regattaId}/operator/station_handoffs/${handoffId}/admin_reveal_pin`, {})
    },
  }
}
