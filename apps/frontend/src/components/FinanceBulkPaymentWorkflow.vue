<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  regattaId: {
    type: String,
    default: '00000000-0000-0000-0000-000000000000'
  }
})

const entryIdsText = ref('')
const clubIdsText = ref('')
const paymentStatus = ref('paid')
const paymentReference = ref('')
const idempotencyKey = ref('')
const isSubmitting = ref(false)
const submitError = ref('')
const result = ref(null)
const pendingPayload = ref(null)

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
    submitError.value = 'Select at least one entry ID or club ID.'
    return
  }

  if (invalidEntryIds.value.length > 0 || invalidClubIds.value.length > 0) {
    submitError.value = 'All IDs must be valid UUIDs.'
    return
  }

  pendingPayload.value = {
    entry_ids: parsedEntryIds.value,
    club_ids: parsedClubIds.value,
    payment_status: paymentStatus.value,
    payment_reference: paymentReference.value.trim() || undefined,
    idempotency_key: idempotencyKey.value.trim() || undefined
  }
}

async function confirmSubmit() {
  if (!pendingPayload.value) {
    return
  }

  isSubmitting.value = true
  submitError.value = ''

  try {
    const response = await fetch(`/api/v1/regattas/${props.regattaId}/payments/mark_bulk`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(pendingPayload.value)
    })

    const payload = await response.json()

    if (!response.ok) {
      const message = payload?.error?.message || 'Bulk payment update failed.'
      throw new Error(message)
    }

    result.value = payload
    pendingPayload.value = null
  } catch (error) {
    submitError.value = error instanceof Error ? error.message : 'Bulk payment update failed.'
  } finally {
    isSubmitting.value = false
  }
}

function cancelConfirmation() {
  pendingPayload.value = null
}
</script>

<template>
  <section class="finance-bulk" aria-labelledby="finance-bulk-title">
    <h1 id="finance-bulk-title">Bulk Payment Status</h1>
    <p class="lead">Mark multiple entries or clubs as paid/unpaid with one auditable operation.</p>

    <form class="form-grid" @submit.prevent="startConfirmation">
      <label>
        <span>Entry IDs (UUID, comma/newline separated)</span>
        <textarea
          v-model="entryIdsText"
          rows="4"
          name="entry_ids"
          autocomplete="off"
          placeholder="7f7af3d8-9090-49d5-b21c-9cc12d35a0e6"
        />
      </label>

      <label>
        <span>Club IDs (UUID, comma/newline separated)</span>
        <textarea
          v-model="clubIdsText"
          rows="3"
          name="club_ids"
          autocomplete="off"
          placeholder="81a4c9ea-2e7d-4e67-8c0e-4657d8ce26fd"
        />
      </label>

      <label>
        <span>Target Status</span>
        <select v-model="paymentStatus" name="payment_status">
          <option value="paid">Paid</option>
          <option value="unpaid">Unpaid</option>
        </select>
      </label>

      <label>
        <span>Payment Reference (optional)</span>
        <input v-model="paymentReference" type="text" name="payment_reference" />
      </label>

      <label>
        <span>Idempotency Key (optional)</span>
        <input v-model="idempotencyKey" type="text" name="idempotency_key" maxlength="128" />
      </label>

      <p v-if="submitError" class="error" role="alert">{{ submitError }}</p>

      <button type="submit" class="primary">Review Bulk Update</button>
    </form>

    <section v-if="pendingPayload" class="confirm" aria-live="polite">
      <h2>Confirm Bulk Update</h2>
      <p>
        You are about to mark
        <strong>{{ pendingPayload.entry_ids.length }}</strong>
        entries and
        <strong>{{ pendingPayload.club_ids.length }}</strong>
        clubs as
        <strong>{{ pendingPayload.payment_status }}</strong>.
      </p>
      <div class="confirm-actions">
        <button type="button" class="primary" :disabled="isSubmitting" @click="confirmSubmit">
          {{ isSubmitting ? 'Submitting…' : 'Confirm and Apply' }}
        </button>
        <button type="button" @click="cancelConfirmation">Cancel</button>
      </div>
    </section>

    <section v-if="result" class="result" aria-live="polite">
      <h2>Result</h2>
      <p>{{ result.message }}</p>
      <ul>
        <li>Total requested: {{ result.total_requested }}</li>
        <li>Processed: {{ result.processed_count }}</li>
        <li>Updated: {{ result.updated_count }}</li>
        <li>Unchanged: {{ result.unchanged_count }}</li>
        <li>Failed: {{ result.failed_count }}</li>
        <li>Idempotent replay: {{ result.idempotent_replay ? 'yes' : 'no' }}</li>
      </ul>

      <table v-if="result.failures?.length" class="failures">
        <caption>Partial failure diagnostics</caption>
        <thead>
          <tr>
            <th scope="col">Scope</th>
            <th scope="col">ID</th>
            <th scope="col">Code</th>
            <th scope="col">Message</th>
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
