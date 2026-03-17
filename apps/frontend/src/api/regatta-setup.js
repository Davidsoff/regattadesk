export function createRegattaSetupApi(client) {
  return {
    listAthletes(params) {
      return client.get('/athletes', { params })
    },

    createAthlete(payload) {
      return client.post('/athletes', payload)
    },

    updateAthlete(athleteId, payload) {
      return client.patch(`/athletes/${athleteId}`, payload)
    },

    deleteAthlete(athleteId) {
      return client.delete(`/athletes/${athleteId}`)
    },

    listEventGroups(regattaId, params) {
      return client.get(`/regattas/${regattaId}/event-groups`, { params })
    },

    createEventGroup(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/event-groups`, payload)
    },

    updateEventGroup(regattaId, eventGroupId, payload) {
      return client.patch(`/regattas/${regattaId}/event-groups/${eventGroupId}`, payload)
    },

    deleteEventGroup(regattaId, eventGroupId) {
      return client.delete(`/regattas/${regattaId}/event-groups/${eventGroupId}`)
    },

    createEvent(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/events`, payload)
    },

    listEvents(regattaId, params) {
      return client.get(`/regattas/${regattaId}/events`, { params })
    },

    updateEvent(regattaId, eventId, payload) {
      return client.patch(`/regattas/${regattaId}/events/${eventId}`, payload)
    },

    deleteEvent(regattaId, eventId) {
      return client.delete(`/regattas/${regattaId}/events/${eventId}`)
    },

    createCrew(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/crews`, payload)
    },

    listCrews(regattaId, params) {
      return client.get(`/regattas/${regattaId}/crews`, { params })
    },

    updateCrew(regattaId, crewId, payload) {
      return client.patch(`/regattas/${regattaId}/crews/${crewId}`, payload)
    },

    deleteCrew(regattaId, crewId) {
      return client.delete(`/regattas/${regattaId}/crews/${crewId}`)
    },

    listEntries(regattaId, params) {
      return client.get(`/regattas/${regattaId}/entries`, { params })
    },

    createEntry(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/entries`, payload)
    },

    updateEntry(regattaId, entryId, payload) {
      return client.patch(`/regattas/${regattaId}/entries/${entryId}`, payload)
    },

    deleteEntry(regattaId, entryId) {
      return client.delete(`/regattas/${regattaId}/entries/${entryId}`)
    },

    withdrawEntry(regattaId, entryId, payload) {
      return client.post(`/regattas/${regattaId}/entries/${entryId}/withdraw`, payload)
    },

    reinstateEntry(regattaId, entryId, payload) {
      return client.post(`/regattas/${regattaId}/entries/${entryId}/reinstate`, payload)
    }
  }
}
