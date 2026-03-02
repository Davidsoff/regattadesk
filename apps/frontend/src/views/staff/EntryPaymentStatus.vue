<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { createApiClient, createFinanceApi } from '../../api'
import { SUCCESS_MESSAGE_DURATION_MS, validateRouteParam } from './financeViewShared'

const route = useRoute()
const { t } = useI18n()
const apiClient = createApiClient()
const financeApi = createFinanceApi(apiClient)

const regattaId = validateRouteParam(route.params.regattaId, 'regattaId')
const entryId = validateRouteParam(route.params.entryId, 'entryId')
const hasValidRouteParams = Boolean(regattaId && entryId)

const entry = ref(null)
const loading = ref(true)
const error = ref(null)
const updating = ref(false)
const updateError = ref(null)
const updateSuccess = ref(false)

const paymentStatus = ref('paid')
const paymentReference = ref('')
let successMessageTimeoutId = null

function clearSuccessMessageTimeout() {
  if (successMessageTimeoutId !== null) {
    clearTimeout(successMessageTimeoutId)
    successMessageTimeoutId = null
  }
}

async function loadEntry() {
  if (!hasValidRouteParams) {
    error.value = t('finance.invalid_route_params')
    loading.value = false
    return
  }

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
  if (!hasValidRouteParams) {
    updateError.value = t('finance.invalid_route_params')
    return
  }

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
    clearSuccessMessageTimeout()
    successMessageTimeoutId = setTimeout(() => {
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

onUnmounted(() => {
  clearSuccessMessageTimeout()
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

      <form class="finance-form" @submit.prevent="updateStatus">
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
        <output v-if="updateSuccess" class="success" aria-live="polite">{{ t('finance.entry.update_success') }}</output>

        <button type="submit" class="primary" :disabled="updating">
          {{ updating ? t('finance.bulk.submitting') : t('common.submit') }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
@import './financeViewShared.css';

.entry-payment-status {
  max-width: 56rem;
  margin: 2rem auto;
  padding: 1.5rem;
}

.entry-info {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.5rem 1.5rem;
  margin-bottom: 2rem;
  padding: 1.5rem;
  background: var(--rd-surface);
  border: 1px solid var(--rd-border);
  border-radius: 0.5rem;
}

.entry-info dt {
  font-weight: 600;
  color: var(--rd-text-muted);
}

.entry-info dd {
  margin: 0;
  color: var(--rd-text);
}
</style>
