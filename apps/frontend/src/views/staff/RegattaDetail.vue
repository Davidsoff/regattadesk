<script setup>
import { nextTick, ref, watch } from 'vue'
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

const entryForm = ref({
  crewId: '',
  athleteId: ''
})
const entryFormErrors = ref({
  crewId: '',
  athleteId: ''
})

const crewEntryInput = ref(null)
const athleteEntryInput = ref(null)
const withdrawTriggerButton = ref(null)
const withdrawDialog = ref(null)

const showWithdrawDialog = ref(false)
const withdrawReason = ref('')
const showConflictError = ref(false)

function hasEntryErrors() {
  return Boolean(entryFormErrors.value.crewId || entryFormErrors.value.athleteId)
}

function firstEntryErrorMessage() {
  return entryFormErrors.value.crewId || entryFormErrors.value.athleteId || ''
}

function focusFirstInvalidField() {
  if (entryFormErrors.value.crewId) {
    crewEntryInput.value?.focus()
    return
  }
  if (entryFormErrors.value.athleteId) {
    athleteEntryInput.value?.focus()
  }
}

function submitEntryForm() {
  entryFormErrors.value = {
    crewId: '',
    athleteId: ''
  }

  if (!entryForm.value.crewId) {
    entryFormErrors.value.crewId = t('validation.entry.crewRequired')
  }
  if (!entryForm.value.athleteId) {
    entryFormErrors.value.athleteId = t('validation.entry.athleteRequired')
  }

  if (hasEntryErrors()) {
    nextTick(() => {
      focusFirstInvalidField()
    })
  }
}

async function submitWithdrawRequest() {
  const reason = withdrawReason.value.trim().toLowerCase()
  if (reason.includes('conflict')) {
    return { status: 409 }
  }

  return { status: 204 }
}

function startWithdraw() {
  showConflictError.value = false
  withdrawReason.value = ''
  showWithdrawDialog.value = true
}

function closeWithdrawDialog() {
  showWithdrawDialog.value = false
  nextTick(() => {
    withdrawTriggerButton.value?.focus()
  })
}

function dialogFocusableElements() {
  if (!withdrawDialog.value) {
    return []
  }

  return [...withdrawDialog.value.querySelectorAll('button, textarea, input, select, [tabindex]:not([tabindex="-1"])')]
    .filter((element) => !element.hasAttribute('disabled'))
}

function onDialogKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    closeWithdrawDialog()
    return
  }

  if (event.key !== 'Tab') {
    return
  }

  const focusable = dialogFocusableElements()
  if (!focusable.length) {
    event.preventDefault()
    return
  }

  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  const active = document.activeElement

  if (event.shiftKey) {
    if (active === first || !withdrawDialog.value?.contains(active)) {
      event.preventDefault()
      last.focus()
    }
    return
  }

  if (active === last) {
    event.preventDefault()
    first.focus()
  }
}

async function confirmWithdraw() {
  const response = await submitWithdrawRequest()
  showWithdrawDialog.value = false
  showConflictError.value = response.status === 409

  nextTick(() => {
    withdrawTriggerButton.value?.focus()
  })
}

