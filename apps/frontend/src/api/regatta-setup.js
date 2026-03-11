export function createRegattaSetupApi(client) {
  return {
    listAthletes(params) {
      return client.get('/athletes', { params })
    },

    createAthlete(payload) {
      return client.post('/athletes', payload)
    },

    listEventGroups(regattaId, params) {
      return client.get(`/regattas/${regattaId}/event-groups`, { params })
    },

    createEventGroup(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/event-groups`, payload)
    },

    createEvent(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/events`, payload)
    },

    listEvents(regattaId) {
      return client.get(`/regattas/${regattaId}/events`)
    },

    createCrew(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/crews`, payload)
    },

    listCrews(regattaId) {
      return client.get(`/regattas/${regattaId}/crews`)
    },

    listEntries(regattaId, params) {
      return client.get(`/regattas/${regattaId}/entries`, { params })
    },

    createEntry(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/entries`, payload)
    },

    withdrawEntry(regattaId, entryId, payload) {
      return client.post(`/regattas/${regattaId}/entries/${entryId}/withdraw`, payload)
    },

    reinstateEntry(regattaId, entryId, payload) {
      return client.post(`/regattas/${regattaId}/entries/${entryId}/reinstate`, payload)
    }
  }
}
