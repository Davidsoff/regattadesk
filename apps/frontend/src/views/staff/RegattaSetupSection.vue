<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ApiError, createApiClient, createRegattaSetupApi } from '../../api'

const route = useRoute()
const { t } = useI18n()
const api = createRegattaSetupApi(createApiClient())

const destructiveDialog = ref(null)
const destructiveTriggerButton = ref(null)
const items = ref([])
const loading = ref(false)
const pageError = ref('')
const saving = ref(false)
const showDestructiveDialog = ref(false)
const destructiveMode = ref('withdraw')
const withdrawReason = ref('')
const withdrawStatus = ref('withdrawn_before_draw')
const selectedItem = ref(null)
const editingItem = ref(null)
const conflictMessage = ref('')
const formErrors = ref({})
const filters = ref({
  search: '',
  status: ''
})

function normalizeNullable(value) {
  const normalized = String(value ?? '').trim()
  return normalized ? normalized : null
}

function toInteger(value) {
  return Number(value || 0)
}

function buildEventGroupPayload(payload) {
  return {
    name: payload.name,
    description: normalizeNullable(payload.description),
    display_order: toInteger(payload.display_order)
  }
}

function buildEventPayload(payload) {
  return {
    event_group_id: payload.event_group_id,
    category_id: payload.category_id,
    boat_type_id: payload.boat_type_id,
    name: payload.name,
    display_order: toInteger(payload.display_order)
  }
}

function buildAthletePayload(payload) {
  return {
    first_name: payload.first_name,
    middle_name: normalizeNullable(payload.middle_name),
    last_name: payload.last_name,
    date_of_birth: payload.date_of_birth,
    gender: payload.gender,
    club_id: normalizeNullable(payload.club_id)
  }
}

function buildCrewPayload(payload) {
  return {
    display_name: payload.display_name,
    club_id: normalizeNullable(payload.club_id),
    is_composite: false,
    members: [
      {
        athlete_id: payload.athlete_id,
        seat_position: Number(payload.seat_position)
      }
    ]
  }
}

function buildEntryPayload(payload) {
  return {
    event_id: payload.event_id,
    block_id: payload.block_id,
    crew_id: payload.crew_id,
    billing_club_id: normalizeNullable(payload.billing_club_id)
  }
}

