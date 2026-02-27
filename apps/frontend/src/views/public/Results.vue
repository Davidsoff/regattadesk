<script setup>
import { computed, onMounted, onUnmounted, ref, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { createSseConnection } from '../../composables/useSseReconnect'

const { t } = useI18n()
const route = useRoute()

const drawRevision = ref(Number(route.params.drawRevision))
const resultsRevision = ref(Number(route.params.resultsRevision))
const isLive = ref(false)
const hasConnected = ref(false)
const isDataStale = ref(false)
const resultRows = ref([])

let connection = null
const REGATTA_ID_STORAGE_KEY = 'regattadesk_public_regatta_id'

const regattaId = computed(() => {
  if (typeof route.query.regatta_id === 'string' && route.query.regatta_id.length > 0) {
    return route.query.regatta_id
  }

  if (typeof sessionStorage !== 'undefined') {
    return sessionStorage.getItem(REGATTA_ID_STORAGE_KEY)
  }

  return null
})
const versionsUrl = computed(() => `/public/regattas/${regattaId.value}/versions`)
const eventsUrl = computed(() => `/public/regattas/${regattaId.value}/events`)
const resultsUrl = computed(
  () => `/public/v${drawRevision.value}-${resultsRevision.value}/regattas/${regattaId.value}/results`,
)

function isJsonResponse(response) {
  const contentType = response.headers?.get?.('content-type') ?? ''
  return contentType.includes('application/json')
}

function applyRevisions(data) {
  drawRevision.value = data.draw_revision
  resultsRevision.value = data.results_revision
}

function parseRevisionEventData(event) {
  if (!event?.data) {
    return null
  }

  try {
    return JSON.parse(event.data)
  } catch {
    return null
  }
}

watchEffect(() => {
  if (typeof route.query.regatta_id === 'string' && route.query.regatta_id.length > 0 && typeof sessionStorage !== 'undefined') {
    sessionStorage.setItem(REGATTA_ID_STORAGE_KEY, route.query.regatta_id)
  }
})

function fetchVersions() {
  return fetch(versionsUrl.value, {
    credentials: 'include',
  })
}

async function fetchResults() {
  if (!regattaId.value) {
    return
  }

  try {
    const response = await fetch(resultsUrl.value, {
      credentials: 'include',
    })

    if (!response.ok || !isJsonResponse(response)) {
      return
    }

    const data = await response.json()
    resultRows.value = Array.isArray(data?.data) ? data.data : []
  } catch {}
}

async function bootstrapVersions() {
  if (!regattaId.value) {
    return
  }

  try {
    let response = await fetchVersions()

    if (response.status === 401 || response.status === 403) {
      await fetch('/public/session', {
        method: 'POST',
        credentials: 'include',
      })
      response = await fetchVersions()
    }

    if (!response.ok || !isJsonResponse(response)) {
      return
    }

    const data = await response.json()
    applyRevisions(data)
    await fetchResults()
  } catch {}
}

function setupLiveEvents() {
  if (!regattaId.value) {
    return
  }

  connection = createSseConnection(eventsUrl.value, {
    onConnectionChange: (connected) => {
      isLive.value = connected
      if (connected) {
        hasConnected.value = true
        isDataStale.value = false
        void bootstrapVersions()
      } else {
        isDataStale.value = hasConnected.value
      }
    },
    onSnapshot: (data) => {
      applyRevisions(data)
      void fetchResults()
    },
    onDrawRevision: (data) => {
      applyRevisions(data)
      void fetchResults()
    },
    onResultsRevision: (data) => {
      applyRevisions(data)
      void fetchResults()
    },
    onError: (event) => {
      const data = parseRevisionEventData(event)
      if (!data) {
        return
      }

      applyRevisions(data)
      void fetchResults()
    },
  })
}

onMounted(async () => {
  await bootstrapVersions()
  setupLiveEvents()
})

onUnmounted(() => {
  if (connection && typeof connection.close === 'function') {
    connection.close()
    connection = null
  }
})
</script>

<template>
  <div class="public-results">
    <h2>{{ t('public.results.title') }}</h2>
    <p>{{ t('public.results.description') }}</p>
    <p data-testid="public-live-indicator" class="live-indicator">
      {{ isLive ? 'Live' : 'Offline' }}
    </p>
    <p v-if="isDataStale" data-testid="public-stale-data-banner" class="stale-data-banner">
      Showing cached results. Reconnecting for latest updates.
    </p>
    <p class="version-info">
      {{ t('public.version.draw') }}: {{ drawRevision }},
      {{ t('public.version.results') }}: {{ resultsRevision }}
    </p>
    <ul data-testid="public-results-list" class="results-list">
      <li v-for="row in resultRows" :key="row.entry_id">
        {{ row.rank ?? '-' }}. {{ row.crew_name }} ({{ row.status }})
      </li>
    </ul>
  </div>
</template>

<style scoped>
.public-results h2 {
  margin-bottom: var(--rd-space-3);
}

.live-indicator {
  margin-top: var(--rd-space-2);
  font-size: 0.875rem;
}

.stale-data-banner {
  margin-top: var(--rd-space-2);
  color: var(--rd-warning-700);
  font-size: 0.875rem;
}

.version-info {
  margin-top: var(--rd-space-4);
  font-size: 0.875rem;
  color: var(--rd-text-muted);
}

.results-list {
  margin-top: var(--rd-space-4);
  padding-left: var(--rd-space-5);
}
</style>
