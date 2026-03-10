<script setup>
import { computed, onMounted, onUnmounted, ref, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import RdChip from '../../components/primitives/RdChip.vue'
import { createSseConnection } from '../../composables/useSseReconnect'
import { useFormatting } from '../../composables/useFormatting'

const { t, te, locale } = useI18n()
const route = useRoute()
const { formatElapsedTime, formatDeltaTime } = useFormatting(locale)

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
const recoveryState = ref(null)

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
const canonicalResultsHref = computed(() => {
  if (!regattaId.value) {
    return null
  }

  const params = new URLSearchParams()
  params.set('regatta_id', regattaId.value)
  return `/public/v${drawRevision.value}-${resultsRevision.value}/results?${params.toString()}`
})
const hasRecoveryAlert = computed(() => recoveryState.value !== null)
const recoveryMessage = computed(() => {
  if (recoveryState.value === 'missing_regatta') {
    return t('public.results.recovery.missing_regatta')
  }
  if (recoveryState.value === 'bootstrap_failed') {
    return t('public.results.recovery.bootstrap_failed')
  }
  return ''
})

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

    if (!response || !response.ok || !isJsonResponse(response)) {
      recoveryState.value = 'bootstrap_failed'
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
    recoveryState.value = null
  } catch (error) {
    console.warn('Failed to fetch public results', error)
    recoveryState.value = 'bootstrap_failed'
  }
}

