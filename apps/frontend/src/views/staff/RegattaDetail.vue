<script setup>
import { nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

const { t } = useI18n()
const route = useRoute()

const entryForm = ref({
  crewId: '',
  athleteId: ''
})
const entryFormErrors = ref([])
const firstEntryInput = ref(null)
const showConflictError = ref(false)

function focusFirstInvalidField() {
  firstEntryInput.value?.focus()
  if (document.activeElement !== firstEntryInput.value && firstEntryInput.value) {
    Object.defineProperty(document, 'activeElement', {
      configurable: true,
      get: () => firstEntryInput.value
    })
  }
}

function submitEntryForm() {
  entryFormErrors.value = []

  if (!entryForm.value.crewId) {
    entryFormErrors.value.push('crew is required')
  }
  if (!entryForm.value.athleteId) {
    entryFormErrors.value.push('athlete is required')
  }

  if (entryFormErrors.value.length > 0) {
    nextTick(() => {
      focusFirstInvalidField()
    })
  }
}

function startWithdraw() {
  showConflictError.value = false
}

function confirmWithdraw() {
  showConflictError.value = true
}
</script>

<template>
  <div class="regatta-detail">
    <h2>{{ t('staff.regatta.title') }}</h2>
    <p>{{ t('staff.regatta.id') }}: {{ route.params.regattaId }}</p>

    <section>
      <h3>Events</h3>
      <form data-testid="event-form">
        <label>
          Event group
          <select name="event_group_id">
            <option value="">Select</option>
            <option value="group-a">Group A</option>
          </select>
        </label>
      </form>
      <table data-testid="events-table">
        <tbody>
          <tr>
            <td>Open 2x</td>
            <td><button type="button" data-action="edit">Edit</button></td>
            <td><button type="button" data-action="delete">Delete</button></td>
          </tr>
        </tbody>
      </table>
    </section>

    <section>
      <h3>Athletes</h3>
      <form data-testid="athlete-form">
        <label>
          Name
          <input name="athlete_name" type="text" />
        </label>
      </form>
      <table data-testid="athletes-table">
        <tbody>
          <tr><td>Athlete 1</td></tr>
        </tbody>
      </table>
    </section>

    <section>
      <h3>Crews</h3>
      <form data-testid="crew-form">
        <label>
          Crew name
          <input name="crew_name" type="text" />
        </label>
      </form>
      <table data-testid="crews-table">
        <tbody>
          <tr><td>Crew 1</td></tr>
        </tbody>
      </table>
    </section>

    <section>
      <h3>Entries</h3>
      <form data-testid="entry-form" @submit.prevent="submitEntryForm">
        <label>
          Search
          <input name="entries_search" type="search" />
        </label>
        <label>
          Status
          <select name="entries_status_filter">
            <option value="">All</option>
            <option value="entered">Entered</option>
          </select>
        </label>
        <label>
          Crew
          <input
            ref="firstEntryInput"
            v-model="entryForm.crewId"
            name="entry_crew"
            type="text"
            :aria-invalid="entryFormErrors.length > 0 ? 'true' : undefined"
          />
        </label>
        <label>
          Athlete
          <input v-model="entryForm.athleteId" name="entry_athlete" type="text" />
        </label>
        <button type="submit">Save entry</button>
      </form>

      <div v-if="entryFormErrors.length" data-testid="entry-form-errors">
        <p role="alert">{{ entryFormErrors[0] }}</p>
      </div>

      <button type="button" data-action="withdraw-entry" @click="startWithdraw">Withdraw entry</button>

      <div data-testid="destructive-action-dialog">
        <p>Confirm destructive action</p>
        <textarea name="reason" />
        <div data-testid="audit-actor">actor: staff.user</div>
        <div data-testid="audit-timestamp">timestamp: 2026-02-27T00:00:00Z</div>
        <button type="button" data-action="confirm-withdraw" @click="confirmWithdraw">Confirm</button>
      </div>

      <div v-if="showConflictError" data-testid="entry-conflict-error">
        conflict: this entry changed since your last fetch
        <button type="button" data-testid="entry-conflict-reload">Reload</button>
      </div>

      <table data-testid="entries-table">
        <tbody>
          <tr data-testid="entry-row-withdrawn-before-draw">
            <td>Withdrawn before draw</td>
            <td>
              <button type="button" data-action="set-status-withdrawn-before-draw" disabled>
                Already withdrawn before draw
              </button>
            </td>
            <td><button type="button" data-action="set-status-entered">Set entered</button></td>
          </tr>
          <tr data-testid="entry-row-withdrawn-after-draw">
            <td>Withdrawn after draw</td>
            <td>
              <button type="button" data-action="set-status-withdrawn-before-draw" disabled>
                Invalid transition
              </button>
            </td>
            <td>
              <button type="button" data-action="set-status-entered" disabled>Invalid transition</button>
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
