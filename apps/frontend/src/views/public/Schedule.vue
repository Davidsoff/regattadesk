<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import RdTable from '../../components/primitives/RdTable.vue'
import { createApiClient } from '../../api'

const { t } = useI18n()
const route = useRoute()
const apiClient = createApiClient({ baseUrl: '' })

const rows = ref([])
const loading = ref(false)
const errorMessage = ref('')

const drawRevision = computed(() => String(route.params.drawRevision ?? ''))
const resultsRevision = computed(() => String(route.params.resultsRevision ?? ''))
const regattaId = computed(
  () => String(route.params.regattaId ?? route.query.regatta_id ?? route.query.regattaId ?? '')
)

const isEmpty = computed(() => !loading.value && rows.value.length === 0 && !errorMessage.value)

function mapStatus(status) {
  const key = `status.${status}`
  const translated = t(key)
  return translated === key ? status : translated
}

function raceLabel(eventId) {
  return eventId ? String(eventId).slice(0, 8) : '-'
}

async function loadSchedule() {
  if (!drawRevision.value || !resultsRevision.value) {
    rows.value = []
    return
  }

  if (!regattaId.value) {
    errorMessage.value = t('public.schedule.errors.missing_regatta')
    rows.value = []
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const response = await apiClient.get(
      `/public/v${drawRevision.value}-${resultsRevision.value}/regattas/${regattaId.value}/schedule`
    )
    rows.value = Array.isArray(response?.data) ? response.data : []
  } catch {
    rows.value = []
    errorMessage.value = t('public.schedule.errors.load_failed')
  } finally {
    loading.value = false
  }
}

watch([drawRevision, resultsRevision, regattaId], loadSchedule)
onMounted(loadSchedule)
</script>

<template>
  <div class="public-schedule">
    <h2>{{ t('public.schedule.title') }}</h2>
    <p>{{ t('public.schedule.description') }}</p>

    <p v-if="errorMessage" class="error-message" role="alert">{{ errorMessage }}</p>

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
          <th scope="col">{{ t('public.schedule.headers.lane') }}</th>
          <th scope="col">{{ t('public.schedule.headers.race') }}</th>
          <th scope="col">{{ t('public.schedule.headers.status') }}</th>
        </tr>
      </template>

      <tr v-for="row in rows" :key="row.entry_id">
        <td class="rd-tabular-nums">{{ row.lane ?? '-' }}</td>
        <td>{{ raceLabel(row.event_id) }}</td>
        <td>{{ mapStatus(row.status) }}</td>
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

.version-info {
  margin-top: var(--rd-space-4);
  font-size: 0.875rem;
  color: var(--rd-text-muted);
}
</style>