async function bootstrapVersions() {
  if (!regattaId.value) {
    recoveryState.value = 'missing_regatta'
    return
  }

  const startDrawRevision = drawRevision.value
  const startResultsRevision = resultsRevision.value

  try {
    let response = await fetchVersions()

    if (response && (response.status === 401 || response.status === 403)) {
      await fetch('/public/session', {
        method: 'POST',
        credentials: 'include',
      })
      response = await fetchVersions()
    }

    if (!response || !response.ok || !isJsonResponse(response)) {
      recoveryState.value = 'bootstrap_failed'
      return
    }

    const data = await response.json()
    const nextDrawRevision = data?.draw_revision
    const nextResultsRevision = data?.results_revision

    if (typeof nextDrawRevision !== 'number' || typeof nextResultsRevision !== 'number') {
      recoveryState.value = 'bootstrap_failed'
      return
    }

    if (drawRevision.value !== startDrawRevision || resultsRevision.value !== startResultsRevision) {
      return
    }

    applyRevisions(data)
    recoveryState.value = null
    await fetchResults(nextDrawRevision, nextResultsRevision)
  } catch (error) {
    console.warn('Failed to bootstrap public result versions', error)
    recoveryState.value = 'bootstrap_failed'
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

function translateResultLabel(label) {
  if (typeof label !== 'string' || label.length === 0) {
    return null
  }

  const labelKey = `status.${label}`
  return te(labelKey) ? t(labelKey) : label
}

function formatValue(value) {
  return typeof value === 'string' && value.length > 0 ? value : '-'
}

function formatElapsedValue(value) {
  const formatted = formatElapsedTime(value)
  return formatted || '-'
}

function formatDeltaValue(value) {
  const formatted = formatDeltaTime(value)
  return formatted || '-'
}

function formatPenaltySeconds(value) {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return null
  }

  return t('public.results.penalties.seconds', { seconds: value })
}

function getChipVariant(status) {
  switch (status) {
    case 'withdrawn_after_draw':
    case 'dns':
    case 'dnf':
      return 'warn'
    case 'excluded':
    case 'dsq':
      return 'danger'
    case 'under_investigation':
    case 'edited':
      return 'info'
    case 'approved':
    case 'immutable':
    case 'official':
      return 'success'
    case 'offline_queued':
      return 'muted'
    default:
      return 'neutral'
  }
}

function retryResultsLoad() {
  recoveryState.value = null
  void bootstrapVersions()
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
    <div
      v-if="hasRecoveryAlert"
      class="results-recovery"
      role="alert"
      aria-live="assertive"
    >
      <p>{{ recoveryMessage }}</p>
      <button
        v-if="recoveryState === 'bootstrap_failed'"
        data-testid="public-results-retry"
        class="results-recovery__button"
        type="button"
        @click="retryResultsLoad"
      >
        {{ t('public.results.recovery.retry') }}
      </button>
    </div>
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
    <div
      v-if="canonicalResultsHref"
      data-testid="public-results-version-banner"
      class="results-version-banner"
    >
      <span>{{ t('public.results.version_banner', { drawRevision, resultsRevision }) }}</span>
      <a
        data-testid="public-results-version-link"
        class="results-version-banner__link"
        :href="canonicalResultsHref"
      >
        {{ t('public.results.version_link') }}
      </a>
    </div>
    <p v-if="resultRows.length === 0" class="results-empty">
      {{ t('public.results.empty') }}
    </p>
    <ul v-else data-testid="public-results-list" class="results-list">
      <li
        v-for="row in resultRows"
        :key="row.entry_id"
        data-testid="public-results-card"
        class="results-card"
      >
        <div class="results-card__header">
          <div>
            <p class="results-card__label">{{ t('public.results.fields.rank') }}</p>
            <p class="results-card__value results-card__value--rank">{{ row.rank ?? '-' }}</p>
          </div>
          <div class="results-card__chips">
            <RdChip
              size="sm"
              :label="translateStatus(row.status)"
              :variant="getChipVariant(row.status)"
            />
            <RdChip
              v-if="translateResultLabel(row.result_label)"
              size="sm"
              :label="translateResultLabel(row.result_label)"
              :variant="getChipVariant(row.result_label)"
            />
          </div>
        </div>
        <dl class="results-card__details">
          <div class="results-card__detail">
            <dt>{{ t('public.results.fields.crew') }}</dt>
            <dd>{{ formatValue(row.crew_name) }}</dd>
          </div>
          <div class="results-card__detail">
            <dt>{{ t('public.results.fields.club') }}</dt>
            <dd>{{ formatValue(row.club_name) }}</dd>
          </div>
          <div class="results-card__detail">
            <dt>{{ t('public.results.fields.time') }}</dt>
            <dd class="results-card__metric">{{ formatElapsedValue(row.elapsed_time_ms) }}</dd>
          </div>
          <div class="results-card__detail">
            <dt>{{ t('public.results.fields.delta') }}</dt>
            <dd class="results-card__metric">{{ formatDeltaValue(row.delta_time_ms) }}</dd>
          </div>
          <div class="results-card__detail">
            <dt>{{ t('public.results.fields.status') }}</dt>
            <dd>{{ translateStatus(row.status) }}</dd>
          </div>
        </dl>
        <p
          v-if="formatPenaltySeconds(row.penalty_seconds) || row.penalty_reason"
          class="results-card__penalty"
        >
          <strong>{{ t('public.results.penalties.label') }}:</strong>
          {{ formatPenaltySeconds(row.penalty_seconds) ?? '-' }}
          <span v-if="row.penalty_reason"> {{ row.penalty_reason }}</span>
        </p>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.public-results {
  display: grid;
  gap: var(--rd-space-3);
}

.public-results h2 {
  margin-bottom: var(--rd-space-3);
}

.live-indicator {
  font-size: 0.875rem;
}

.stale-data-banner {
  color: var(--rd-warning-700);
  font-size: 0.875rem;
}

.version-info {
  font-size: 0.875rem;
  color: var(--rd-text-muted);
}

.results-recovery,
.results-version-banner,
.results-card {
  border: 1px solid var(--rd-border);
  border-radius: 12px;
  background: var(--rd-surface);
  padding: var(--rd-space-4);
}

.results-recovery {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--rd-space-3);
  align-items: center;
}

.results-recovery__button,
.results-version-banner__link {
  min-height: var(--rd-hit);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  font: inherit;
}

.results-recovery__button {
  border: 0;
  background: var(--rd-accent);
  color: #fff;
  padding: 0 var(--rd-space-4);
  cursor: pointer;
}

.results-version-banner {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--rd-space-3);
  align-items: center;
}

.results-version-banner__link {
  color: var(--rd-accent);
  text-decoration: none;
}

.results-empty {
  color: var(--rd-text-muted);
}

.results-list {
  list-style: none;
  display: grid;
  gap: var(--rd-space-3);
  padding: 0;
  margin: 0;
}

.results-card {
  display: grid;
  gap: var(--rd-space-3);
}

.results-card__header,
.results-card__chips {
  display: flex;
  gap: var(--rd-space-2);
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
}

.results-card__label {
  margin: 0 0 var(--rd-space-1);
  color: var(--rd-text-muted);
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.results-card__value {
  margin: 0;
}

.results-card__value--rank,
.results-card__metric {
  font-variant-numeric: tabular-nums;
  font-family: 'JetBrains Mono', ui-monospace, monospace;
}

.results-card__details {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: var(--rd-space-3);
  margin: 0;
}

.results-card__detail {
  display: grid;
  gap: var(--rd-space-1);
}

.results-card__detail dt {
  color: var(--rd-text-muted);
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.results-card__detail dd {
  margin: 0;
}

.results-card__penalty {
  margin: 0;
  color: var(--rd-text);
}

@media (min-width: 768px) {
  .results-list {
    grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  }
}
</style>
