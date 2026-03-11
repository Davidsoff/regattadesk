<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError, createApiClient, createRegattaSetupApi } from '../../api'

const route = useRoute()
const api = createRegattaSetupApi(createApiClient())

const withdrawDialog = ref(null)
const withdrawTriggerButton = ref(null)
const items = ref([])
const loading = ref(false)
const pageError = ref('')
const saving = ref(false)
const showWithdrawDialog = ref(false)
const withdrawReason = ref('')
const withdrawStatus = ref('withdrawn_before_draw')
const selectedEntry = ref(null)
const conflictMessage = ref('')
const formErrors = ref({})
const filters = ref({
  search: '',
  status: ''
})

const sectionConfigs = {
  'staff-regatta-setup-event-groups': {
    title: 'Event Groups',
    description: 'Create and review event groups for this regatta.',
    formFields: [
      { key: 'name', label: 'Name', required: true },
      { key: 'description', label: 'Description' },
      { key: 'display_order', label: 'Display Order', type: 'number' }
    ],
    create(payload) {
      return api.createEventGroup(route.params.regattaId, {
        name: payload.name,
        description: payload.description || null,
        display_order: Number(payload.display_order || 0)
      })
    },
    list() {
      return api.listEventGroups(route.params.regattaId, { search: filters.value.search || undefined })
    }
  },
  'staff-regatta-setup-events': {
    title: 'Events',
    description: 'Manage regatta-scoped events and their group assignment.',
    formFields: [
      { key: 'event_group_id', label: 'Event Group ID', required: true },
      { key: 'category_id', label: 'Category ID', required: true },
      { key: 'boat_type_id', label: 'Boat Type ID', required: true },
      { key: 'name', label: 'Name', required: true },
      { key: 'display_order', label: 'Display Order', type: 'number' }
    ],
    create(payload) {
      return api.createEvent(route.params.regattaId, {
        event_group_id: payload.event_group_id,
        category_id: payload.category_id,
        boat_type_id: payload.boat_type_id,
        name: payload.name,
        display_order: Number(payload.display_order || 0)
      })
    },
    list() {
      return api.listEvents(route.params.regattaId)
    }
  },
  'staff-regatta-setup-athletes': {
    title: 'Athletes',
    description: 'Search and create athletes using the shared BC03 athlete API.',
    formFields: [
      { key: 'first_name', label: 'First Name', required: true },
      { key: 'last_name', label: 'Last Name', required: true },
      { key: 'date_of_birth', label: 'Date of Birth', type: 'date', required: true },
      { key: 'gender', label: 'Gender', required: true }
    ],
    create(payload) {
      return api.createAthlete({
        first_name: payload.first_name,
        last_name: payload.last_name,
        date_of_birth: payload.date_of_birth,
        gender: payload.gender
      })
    },
    list() {
      return api.listAthletes({ search: filters.value.search || undefined })
    }
  },
  'staff-regatta-setup-crews': {
    title: 'Crews',
    description: 'Create crews and seed member assignments for regatta entries.',
    formFields: [
      { key: 'display_name', label: 'Display Name', required: true },
      { key: 'club_id', label: 'Club ID' },
      { key: 'athlete_id', label: 'Athlete ID', required: true },
      { key: 'seat_position', label: 'Seat Position', type: 'number', required: true }
    ],
    create(payload) {
      return api.createCrew(route.params.regattaId, {
        display_name: payload.display_name,
        club_id: payload.club_id || null,
        is_composite: false,
        members: [
          {
            athlete_id: payload.athlete_id,
            seat_position: Number(payload.seat_position)
          }
        ]
      })
    },
    list() {
      return api.listCrews(route.params.regattaId)
    }
  },
  'staff-regatta-setup-entries': {
    title: 'Entries',
    description: 'Create entries, filter by status, and handle withdraw and reinstate workflows.',
    formFields: [
      { key: 'event_id', label: 'Event ID', required: true, focusName: 'event_id' },
      { key: 'block_id', label: 'Block ID', required: true },
      { key: 'crew_id', label: 'Crew ID', required: true, focusName: 'crew_id' },
      { key: 'billing_club_id', label: 'Billing Club ID' }
    ],
    create(payload) {
      return api.createEntry(route.params.regattaId, {
        event_id: payload.event_id,
        block_id: payload.block_id,
        crew_id: payload.crew_id,
        billing_club_id: payload.billing_club_id || null
      })
    },
    list() {
      return api.listEntries(route.params.regattaId, { status: filters.value.status || undefined })
    }
  }
}

const currentSection = computed(() => sectionConfigs[route.name] || sectionConfigs['staff-regatta-setup-event-groups'])
const formState = ref({})

