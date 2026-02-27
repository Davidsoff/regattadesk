<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createApiClient, createFinanceApi } from '../api'

const props = defineProps({
  regattaId: {
    type: String,
    required: true
  }
})
const { t } = useI18n()

// Initialize API client
const apiClient = createApiClient()
const financeApi = createFinanceApi(apiClient)

const entryIdsText = ref('')
const clubIdsText = ref('')
const paymentStatus = ref('paid')
const paymentReference = ref('')
const idempotencyKey = ref('')
const isSubmitting = ref(false)
const submitError = ref('')
const result = ref(null)
const pendingPayload = ref(null)
const density = ref('comfortable')
const sseStatus = ref('offline')
let eventSource = null

const uuidPattern =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

function parseUuidList(input) {
  return input
    .split(/[\s,]+/)
    .map((token) => token.trim())
    .filter((token) => token.length > 0)
}

const parsedEntryIds = computed(() => parseUuidList(entryIdsText.value))
const parsedClubIds = computed(() => parseUuidList(clubIdsText.value))

const invalidEntryIds = computed(() => parsedEntryIds.value.filter((id) => !uuidPattern.test(id)))
const invalidClubIds = computed(() => parsedClubIds.value.filter((id) => !uuidPattern.test(id)))

function startConfirmation() {
  submitError.value = ''

  if (parsedEntryIds.value.length === 0 && parsedClubIds.value.length === 0) {
    submitError.value = t('finance.bulk.select_one')
    return
  }

  if (invalidEntryIds.value.length > 0 || invalidClubIds.value.length > 0) {
    submitError.value = t('finance.bulk.invalid_uuid')
    return
  }

  const payload = {
    payment_status: paymentStatus.value,
    payment_reference: paymentReference.value.trim() || undefined,
    idempotency_key: idempotencyKey.value.trim() || undefined
  }
  if (parsedEntryIds.value.length > 0) {
    payload.entry_ids = parsedEntryIds.value
  }
  if (parsedClubIds.value.length > 0) {
    payload.club_ids = parsedClubIds.value
  }
  pendingPayload.value = payload
}

async function confirmSubmit() {
  if (!pendingPayload.value) {
    return
  }

  isSubmitting.value = true
  submitError.value = ''

  try {
    const responseData = await financeApi.markBulkPayment(props.regattaId, pendingPayload.value)
    result.value = responseData
    pendingPayload.value = null
  } catch (error) {
    const isObjectError = error && typeof error === 'object'
    const isUnknownError = isObjectError && 'code' in error && error.code === 'UNKNOWN_ERROR'
    const hasMessage =
      isObjectError &&
      'message' in error &&
      typeof error.message === 'string' &&
      error.message.trim().length > 0
    submitError.value = !hasMessage || isUnknownError ? t('finance.bulk.error') : error.message
  } finally {
    isSubmitting.value = false
  }
}

function cancelConfirmation() {
  pendingPayload.value = null
}

onMounted(() => {
  if (typeof EventSource === 'undefined') {
    return
  }
  eventSource = new EventSource(`/public/regattas/${props.regattaId}/events`)
  eventSource.onopen = () => {
    sseStatus.value = 'live'
  }
  eventSource.onerror = () => {
    sseStatus.value = 'offline'
  }
})

onUnmounted(() => {
  if (eventSource) {
    eventSource.close()
  }
})
</script>

