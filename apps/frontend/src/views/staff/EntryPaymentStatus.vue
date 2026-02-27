<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { createApiClient, createFinanceApi } from '../../api'

const route = useRoute()
const { t } = useI18n()
const apiClient = createApiClient()
const financeApi = createFinanceApi(apiClient)

const SUCCESS_MESSAGE_DURATION_MS = 3000

const regattaId = route.params.regattaId
const entryId = route.params.entryId

const entry = ref(null)
const loading = ref(true)
const error = ref(null)
const updating = ref(false)
const updateError = ref(null)
const updateSuccess = ref(false)

const paymentStatus = ref('paid')
const paymentReference = ref('')

async function loadEntry() {
  loading.value = true
  error.value = null
  try {
    entry.value = await financeApi.getEntryPaymentStatus(regattaId, entryId)
    paymentStatus.value = entry.value.payment_status || 'unpaid'
    paymentReference.value = entry.value.payment_reference || ''
  } catch (err) {
    error.value = err.message || t('finance.entry.not_found')
  } finally {
    loading.value = false
  }
}

async function updateStatus() {
  updating.value = true
  updateError.value = null
  updateSuccess.value = false
  try {
    const payload = {
      payment_status: paymentStatus.value,
      payment_reference: paymentReference.value.trim() || undefined
    }
    entry.value = await financeApi.updateEntryPaymentStatus(regattaId, entryId, payload)
    updateSuccess.value = true
    setTimeout(() => {
      updateSuccess.value = false
    }, SUCCESS_MESSAGE_DURATION_MS)
  } catch (err) {
    updateError.value = err.message || t('finance.entry.update_error')
  } finally {
    updating.value = false
  }
}

onMounted(() => {
  loadEntry()
})
</script>

<template>
  <div class="entry-payment-status">
    <h2>{{ t('finance.entry.title') }}</h2>

    <div v-if="loading" class="loading">{{ t('finance.entry.loading') }}</div>
    <div v-else-if="error" class="error" role="alert">{{ error }}</div>
    <div v-else-if="entry" class="content">
      <dl class="entry-info">
        <dt>{{ t('finance.entry.entry_id') }}</dt>
        <dd class="mono">{{ entryId }}</dd>

        <dt>{{ t('finance.entry.payment_status') }}</dt>
        <dd>
          <span :class="`status-badge status-badge--${entry.payment_status}`">
            {{ entry.payment_status === 'paid' ? t('finance.bulk.status_paid') : t('finance.bulk.status_unpaid') }}
          </span>
        </dd>

        <template v-if="entry.paid_at">
          <dt>{{ t('finance.entry.paid_at') }}</dt>
          <dd>{{ new Date(entry.paid_at).toLocaleString() }}</dd>
        </template>

        <template v-if="entry.paid_by">
          <dt>{{ t('finance.entry.paid_by') }}</dt>
          <dd>{{ entry.paid_by }}</dd>
        </template>

        <template v-if="entry.payment_reference">
          <dt>{{ t('finance.entry.payment_reference') }}</dt>
          <dd>{{ entry.payment_reference }}</dd>
        </template>
      </dl>

      <form class="update-form" @submit.prevent="updateStatus">
        <h3>{{ t('finance.entry.update_status') }}</h3>

        <label>
          <span>{{ t('finance.entry.payment_status') }}</span>
          <select v-model="paymentStatus" :disabled="updating">
            <option value="paid">{{ t('finance.bulk.status_paid') }}</option>
            <option value="unpaid">{{ t('finance.bulk.status_unpaid') }}</option>
          </select>
        </label>

        <label>
          <span>{{ t('finance.entry.payment_reference') }}</span>
          <input v-model="paymentReference" type="text" :disabled="updating" />
        </label>

        <div v-if="updateError" class="error" role="alert">{{ updateError }}</div>
        <div v-if="updateSuccess" class="success" role="status">{{ t('finance.entry.update_success') }}</div>

        <button type="submit" class="primary" :disabled="updating">
          {{ updating ? t('finance.bulk.submitting') : t('common.submit') }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.entry-payment-status {
  max-width: 56rem;
  margin: 2rem auto;
  padding: 1.5rem;
}

h2,
h3 {
  margin: 0 0 1rem;
  color: #1d3557;
}

.loading,
.error {
  padding: 1rem;
  border-radius: 0.5rem;
}

.loading {
  background: #f0f4f8;
  color: #34506f;
}

.error {
  background: #fce4e8;
  color: #8b2531;
}

.success {
  padding: 1rem;
  border-radius: 0.5rem;
  background: #dff5e6;
  color: #0a6d35;
  margin-bottom: 1rem;
}

.entry-info {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.5rem 1.5rem;
  margin-bottom: 2rem;
  padding: 1.5rem;
  background: #f8fbff;
  border: 1px solid #d7dee7;
  border-radius: 0.5rem;
}

.entry-info dt {
  font-weight: 600;
  color: #34506f;
}

.entry-info dd {
  margin: 0;
  color: #1d3557;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.9em;
}

.status-badge {
  display: inline-block;
  padding: 0.25rem 0.75rem;
  border-radius: 99px;
  font-size: 0.9rem;
  font-weight: 600;
}

.status-badge--paid {
  background: #dff5e6;
  color: #0a6d35;
}

.status-badge--unpaid {
  background: #fce4e8;
  color: #8b2531;
}

.update-form {
  padding: 1.5rem;
  border: 1px solid #d7dee7;
  border-radius: 0.5rem;
  background: #ffffff;
}

.update-form label {
  display: grid;
  gap: 0.4rem;
  margin-bottom: 1rem;
}

.update-form input,
.update-form select {
  font: inherit;
  border: 1px solid #b5c4d5;
  border-radius: 0.5rem;
  padding: 0.6rem 0.75rem;
}

.update-form button {
  font: inherit;
  border: 1px solid #1d3557;
  border-radius: 0.5rem;
  padding: 0.6rem 1.5rem;
  cursor: pointer;
  background: #1d3557;
  color: #ffffff;
}

.update-form button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