function resetForm() {
  const next = {}
  for (const field of currentSection.value.formFields) {
    next[field.key] = ''
  }
  formState.value = next
  formErrors.value = {}
}

function displayValue(item, field) {
  const value = item[field.key]
  if (Array.isArray(value)) {
    return value.map((member) => `${member.athlete_id} (#${member.seat_position})`).join(', ')
  }
  return value ?? ''
}

const displayedItems = computed(() => {
  if (route.name !== 'staff-regatta-setup-entries') {
    return items.value
  }

  const search = filters.value.search.trim().toLowerCase()
  if (!search) {
    return items.value
  }

  return items.value.filter((item) => JSON.stringify(item).toLowerCase().includes(search))
})

async function loadItems() {
  loading.value = true
  pageError.value = ''

  try {
    const response = await currentSection.value.list()
    items.value = Array.isArray(response?.data) ? response.data : response || []
  } catch (error) {
    pageError.value = error.message || 'Failed to load setup section.'
  } finally {
    loading.value = false
  }
}

function validateForm() {
  const nextErrors = {}
  for (const field of currentSection.value.formFields) {
    if (field.required && !String(formState.value[field.key] || '').trim()) {
      nextErrors[field.key] = `${field.label} is required`
    }
  }
  formErrors.value = nextErrors

  const firstInvalid = currentSection.value.formFields.find((field) => nextErrors[field.key])
  if (firstInvalid?.focusName) {
    nextTick(() => {
      document.querySelector(`input[name="${firstInvalid.focusName}"]`)?.focus()
    })
  }

  return Object.keys(nextErrors).length === 0
}

async function submitForm() {
  if (!validateForm()) {
    return
  }

  saving.value = true
  pageError.value = ''

  try {
    await currentSection.value.create(formState.value)
    resetForm()
    await loadItems()
  } catch (error) {
    pageError.value = error.message || 'Failed to save changes.'
  } finally {
    saving.value = false
  }
}

function startWithdraw(entry, event) {
  selectedEntry.value = entry
  showWithdrawDialog.value = true
  withdrawReason.value = ''
  withdrawStatus.value = 'withdrawn_before_draw'
  conflictMessage.value = ''
  withdrawTriggerButton.value = event?.currentTarget ?? null
}