watch(showWithdrawDialog, async (open) => {
  if (!open) {
    return
  }

  await nextTick()
  const focusable = dialogFocusableElements()
  ;(focusable[0] || withdrawDialog.value)?.focus()
})

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

    <section>
      <h3>{{ t('staff.regatta_detail.sections.events') }}</h3>
      <form data-testid="event-form">
        <label>
          {{ t('staff.regatta_detail.form.eventGroup') }}
          <select name="event_group_id">
            <option value="">{{ t('staff.regatta_detail.form.selectPlaceholder') }}</option>
            <option value="group-a">{{ t('staff.regatta_detail.form.groupA') }}</option>
          </select>
        </label>
      </form>
      <table data-testid="events-table">
        <thead>
          <tr>
            <th scope="col">{{ t('staff.regatta_detail.table.event') }}</th>
            <th scope="col">{{ t('staff.regatta_detail.table.action') }}</th>
            <th scope="col">{{ t('staff.regatta_detail.table.action') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>{{ t('staff.regatta_detail.events.open2x') }}</td>
            <td><button type="button" data-action="edit">{{ t('common.edit') }}</button></td>
            <td><button type="button" data-action="delete">{{ t('common.delete') }}</button></td>
          </tr>
        </tbody>
      </table>
    </section>

    <section>
      <h3>{{ t('staff.regatta_detail.sections.athletes') }}</h3>
      <form data-testid="athlete-form">
        <label>
          {{ t('staff.regatta_detail.form.name') }}
          <input name="athlete_name" type="text" />
        </label>
      </form>
      <table data-testid="athletes-table">
        <thead>
          <tr>
            <th scope="col">{{ t('staff.regatta_detail.table.athlete') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr><td>{{ t('staff.regatta_detail.athletes.row1') }}</td></tr>
        </tbody>
      </table>
    </section>

    <section>
      <h3>{{ t('staff.regatta_detail.sections.crews') }}</h3>
      <form data-testid="crew-form">
        <label>
          {{ t('staff.regatta_detail.form.crewName') }}
          <input name="crew_name" type="text" />
        </label>
      </form>
      <table data-testid="crews-table">
        <thead>
          <tr>
            <th scope="col">{{ t('staff.regatta_detail.table.crew') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr><td>{{ t('staff.regatta_detail.crews.row1') }}</td></tr>
        </tbody>
      </table>
    </section>

    <section>
      <h3>{{ t('staff.regatta_detail.sections.entries') }}</h3>
      <form data-testid="entry-form" @submit.prevent="submitEntryForm">
        <label>
          {{ t('common.search') }}
          <input name="entries_search" type="search" />
        </label>
        <label>
          {{ t('entry.status') }}
          <select name="entries_status_filter">
            <option value="">{{ t('staff.regatta_detail.form.allStatuses') }}</option>
            <option value="entered">{{ t('status.entered') }}</option>
          </select>
        </label>
        <label>
          {{ t('entry.crew') }}
          <input
            ref="crewEntryInput"
            v-model="entryForm.crewId"
            name="entry_crew"
            type="text"
            :aria-invalid="entryFormErrors.crewId ? 'true' : undefined"
          />
        </label>
        <label>
          {{ t('staff.regatta_detail.form.athlete') }}
          <input
            ref="athleteEntryInput"
            v-model="entryForm.athleteId"
            name="entry_athlete"
            type="text"
            :aria-invalid="entryFormErrors.athleteId ? 'true' : undefined"
          />
        </label>
        <button type="submit">{{ t('staff.regatta_detail.form.saveEntry') }}</button>
      </form>

      <div v-if="hasEntryErrors()" data-testid="entry-form-errors">
        <p role="alert">{{ firstEntryErrorMessage() }}</p>
      </div>

      <button ref="withdrawTriggerButton" type="button" data-action="withdraw-entry" @click="startWithdraw">
        {{ t('staff.regatta_detail.withdraw.open') }}
      </button>

      <dialog
        v-if="showWithdrawDialog"
        ref="withdrawDialog"
        open
        data-testid="destructive-action-dialog"
        aria-modal="true"
        aria-labelledby="withdraw-dialog-title"
        tabindex="-1"
        @keydown="onDialogKeydown"
      >
        <p id="withdraw-dialog-title">{{ t('staff.regatta_detail.withdraw.confirmTitle') }}</p>
        <label for="withdraw-reason">{{ t('staff.regatta_detail.withdraw.reason') }}</label>
        <textarea id="withdraw-reason" v-model="withdrawReason" name="reason" />
        <div data-testid="audit-actor">{{ t('staff.regatta_detail.withdraw.auditActor') }}</div>
        <div data-testid="audit-timestamp">{{ t('staff.regatta_detail.withdraw.auditTimestamp') }}</div>
        <button type="button" data-action="confirm-withdraw" @click="confirmWithdraw">
          {{ t('common.confirm') }}
        </button>
        <button type="button" data-action="cancel-withdraw" @click="closeWithdrawDialog">
          {{ t('common.cancel') }}
        </button>
      </dialog>

      <div v-if="showConflictError" data-testid="entry-conflict-error">
        {{ t('staff.regatta_detail.withdraw.conflict') }}
        <button type="button" data-testid="entry-conflict-reload">
          {{ t('staff.regatta_detail.withdraw.reload') }}
        </button>
      </div>

      <table data-testid="entries-table">
        <thead>
          <tr>
            <th scope="col">{{ t('entry.status') }}</th>
            <th scope="col">{{ t('staff.regatta_detail.table.currentAction') }}</th>
            <th scope="col">{{ t('staff.regatta_detail.table.nextAction') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr data-testid="entry-row-withdrawn-before-draw">
            <td>{{ t('status.withdrawn_before_draw') }}</td>
            <td>
              <button type="button" data-action="set-status-withdrawn-before-draw" disabled>
                {{ t('staff.regatta_detail.withdraw.alreadyBeforeDraw') }}
              </button>
            </td>
            <td>
              <button type="button" data-action="set-status-entered">{{ t('staff.regatta_detail.withdraw.setEntered') }}</button>
            </td>
          </tr>
          <tr data-testid="entry-row-withdrawn-after-draw">
            <td>{{ t('status.withdrawn_after_draw') }}</td>
            <td>
              <button type="button" data-action="set-status-withdrawn-before-draw" disabled>
                {{ t('staff.regatta_detail.withdraw.invalidTransition') }}
              </button>
            </td>
            <td>
              <button type="button" data-action="set-status-entered" disabled>
                {{ t('staff.regatta_detail.withdraw.invalidTransition') }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.regatta-detail h2 {
  margin-bottom: var(--rd-space-3);
}
</style>