const sectionConfigs = {
  'staff-regatta-setup-event-groups': {
    title: 'Event Groups',
    singular: 'event group',
    description: 'Create, search, update, and delete event groups for this regatta.',
    formFields: [
      { key: 'name', label: 'Name', required: true },
      { key: 'description', label: 'Description' },
      { key: 'display_order', label: 'Display Order', type: 'number' }
    ],
    create(payload) {
      return api.createEventGroup(route.params.regattaId, buildEventGroupPayload(payload))
    },
    update(item, payload) {
      return api.updateEventGroup(route.params.regattaId, item.id, buildEventGroupPayload(payload))
    },
    delete(item) {
      return api.deleteEventGroup(route.params.regattaId, item.id)
    },
    list() {
      return api.listEventGroups(route.params.regattaId, { search: filters.value.search || undefined })
    },
    label(item) {
      return item.name || item.id
    }
  },
  'staff-regatta-setup-events': {
    title: 'Events',
    singular: 'event',
    description: 'Manage regatta-scoped events, with real search and edit/delete support.',
    formFields: [
      { key: 'event_group_id', label: 'Event Group ID', required: true },
      { key: 'category_id', label: 'Category ID', required: true },
      { key: 'boat_type_id', label: 'Boat Type ID', required: true },
      { key: 'name', label: 'Name', required: true },
      { key: 'display_order', label: 'Display Order', type: 'number' }
    ],
    create(payload) {
      return api.createEvent(route.params.regattaId, buildEventPayload(payload))
    },
    update(item, payload) {
      return api.updateEvent(route.params.regattaId, item.id, buildEventPayload(payload))
    },
    delete(item) {
      return api.deleteEvent(route.params.regattaId, item.id)
    },
    list() {
      return api.listEvents(route.params.regattaId, { search: filters.value.search || undefined })
    },
    label(item) {
      return item.name || item.id
    }
  },
  'staff-regatta-setup-athletes': {
    title: 'Athletes',
    singular: 'athlete',
    description: 'Search, create, update, and delete athletes using the shared BC03 athlete API.',
    formFields: [
      { key: 'first_name', label: 'First Name', required: true },
      { key: 'middle_name', label: 'Middle Name' },
      { key: 'last_name', label: 'Last Name', required: true },
      { key: 'date_of_birth', label: 'Date of Birth', type: 'date', required: true },
      { key: 'gender', label: 'Gender', required: true },
      { key: 'club_id', label: 'Club ID' }
    ],
    create(payload) {
      return api.createAthlete(buildAthletePayload(payload))
    },
    update(item, payload) {
      return api.updateAthlete(item.id, buildAthletePayload(payload))
    },
    delete(item) {
      return api.deleteAthlete(item.id)
    },
    list() {
      return api.listAthletes({ search: filters.value.search || undefined })
    },
    label(item) {
      return `${item.first_name || ''} ${item.last_name || ''}`.trim() || item.id
    }
  },
  'staff-regatta-setup-crews': {
    title: 'Crews',
    singular: 'crew',
    description: 'Create crews, search them, and maintain their setup metadata before entry handling.',
    formFields: [
      { key: 'display_name', label: 'Display Name', required: true },
      { key: 'club_id', label: 'Club ID' },
      { key: 'athlete_id', label: 'Athlete ID', required: true },
      { key: 'seat_position', label: 'Seat Position', type: 'number', required: true }
    ],
    create(payload) {
      return api.createCrew(route.params.regattaId, buildCrewPayload(payload))
    },
    update(item, payload) {
      return api.updateCrew(route.params.regattaId, item.id, buildCrewPayload(payload))
    },
    delete(item) {
      return api.deleteCrew(route.params.regattaId, item.id)
    },
    list() {
      return api.listCrews(route.params.regattaId, { search: filters.value.search || undefined })
    },
    label(item) {
      return item.display_name || item.id
    }
  },
  'staff-regatta-setup-entries': {
    title: 'Entries',
    singular: 'entry',
    description: 'Create, search, filter, edit, delete, withdraw, and reinstate regatta entries.',
    formFields: [
      { key: 'event_id', label: 'Event ID', required: true, focusName: 'event_id' },
      { key: 'block_id', label: 'Block ID', required: true },
      { key: 'crew_id', label: 'Crew ID', required: true, focusName: 'crew_id' },
      { key: 'billing_club_id', label: 'Billing Club ID' }
    ],
    create(payload) {
      return api.createEntry(route.params.regattaId, buildEntryPayload(payload))
    },
    update(item, payload) {
      return api.updateEntry(route.params.regattaId, item.id, buildEntryPayload(payload))
    },
    delete(item) {
      return api.deleteEntry(route.params.regattaId, item.id)
    },
    list() {
      return api.listEntries(route.params.regattaId, {
        search: filters.value.search || undefined,
        status: filters.value.status || undefined
      })
    },
    label(item) {
      return item.id
    }
  }
}

const currentSection = computed(() => sectionConfigs[route.name] || sectionConfigs['staff-regatta-setup-event-groups'])
const formState = ref({})
const displayedItems = computed(() => items.value)
const isEditing = computed(() => editingItem.value !== null)
const submitButtonLabel = computed(() => (isEditing.value ? 'Update' : 'Save'))
const destructiveDialogTitle = computed(() => {
  if (destructiveMode.value === 'delete') {
    return `Delete ${currentSection.value.singular}`
  }
  return 'Withdraw entry'
})
const destructiveDialogLabelId = computed(() => (destructiveMode.value === 'withdraw' ? 'withdraw-dialog-title' : 'destructive-dialog-title'))
const destructiveDialogDescription = computed(() => {
  if (!selectedItem.value) {
    return ''
  }
  if (destructiveMode.value === 'delete') {
    return `You are deleting ${currentSection.value.label(selectedItem.value)}.`
  }
  return `You are withdrawing entry ${selectedItem.value.id}.`
})

function resetForm() {
  const next = {}
  for (const field of currentSection.value.formFields) {
    next[field.key] = ''
  }
  formState.value = next
  editingItem.value = null
  formErrors.value = {}
}

