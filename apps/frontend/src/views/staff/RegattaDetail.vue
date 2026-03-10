<script setup>
import { watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { createApiClient, createExportApi } from '../../api'
import { useExportJob } from '../../composables/useExportJob'
import ExportJobStatus from '../../components/export/ExportJobStatus.vue'

const { t } = useI18n()
const route = useRoute()

// Export functionality
const apiClient = createApiClient()
const exportApi = createExportApi(apiClient)
const { status: exportStatus, jobId, downloadUrl, error: exportError, startExport, resetState: resetExportState } = useExportJob(
  exportApi,
  () => route.params.regattaId
)
watch(
  () => route.params.regattaId,
  () => {
    resetExportState()
  }
)
</script>

<template>
  <div class="regatta-detail">
    <h2>{{ t('staff.regatta.title') }}</h2>
    <p>{{ t('staff.regatta.id') }}: {{ route.params.regattaId }}</p>
    <section class="regatta-detail__setup-nav" data-testid="setup-nav">
      <h3>Setup workflows</h3>
      <p>Use the route-backed setup sections for event groups, events, athletes, crews, and entries.</p>
      <nav aria-label="Regatta setup">
        <RouterLink :to="{ name: 'staff-regatta-setup-event-groups', params: route.params }">Event groups</RouterLink>
        <RouterLink :to="{ name: 'staff-regatta-setup-events', params: route.params }">Events</RouterLink>
        <RouterLink :to="{ name: 'staff-regatta-setup-athletes', params: route.params }">Athletes</RouterLink>
        <RouterLink :to="{ name: 'staff-regatta-setup-crews', params: route.params }">Crews</RouterLink>
        <RouterLink :to="{ name: 'staff-regatta-setup-entries', params: route.params }">Entries</RouterLink>
      </nav>
    </section>

    <!-- Export Section -->
    <section data-testid="export-section">
      <h3>{{ t('staff.regatta_detail.sections.export') }}</h3>
      <button
        type="button"
        data-testid="export-printables-button"
        :aria-label="t('staff.regatta_detail.export.export_printables_aria')"
        :disabled="exportStatus === 'pending' || exportStatus === 'processing'"
        @click="startExport"
      >
        {{ t('staff.regatta_detail.export.export_printables') }}
      </button>
      <ExportJobStatus
        :status="exportStatus"
        :job-id="jobId"
        :download-url="downloadUrl"
        :error="exportError"
        :on-start="startExport"
      />
    </section>
  </div>
</template>

<style scoped>
.regatta-detail {
  display: grid;
  gap: var(--rd-space-4);
}

.regatta-detail h2 {
  margin-bottom: var(--rd-space-2);
}

.regatta-detail__setup-nav nav {
  display: flex;
  flex-wrap: wrap;
  gap: var(--rd-space-2);
}
</style>
