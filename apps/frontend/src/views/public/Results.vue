<script setup>
import { computed, onMounted, onUnmounted, ref, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { createSseConnection } from '../../composables/useSseReconnect'

const { t, te } = useI18n()
const route = useRoute()

function toRevisionNumber(value) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : 0
}

const drawRevision = ref(toRevisionNumber(route.params.drawRevision))
const resultsRevision = ref(toRevisionNumber(route.params.resultsRevision))
const isLive = ref(false)
const hasConnected = ref(false)
const isDataStale = ref(false)
const resultRows = ref([])

let connection = null
let latestResultsRequestId = 0
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

function isJsonResponse(response) {
  const contentType = response.headers?.get?.('content-type') ?? ''
  return contentType.includes('application/json')
}

function applyRevisions(data) {
  drawRevision.value = data.draw_revision
  resultsRevision.value = data.results_revision
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

async function fetchResults(
  drawRevisionOverride = drawRevision.value,
  resultsRevisionOverride = resultsRevision.value,
  expectedDrawRevision = drawRevisionOverride,
  expectedResultsRevision = resultsRevisionOverride,
) {
  if (!regattaId.value) {
    return
  }

  const requestId = ++latestResultsRequestId
  const requestUrl = `/public/v${drawRevisionOverride}-${resultsRevisionOverride}/regattas/${regattaId.value}/results`

  try {
    const response = await fetch(requestUrl, {
      credentials: 'include',
    })

    if (!response.ok || !isJsonResponse(response)) {
      return
    }

    const data = await response.json()
    if (drawRevision.value !== expectedDrawRevision || resultsRevision.value !== expectedResultsRevision) {
      return
    }
    if (requestId !== latestResultsRequestId) {
      return
    }
    resultRows.value = Array.isArray(data?.data) ? data.data : []
  } catch (error) {
    console.warn('Failed to fetch public results', error)
  }
}

async function bootstrapVersions() {
  if (!regattaId.value) {
    return
  }

  const startDrawRevision = drawRevision.value
  const startResultsRevision = resultsRevision.value

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
    const nextDrawRevision = data?.draw_revision
    const nextResultsRevision = data?.results_revision

    if (typeof nextDrawRevision !== 'number' || typeof nextResultsRevision !== 'number') {
      return
    }

    if (drawRevision.value !== startDrawRevision || resultsRevision.value !== startResultsRevision) {
      return
    }

    applyRevisions(data)
    await fetchResults(nextDrawRevision, nextResultsRevision)
  } catch (error) {
    console.warn('Failed to bootstrap public result versions', error)
  }
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
      console.warn('Public results SSE connection error', event)
    },
  })
}

function translateStatus(status) {
  if (typeof status !== 'string' || status.length === 0) {
    return '-'
  }
  const statusKey = `status.${status}`
  return te(statusKey) ? t(statusKey) : status
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
    <p data-testid="public-live-indicator" class="live-indicator" aria-live="polite">
      {{ isLive ? t('live.live') : t('live.offline') }}
    </p>
    <p
      v-if="isDataStale"
      data-testid="public-stale-data-banner"
      class="stale-data-banner"
      aria-live="polite"
    >
      {{ t('live.stale_data_message') }}
    </p>
    <p class="version-info">
      {{ t('public.version.draw') }}: {{ drawRevision }},
      {{ t('public.version.results') }}: {{ resultsRevision }}
    </p>
    <ul data-testid="public-results-list" class="results-list">
      <li v-for="row in resultRows" :key="row.entry_id">
        {{ row.rank ?? '-' }}. {{ row.crew_name }} ({{ translateStatus(row.status) }})
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