function populateForm(item) {
  const next = {}
  for (const field of currentSection.value.formFields) {
    if (route.name === 'staff-regatta-setup-crews' && field.key === 'athlete_id') {
      next[field.key] = item.members?.[0]?.athlete_id ?? ''
      continue
    }
    if (route.name === 'staff-regatta-setup-crews' && field.key === 'seat_position') {
      next[field.key] = item.members?.[0]?.seat_position ?? ''
      continue
    }
    next[field.key] = item[field.key] ?? ''
  }
  formState.value = next
  editingItem.value = item
  formErrors.value = {}
  pageError.value = ''
  conflictMessage.value = ''
}

function displayValue(item, field) {
  const value = item[field.key]
  if (Array.isArray(value)) {
    return value.map((member) => `${member.athlete_id} (#${member.seat_position})`).join(', ')
  }
  return value ?? ''
}

function extractErrorMessage(error, fallback) {
  if (error instanceof ApiError) {
    return error.message || fallback
  }
  return error?.message || fallback
}

function applyMutationError(error, fallback) {
  const message = extractErrorMessage(error, fallback)
  if (message.toLowerCase().includes('conflict')) {
    conflictMessage.value = message
    return
  }
  pageError.value = message
}

async function loadItems() {
  loading.value = true
  pageError.value = ''

  try {
    const response = await currentSection.value.list()
    items.value = Array.isArray(response?.data) ? response.data : response || []
  } catch (error) {
    pageError.value = extractErrorMessage(error, 'Failed to load setup section.')
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
  conflictMessage.value = ''

  try {
    if (editingItem.value) {
      await currentSection.value.update(editingItem.value, formState.value)
    } else {
      await currentSection.value.create(formState.value)
    }
    resetForm()
    await loadItems()
  } catch (error) {
    applyMutationError(error, 'Failed to save changes.')
  } finally {
    saving.value = false
  }
}

function startWithdraw(entry, event) {
  selectedItem.value = entry
  destructiveMode.value = 'withdraw'
  showDestructiveDialog.value = true
  withdrawReason.value = ''
  withdrawStatus.value = 'withdrawn_before_draw'
  conflictMessage.value = ''
  destructiveTriggerButton.value = event?.currentTarget ?? null
}

function startDelete(item, event) {
  selectedItem.value = item
  destructiveMode.value = 'delete'
  showDestructiveDialog.value = true
  conflictMessage.value = ''
  destructiveTriggerButton.value = event?.currentTarget ?? null
}

function closeDestructiveDialog() {
  showDestructiveDialog.value = false
  withdrawStatus.value = 'withdrawn_before_draw'
  withdrawReason.value = ''
  selectedItem.value = null
  nextTick(() => {
    destructiveTriggerButton.value?.focus?.()
  })
}

function dialogFocusableElements() {
  if (!destructiveDialog.value) {
    return []
  }

  return [...destructiveDialog.value.querySelectorAll('button, textarea, input, select, [tabindex]:not([tabindex="-1"])')]
    .filter((element) => !element.hasAttribute('disabled'))
}

function onDialogKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    closeDestructiveDialog()
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
    if (active === first || !destructiveDialog.value?.contains(active)) {
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
  if (!selectedItem.value) {
    return
  }

  try {
    await api.withdrawEntry(route.params.regattaId, selectedItem.value.id, {
      status: withdrawStatus.value,
      reason: withdrawReason.value,
      expected_status: selectedItem.value.status
    })
    closeDestructiveDialog()
    await loadItems()
  } catch (error) {
    conflictMessage.value = extractErrorMessage(error, 'Conflict')
    showDestructiveDialog.value = false
    nextTick(() => {
      destructiveTriggerButton.value?.focus?.()
    })
  }
}

async function confirmDelete() {
  if (!selectedItem.value) {
    return
  }

  try {
    await currentSection.value.delete(selectedItem.value)
    if (editingItem.value?.id === selectedItem.value.id) {
      resetForm()
    }
    closeDestructiveDialog()
    await loadItems()
  } catch (error) {
    conflictMessage.value = extractErrorMessage(error, 'Failed to delete item.')
    showDestructiveDialog.value = false
    nextTick(() => {
      destructiveTriggerButton.value?.focus?.()
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
    conflictMessage.value = extractErrorMessage(error, 'Conflict')
  }
}

watch(
  () => route.name,
  async () => {
    resetForm()
    filters.value.search = ''
    filters.value.status = ''
    conflictMessage.value = ''
    await loadItems()
  },
  { immediate: true }
)

watch(
  () => filters.value.search,
  async (current, previous) => {
    if (current === previous) {
      return
    }
    await loadItems()
  }
)

watch(
  () => filters.value.status,
  async (current, previous) => {
    if (current === previous) {
      return
    }
    await loadItems()
  }
)

watch(
  () => showDestructiveDialog.value,
  async (open) => {
    if (!open) {
      return
    }

    await nextTick()
    const [first] = dialogFocusableElements()
    first?.focus()
  }
)
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
        {{ t('common.search') }}
        <input v-model="filters.search" name="setup_search" type="search" />
      </label>
      <label v-if="route.name === 'staff-regatta-setup-entries'">
        Status
        <select v-model="filters.status" name="entries_status_filter" @change="loadItems">
          <option value="">All statuses</option>
          <option value="entered">{{ t('status.entered') }}</option>
          <option value="withdrawn_before_draw">{{ t('status.withdrawn_before_draw') }}</option>
          <option value="withdrawn_after_draw">{{ t('status.withdrawn_after_draw') }}</option>
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
      <button type="submit" :disabled="saving">{{ submitButtonLabel }}</button>
      <button v-if="isEditing" type="button" data-action="cancel-edit" @click="resetForm">Cancel</button>
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
          <th scope="col">Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="item in displayedItems"
          :key="item.id"
          :data-testid="route.name === 'staff-regatta-setup-entries' ? `entry-row-${item.status.replaceAll('_', '-')}` : undefined"
        >
          <td v-for="field in currentSection.formFields" :key="`${item.id}-${field.key}`">{{ displayValue(item, field) }}</td>
          <td>
            <button type="button" data-action="edit-item" @click="populateForm(item)">Edit</button>
            <button type="button" data-action="delete-item" @click="startDelete(item, $event)">Delete</button>
            <button
              v-if="route.name === 'staff-regatta-setup-entries' && item.status === 'entered'"
              type="button"
              data-action="withdraw-entry"
              @click="startWithdraw(item, $event)"
            >
              Withdraw
            </button>
            <button
              v-else-if="route.name === 'staff-regatta-setup-entries' && (item.status === 'withdrawn_before_draw' || item.status === 'withdrawn_after_draw')"
              type="button"
              data-action="set-status-entered"
              @click="reinstateEntry(item)"
            >
              Reinstate
            </button>
            <button
              v-else-if="route.name === 'staff-regatta-setup-entries'"
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
      v-if="showDestructiveDialog"
      ref="destructiveDialog"
      open
      data-testid="destructive-action-dialog"
      aria-modal="true"
      :aria-labelledby="destructiveDialogLabelId"
      tabindex="-1"
      @keydown="onDialogKeydown"
    >
      <p :id="destructiveDialogLabelId">{{ destructiveDialogTitle }}</p>
      <p>{{ destructiveDialogDescription }}</p>
      <template v-if="destructiveMode === 'withdraw'">
        <label for="withdraw-status">Withdrawal timing</label>
        <select id="withdraw-status" v-model="withdrawStatus" name="status">
          <option value="withdrawn_before_draw">{{ t('status.withdrawn_before_draw') }}</option>
          <option value="withdrawn_after_draw">{{ t('status.withdrawn_after_draw') }}</option>
        </select>
        <label for="withdraw-reason">Reason</label>
        <textarea id="withdraw-reason" v-model="withdrawReason" name="reason" />
      </template>
      <div data-testid="audit-actor">Actor recorded from staff identity</div>
      <div data-testid="audit-timestamp">Timestamp recorded on submit</div>
      <button
        v-if="destructiveMode === 'withdraw'"
        type="button"
        data-action="confirm-withdraw"
        @click="confirmWithdraw"
      >
        Confirm
      </button>
      <button
        v-else
        type="button"
        data-action="confirm-delete"
        @click="confirmDelete"
      >
        Delete
      </button>
      <button type="button" data-action="cancel-destructive" @click="closeDestructiveDialog">Cancel</button>
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
