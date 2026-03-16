<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { createApiClient, createExportApi } from '../../api'
import ExportJobStatus from '../../components/export/ExportJobStatus.vue'
import PrintHeader from '../../components/print/PrintHeader.vue'
import { useExportJob } from '../../composables/useExportJob'

const { t } = useI18n()
const route = useRoute()
const regattaId = computed(() => route.params.regattaId)

const apiClient = createApiClient()
const exportApi = createExportApi(apiClient)
const { status: exportStatus, jobId, downloadUrl, error: exportError, startExport, resetState: resetExportState } = useExportJob(
  exportApi,
  regattaId
)

const regattaName = ref(`Regatta ${route.params.regattaId}`)
const drawRevision = ref(0)
const resultsRevision = ref(0)
const generatedAt = ref(new Date().toISOString())
const regattaTimezone = ref(Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC')

function isJsonResponse(response) {
  const contentType = response?.headers?.get?.('content-type') ?? ''
  return contentType.includes('application/json')
}

function resetMetadata() {
  generatedAt.value = new Date().toISOString()
  regattaName.value = `Regatta ${regattaId.value}`
  drawRevision.value = 0
  resultsRevision.value = 0
  regattaTimezone.value = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
}

function applyRegattaMetadata(regatta) {
  if (!regatta || typeof regatta !== 'object') {
    return
  }

  if (typeof regatta.name === 'string' && regatta.name.trim().length > 0) {
    regattaName.value = regatta.name.trim()
  }
  if (Number.isInteger(regatta.draw_revision) && regatta.draw_revision >= 0) {
    drawRevision.value = regatta.draw_revision
  }
  if (Number.isInteger(regatta.results_revision) && regatta.results_revision >= 0) {
    resultsRevision.value = regatta.results_revision
  }
  if (typeof regatta.timezone === 'string' && regatta.timezone.trim().length > 0) {
    regattaTimezone.value = regatta.timezone.trim()
  }
}

function applyRevisionMetadata(versions) {
  if (Number.isInteger(versions?.draw_revision) && versions.draw_revision >= 0) {
    drawRevision.value = versions.draw_revision
  }
  if (Number.isInteger(versions?.results_revision) && versions.results_revision >= 0) {
    resultsRevision.value = versions.results_revision
  }
}

function fetchVersions() {
  return fetch(`/public/regattas/${regattaId.value}/versions`, {
    credentials: 'include'
  })
}

async function loadRegattaMetadata() {
  resetMetadata()
  try {
    const response = await fetch(`/api/v1/regattas/${regattaId.value}`, {
      credentials: 'include'
    })

    if (response.ok && isJsonResponse(response)) {
      applyRegattaMetadata(await response.json())
    }
  } catch {
    // Keep route-derived fallback metadata when regatta details are unavailable.
  }

  try {
    let response = await fetchVersions()

    if (response.status === 401 || response.status === 403) {
      await fetch('/public/session', {
        method: 'POST',
        credentials: 'include'
      })
      response = await fetchVersions()
    }

    if (!response.ok || !isJsonResponse(response)) {
      return
    }

    applyRevisionMetadata(await response.json())
  } catch {
    // Keep previously loaded revision values when the public versions endpoint is unavailable.
  }
}

const printHeaderPreview = computed(() => ({
  regattaName: regattaName.value,
  drawRevision: drawRevision.value,
  resultsRevision: resultsRevision.value,
  pageNumber: 1,
  totalPages: 1,
  timestamp: generatedAt.value,
  regattaTimezone: regattaTimezone.value
}))

watch(
  () => route.params.regattaId,
  () => {
    resetExportState()
    void loadRegattaMetadata()
  }
)

onMounted(() => {
  void loadRegattaMetadata()
})
</script>

<template>
  <section class="regatta-printables" data-testid="printables-page">
    <header class="regatta-printables__header">
      <h2>{{ t('staff.printables.title') }}</h2>
      <p>{{ t('staff.printables.description') }}</p>
    </header>

    <section class="regatta-printables__export">
      <button
        type="button"
        data-testid="export-printables-button"
        :aria-label="t('staff.regatta_detail.export.export_printables_aria')"
        :disabled="exportStatus === 'pending' || exportStatus === 'processing'"
        @click="startExport"
      >
        {{ t('staff.regatta_detail.export.export_printables') }}
      </button>

      <div data-testid="printables-job-status">
        <ExportJobStatus
          :status="exportStatus"
          :job-id="jobId"
          :download-url="downloadUrl"
          :error="exportError"
          :on-start="startExport"
        />
      </div>
    </section>

    <section class="regatta-printables__preview">
      <h3>{{ t('staff.printables.header_preview') }}</h3>
      <div data-testid="printables-header-preview" class="regatta-printables__preview-card">
        <PrintHeader v-bind="printHeaderPreview" />
      </div>
    </section>
  </section>
</template>

<style scoped>
.regatta-printables {
  display: grid;
  gap: var(--rd-space-5);
}

.regatta-printables__header h2,
.regatta-printables__preview h3 {
  margin: 0 0 var(--rd-space-2);
}

.regatta-printables__header p {
  margin: 0;
  color: var(--rd-text-muted);
}

.regatta-printables__export {
  display: grid;
  gap: var(--rd-space-3);
  justify-items: start;
}

.regatta-printables__export button {
  padding: var(--rd-space-2) var(--rd-space-4);
}

.regatta-printables__preview-card {
  padding: var(--rd-space-4);
  border: 1px solid var(--rd-border);
  background: var(--rd-surface);
}
</style>