function closeWithdrawDialog() {
  showWithdrawDialog.value = false
  withdrawStatus.value = 'withdrawn_before_draw'
  nextTick(() => {
    withdrawTriggerButton.value?.focus?.()
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
  if (!selectedEntry.value) {
    return
  }

  try {
    await api.withdrawEntry(route.params.regattaId, selectedEntry.value.id, {
      status: withdrawStatus.value,
      reason: withdrawReason.value,
      expected_status: selectedEntry.value.status
    })
    closeWithdrawDialog()
    await loadItems()
  } catch (error) {
    const message = error instanceof ApiError ? error.message : error?.message
    conflictMessage.value = message || 'Conflict'
    showWithdrawDialog.value = false
    nextTick(() => {
      withdrawTriggerButton.value?.focus?.()
    })
  }
}

async function reinstateEntry(entry) {
  try {
    await api.reinstateEntry(route.params.regattaId, entry.id, {
      expected_status: entry.status
    })
    conflictMessage.value = ''
    await loadItems()
  } catch (error) {
    conflictMessage.value = error.message || 'Conflict'
  }
}

watch(
  () => route.name,
  async () => {
    resetForm()
    filters.value.search = ''
    filters.value.status = ''
    await loadItems()
  }
)

watch(
  () => showWithdrawDialog.value,
  async (open) => {
    if (!open) {
      return
    }

    await nextTick()
    const [first] = dialogFocusableElements()
    first?.focus()
  }
)

onMounted(async () => {
  resetForm()
  await loadItems()
})
</script>

<template>
  <section class="regatta-setup-section" data-testid="regatta-setup-section">
    <header class="regatta-setup-section__header">
      <div>
        <p class="regatta-setup-section__eyebrow">Regatta Setup</p>
        <h2>{{ currentSection.title }}</h2>
        <p>{{ currentSection.description }}</p>
      </div>
      <nav aria-label="Setup sections" class="regatta-setup-section__nav">
        <RouterLink :to="{ name: 'staff-regatta-setup-event-groups', params: route.params }">Event groups</RouterLink>
        <RouterLink :to="{ name: 'staff-regatta-setup-events', params: route.params }">Events</RouterLink>
        <RouterLink :to="{ name: 'staff-regatta-setup-athletes', params: route.params }">Athletes</RouterLink>
        <RouterLink :to="{ name: 'staff-regatta-setup-crews', params: route.params }">Crews</RouterLink>
        <RouterLink :to="{ name: 'staff-regatta-setup-entries', params: route.params }">Entries</RouterLink>
      </nav>
    </header>

    <div class="regatta-setup-section__controls">
      <label>
        Search
        <input v-model="filters.search" name="setup_search" type="search" />
      </label>
      <label v-if="route.name === 'staff-regatta-setup-entries'">
        Status
        <select v-model="filters.status" name="entries_status_filter" @change="loadItems">
          <option value="">All statuses</option>
          <option value="entered">Entered</option>
          <option value="withdrawn_before_draw">Withdrawn Before Draw</option>
          <option value="withdrawn_after_draw">Withdrawn After Draw</option>
        </select>
      </label>
    </div>

    <form class="regatta-setup-section__form" data-testid="setup-form" @submit.prevent="submitForm">
      <label v-for="field in currentSection.formFields" :key="field.key">
        {{ field.label }}
        <input
          v-model="formState[field.key]"
          :name="field.key"
          :type="field.type || 'text'"
          :aria-invalid="formErrors[field.key] ? 'true' : undefined"
        />
      </label>
      <button type="submit" :disabled="saving">Save</button>
    </form>

    <div v-if="Object.keys(formErrors).length" data-testid="entry-form-errors">
      <p role="alert">{{ Object.values(formErrors)[0] }}</p>
    </div>

    <div v-if="pageError" class="regatta-setup-section__error" role="alert">
      {{ pageError }}
    </div>
    <div v-if="conflictMessage" data-testid="entry-conflict-error" class="regatta-setup-section__error" role="alert">
      {{ conflictMessage }}
      <button type="button" data-testid="entry-conflict-reload" @click="loadItems">Reload</button>
    </div>

    <div v-if="loading">Loading…</div>
    <table v-else class="regatta-setup-section__table" data-testid="setup-table">
      <thead>
        <tr>
          <th v-for="field in currentSection.formFields" :key="`head-${field.key}`" scope="col">
            {{ field.label }}
          </th>
          <th v-if="route.name === 'staff-regatta-setup-entries'" scope="col">Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="item in displayedItems"
          :key="item.id"
          :data-testid="route.name === 'staff-regatta-setup-entries' ? `entry-row-${item.status.replaceAll('_', '-')}` : undefined"
        >
          <td v-for="field in currentSection.formFields" :key="`${item.id}-${field.key}`">{{ displayValue(item, field) }}</td>
          <td v-if="route.name === 'staff-regatta-setup-entries'">
            <button
              v-if="item.status === 'entered'"
              type="button"
              data-action="withdraw-entry"
              @click="startWithdraw(item, $event)"
            >
              Withdraw
            </button>
            <button
              v-else-if="item.status === 'withdrawn_before_draw' || item.status === 'withdrawn_after_draw'"
              type="button"
              data-action="set-status-entered"
              @click="reinstateEntry(item)"
            >
              Reinstate
            </button>
            <button
              v-else
              type="button"
              data-action="set-status-entered"
              disabled
            >
              Invalid transition
            </button>
          </td>
        </tr>
      </tbody>
    </table>

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
      <p id="withdraw-dialog-title">Withdraw entry</p>
      <label for="withdraw-status">Withdrawal timing</label>
      <select id="withdraw-status" v-model="withdrawStatus" name="status">
        <option value="withdrawn_before_draw">Before draw publication</option>
        <option value="withdrawn_after_draw">After draw publication</option>
      </select>
      <label for="withdraw-reason">Reason</label>
      <textarea id="withdraw-reason" v-model="withdrawReason" name="reason" />
      <div data-testid="audit-actor">Actor recorded from staff identity</div>
      <div data-testid="audit-timestamp">Timestamp recorded on submit</div>
      <button type="button" data-action="confirm-withdraw" @click="confirmWithdraw">Confirm</button>
      <button type="button" data-action="cancel-withdraw" @click="closeWithdrawDialog">Cancel</button>
    </dialog>
  </section>
</template>

<style scoped>
.regatta-setup-section {
  display: grid;
  gap: var(--rd-space-4);
}

.regatta-setup-section__header {
  display: grid;
  gap: var(--rd-space-3);
}

.regatta-setup-section__eyebrow {
  margin: 0;
  font-size: 0.75rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.regatta-setup-section__nav {
  display: flex;
  flex-wrap: wrap;
  gap: var(--rd-space-2);
}

.regatta-setup-section__controls,
.regatta-setup-section__form {
  display: grid;
  gap: var(--rd-space-3);
}

.regatta-setup-section__error {
  color: var(--rd-color-danger-700, #8a1c1c);
}

.regatta-setup-section__table {
  width: 100%;
}
@media (min-width: 768px) {
  .regatta-setup-section__form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
