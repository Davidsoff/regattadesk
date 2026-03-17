export function createAdjudicationApi(client) {
  return {
    async listInvestigations(regattaId) {
      return client.get(`/regattas/${regattaId}/adjudication/investigations`)
    },

    async openInvestigation(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/adjudication/investigations`, payload)
    },

    async getEntryDetail(regattaId, entryId) {
      return client.get(`/regattas/${regattaId}/adjudication/entries/${entryId}`)
    },

    async applyPenalty(regattaId, entryId, payload) {
      return client.post(`/regattas/${regattaId}/adjudication/entries/${entryId}/penalty`, payload)
    },

    async applyDsq(regattaId, entryId, payload) {
      return client.post(`/regattas/${regattaId}/adjudication/entries/${entryId}/dsq`, payload)
    },

    async applyExclusion(regattaId, entryId, payload) {
      return client.post(`/regattas/${regattaId}/adjudication/entries/${entryId}/exclude`, payload)
    },

    async revertDsq(regattaId, entryId, payload) {
      return client.post(`/regattas/${regattaId}/adjudication/entries/${entryId}/revert_dsq`, payload)
    }
  }
}