<template>
  <section class="finance-bulk" aria-labelledby="finance-bulk-title">
    <div class="top-row">
      <h1 id="finance-bulk-title">{{ t('finance.bulk.title') }}</h1>
      <span class="sse-pill" :class="`sse-pill--${sseStatus}`">
        {{ sseStatus === 'live' ? t('live.live') : t('live.offline') }}
      </span>
    </div>
    <p class="lead">{{ t('finance.bulk.subtitle') }}</p>

    <form class="form-grid" @submit.prevent="startConfirmation">
      <label>
        <span>{{ t('finance.bulk.entry_ids_label') }}</span>
        <textarea
          v-model="entryIdsText"
          rows="4"
          name="entry_ids"
          autocomplete="off"
          :placeholder="t('finance.bulk.entry_ids_placeholder')"
        />
      </label>

      <label>
        <span>{{ t('finance.bulk.club_ids_label') }}</span>
        <textarea
          v-model="clubIdsText"
          rows="3"
          name="club_ids"
          autocomplete="off"
          :placeholder="t('finance.bulk.club_ids_placeholder')"
        />
      </label>

      <label>
        <span>{{ t('finance.bulk.target_status') }}</span>
        <select v-model="paymentStatus" name="payment_status">
          <option value="paid">{{ t('finance.bulk.status_paid') }}</option>
          <option value="unpaid">{{ t('finance.bulk.status_unpaid') }}</option>
        </select>
      </label>

      <label>
        <span>{{ t('finance.bulk.payment_reference') }}</span>
        <input v-model="paymentReference" type="text" name="payment_reference" />
      </label>

      <label>
        <span>{{ t('finance.bulk.idempotency_key') }}</span>
        <input v-model="idempotencyKey" type="text" name="idempotency_key" maxlength="128" />
      </label>

      <p v-if="submitError" class="error" role="alert">{{ submitError }}</p>

      <button type="submit" class="primary">{{ t('finance.bulk.review_button') }}</button>
    </form>

    <section v-if="pendingPayload" class="confirm" aria-live="polite">
      <h2>{{ t('finance.bulk.confirm') }}</h2>
      <p>
        {{ t('finance.bulk.confirm_prefix') }}
        <strong>{{ pendingPayload.entry_ids?.length ?? 0 }}</strong>
        {{ t('finance.bulk.confirm_entries_and') }}
        <strong>{{ pendingPayload.club_ids?.length ?? 0 }}</strong>
        {{ t('finance.bulk.confirm_clubs_as') }}
        <strong>{{ pendingPayload.payment_status }}</strong>.
      </p>
      <div class="confirm-actions">
        <button type="button" class="primary" :disabled="isSubmitting" @click="confirmSubmit">
          {{ isSubmitting ? t('finance.bulk.submitting') : t('finance.bulk.confirm_apply') }}
        </button>
        <button type="button" @click="cancelConfirmation">{{ t('common.cancel') }}</button>
      </div>
    </section>

    <section v-if="result" class="result" aria-live="polite">
      <h2>{{ t('finance.bulk.result') }}</h2>
      <p>{{ result.message }}</p>
      <ul>
        <li>{{ t('finance.bulk.total_requested') }}: {{ result.total_requested }}</li>
        <li>{{ t('finance.bulk.processed') }}: {{ result.processed_count }}</li>
        <li>{{ t('finance.bulk.updated', { count: result.updated_count }) }}</li>
        <li>{{ t('finance.bulk.unchanged') }}: {{ result.unchanged_count }}</li>
        <li>{{ t('finance.bulk.failed') }}: {{ result.failed_count }}</li>
        <li>
          {{ t('finance.bulk.idempotent_replay') }}:
          {{ result.idempotent_replay ? t('common.yes') : t('common.no') }}
        </li>
      </ul>

      <div v-if="result.failures?.length" class="table-controls">
        <label>
          <span>{{ t('finance.bulk.density') }}</span>
          <select v-model="density" name="density">
            <option value="comfortable">{{ t('finance.bulk.density_comfortable') }}</option>
            <option value="compact">{{ t('finance.bulk.density_compact') }}</option>
          </select>
        </label>
      </div>

      <table
        v-if="result.failures?.length"
        class="failures"
        :class="{ 'failures--compact': density === 'compact' }"
      >
        <caption>{{ t('finance.bulk.partial_failures') }}</caption>
        <thead>
          <tr>
            <th scope="col">{{ t('finance.bulk.scope') }}</th>
            <th scope="col">ID</th>
            <th scope="col">{{ t('finance.bulk.code') }}</th>
            <th scope="col">{{ t('finance.bulk.message') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="failure in result.failures" :key="`${failure.scope_type}-${failure.id}`">
            <td>{{ failure.scope_type }}</td>
            <td class="mono">{{ failure.id }}</td>
            <td>{{ failure.code }}</td>
            <td>{{ failure.message }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<style scoped>
.finance-bulk {
  max-width: 56rem;
  margin: 2rem auto;
  padding: 1.5rem;
  border: 1px solid #d7dee7;
  border-radius: 1rem;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}

h1,
h2 {
  margin: 0;
  color: #1d3557;
}

.top-row {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  justify-content: space-between;
}

.sse-pill {
  border-radius: 99px;
  padding: 0.25rem 0.75rem;
  font-size: 0.9rem;
  font-weight: 600;
}

.sse-pill--live {
  color: #0a6d35;
  background: #dff5e6;
}

.sse-pill--offline {
  color: #8b2531;
  background: #fce4e8;
}

.lead {
  margin: 0.5rem 0 1.5rem;
  color: #34506f;
}

.form-grid {
  display: grid;
  gap: 1rem;
}

label {
  display: grid;
  gap: 0.4rem;
}

textarea,
input,
select,
button {
  font: inherit;
  border: 1px solid #b5c4d5;
  border-radius: 0.5rem;
  padding: 0.6rem 0.75rem;
}

button {
  cursor: pointer;
}

button.primary {
  background: #1d3557;
  border-color: #1d3557;
  color: #ffffff;
}

.confirm,
.result {
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid #d7dee7;
}

.confirm-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: 0.75rem;
}

.error {
  margin: 0;
  color: #ac1a2f;
}

.table-controls {
  margin-top: 0.5rem;
  margin-bottom: 0.75rem;
}

.failures {
  margin-top: 1rem;
  width: 100%;
  border-collapse: collapse;
}

.failures th,
.failures td {
  border: 1px solid #d7dee7;
  text-align: left;
  padding: 0.45rem 0.5rem;
}

.failures--compact th,
.failures--compact td {
  padding: 0.25rem 0.4rem;
  line-height: 1.2;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

@media (max-width: 720px) {
  .finance-bulk {
    margin: 1rem;
    padding: 1rem;
  }
}
</style>
