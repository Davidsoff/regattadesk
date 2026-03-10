<script setup>
import { computed, onMounted, ref, watch, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import RdTable from '../../components/primitives/RdTable.vue'
import { createApiClient } from '../../api'
import { useFormatting } from '../../composables/useFormatting'

const { locale, t } = useI18n()
const route = useRoute()
const router = useRouter()
const apiClient = createApiClient({ baseUrl: '' })
const { formatTimestampDisplay } = useFormatting(locale)

const REGATTA_ID_STORAGE_KEY = 'regattadesk_public_regatta_id'

const rows = ref([])
const loading = ref(false)
const errorMessage = ref('')
const recoveredRegattaId = ref('')

const drawRevision = computed(() => String(route.params.drawRevision ?? ''))
const resultsRevision = computed(() => String(route.params.resultsRevision ?? ''))
const regattaIdFromQuery = computed(
  () => String(route.params.regattaId ?? route.query.regatta_id ?? route.query.regattaId ?? '')
)
const regattaTimezone = computed(() => {
  const candidate = route.query.timezone ?? route.query.time_zone
  return typeof candidate === 'string' && candidate.length > 0 ? candidate : null
})
const effectiveRegattaId = computed(() => regattaIdFromQuery.value || recoveredRegattaId.value)

const isEmpty = computed(() => !loading.value && rows.value.length === 0 && !errorMessage.value)
const showSavedRegattaRecovery = computed(
  () => !regattaIdFromQuery.value && Boolean(recoveredRegattaId.value)
)

function mapStatus(status) {
  const key = `status.${status}`
  const translated = t(key)
  return translated === key ? status : translated
}

function eventLabel(eventId) {
  if (!eventId) {
    return '-'
  }

  const value = String(eventId)
  return value.includes('-') && value.length > 12 ? value.slice(0, 8) : value
}

function formatScheduleTime(value) {
  const formatted = formatTimestampDisplay(value, regattaTimezone.value)
  return formatted || '-'
}

function readSessionStorage(key) {
  if (typeof sessionStorage === 'undefined') {
    return ''
  }

  try {
    return String(sessionStorage.getItem(key) ?? '')
  } catch {
    return ''
  }
}

function savedRegattaId() {
  return readSessionStorage(REGATTA_ID_STORAGE_KEY)
}

function writeSessionStorage(key, value) {
  if (!value || typeof sessionStorage === 'undefined') {
    return
  }

  try {
    sessionStorage.setItem(key, value)
  } catch {
    // Some browsers can throw on sessionStorage access in private modes.
  }
}

function syncRecoveredRegattaId() {
  if (regattaIdFromQuery.value) {
    recoveredRegattaId.value = ''
    return
  }

  recoveredRegattaId.value = savedRegattaId()
}

function persistRegattaId(value) {
  writeSessionStorage(REGATTA_ID_STORAGE_KEY, value)
}

async function applyRecoveredRegatta() {
  if (!recoveredRegattaId.value) {
    return
  }

  try {
    await router.replace({
      query: {
        ...route.query,
        regatta_id: recoveredRegattaId.value,
      },
    })
  } catch {
    // Ignore failed navigation and leave the current recovery state intact.
  }
}

async function loadSchedule() {
  if (!drawRevision.value || !resultsRevision.value) {
    rows.value = []
    return
  }

  if (!effectiveRegattaId.value) {
    errorMessage.value = t('public.schedule.errors.missing_regatta')
    rows.value = []
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const response = await apiClient.get(
      `/public/v${drawRevision.value}-${resultsRevision.value}/regattas/${effectiveRegattaId.value}/schedule`
    )
    rows.value = Array.isArray(response?.data) ? response.data : []
  } catch {
    rows.value = []
    errorMessage.value = t('public.schedule.errors.load_failed')
  } finally {
    loading.value = false
  }
}

watchEffect(() => {
  if (regattaIdFromQuery.value) {
    persistRegattaId(regattaIdFromQuery.value)
  }
})

watch([drawRevision, resultsRevision, regattaIdFromQuery], syncRecoveredRegattaId, { immediate: true })
watch([drawRevision, resultsRevision, effectiveRegattaId], loadSchedule)
onMounted(loadSchedule)
</script>

<template>
  <div class="public-schedule">
    <h2>{{ t('public.schedule.title') }}</h2>
    <p>{{ t('public.schedule.description') }}</p>

    <p v-if="errorMessage" class="error-message" role="alert">{{ errorMessage }}</p>
    <div v-if="showSavedRegattaRecovery" class="recovery-banner">
      <p class="recovery-banner__text">{{ t('public.schedule.recovery.saved_regatta_hint') }}</p>
      <button
        type="button"
        class="recovery-banner__action"
        :aria-label="t('public.schedule.recovery.use_saved_regatta')"
        @click="applyRecoveredRegatta"
      >
        {{ t('public.schedule.recovery.use_saved_regatta') }}
      </button>
    </div>

    <RdTable
      :caption="t('public.schedule.title')"
      :loading="loading"
      :is-empty="isEmpty"
      :empty-text="t('public.schedule.empty')"
      :clearable="false"
      sticky
    >
      <template #header>
        <tr>
          <th scope="col">{{ t('public.schedule.headers.time') }}</th>
          <th scope="col">{{ t('public.schedule.headers.event') }}</th>
          <th scope="col">{{ t('public.schedule.headers.crew') }}</th>
          <th scope="col">{{ t('public.schedule.headers.club') }}</th>
          <th scope="col">{{ t('public.schedule.headers.bib_lane') }}</th>
          <th scope="col">{{ t('public.schedule.headers.status') }}</th>
        </tr>
      </template>

      <tr v-for="row in rows" :key="row.entry_id">
        <td :data-label="t('public.schedule.headers.time')" class="rd-tabular-nums">
          {{ formatScheduleTime(row.scheduled_start_time) }}
        </td>
        <td :data-label="t('public.schedule.headers.event')">{{ eventLabel(row.event_id) }}</td>
        <td :data-label="t('public.schedule.headers.crew')">{{ row.crew_name ?? '-' }}</td>
        <td :data-label="t('public.schedule.headers.club')">{{ row.club_name ?? '-' }}</td>
        <td :data-label="t('public.schedule.headers.bib_lane')" class="rd-tabular-nums">
          {{ row.bib ?? '-' }} / {{ row.lane ?? '-' }}
        </td>
        <td :data-label="t('public.schedule.headers.status')">{{ mapStatus(row.status) }}</td>
      </tr>
    </RdTable>

    <p class="version-info">
      {{ t('public.version.draw') }}: {{ route.params.drawRevision }},
      {{ t('public.version.results') }}: {{ route.params.resultsRevision }}
    </p>
  </div>
</template>

<style scoped>
.public-schedule h2 {
  margin-bottom: var(--rd-space-3);
}

.error-message {
  margin-bottom: var(--rd-space-3);
  color: var(--rd-error);
}

.recovery-banner {
  margin-bottom: var(--rd-space-4);
  padding: var(--rd-space-3);
  border: 1px solid var(--rd-border);
  border-radius: var(--rd-border-radius);
  background: var(--rd-surface);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--rd-space-3);
}

.recovery-banner__text {
  margin: 0;
  color: var(--rd-text-muted);
}

.recovery-banner__action {
  min-height: var(--rd-hit);
  padding: var(--rd-space-2) var(--rd-space-4);
  border: 1px solid var(--rd-accent);
  border-radius: var(--rd-border-radius);
  background: var(--rd-accent);
  color: white;
  font: inherit;
  cursor: pointer;
}

.version-info {
  margin-top: var(--rd-space-4);
  font-size: 0.875rem;
  color: var(--rd-text-muted);
}

@media (max-width: 767px) {
  .recovery-banner {
    align-items: stretch;
    flex-direction: column;
  }

  :deep(.rd-table-head) {
    position: absolute;
    inset-inline-start: 0;
    inline-size: 1px;
    block-size: 1px;
    overflow: hidden;
    clip-path: inset(50%);
  }

  :deep(.rd-table-body tr) {
    display: grid;
    gap: var(--rd-space-2);
    padding: var(--rd-space-3);
  }

  :deep(.rd-table-body td) {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: var(--rd-space-3);
    padding: 0;
  }

  :deep(.rd-table-body td::before) {
    content: attr(data-label);
    color: var(--rd-text-muted);
    font-weight: 600;
  }
}
</style>
