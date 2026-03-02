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
const invoiceId = validateRouteParam(route.params.invoiceId, 'invoiceId')
const hasValidRouteParams = Boolean(regattaId && invoiceId)

const invoice = ref(null)
const loading = ref(true)
const error = ref(null)
const marking = ref(false)
const markError = ref(null)
const markSuccess = ref(false)

const paymentReference = ref('')
let successMessageTimeoutId = null

function clearSuccessMessageTimeout() {
  if (successMessageTimeoutId !== null) {
    clearTimeout(successMessageTimeoutId)
    successMessageTimeoutId = null
  }
}

async function loadInvoice() {
  if (!hasValidRouteParams) {
    error.value = t('finance.invalid_route_params')
    loading.value = false
    return
  }

  loading.value = true
  error.value = null
  try {
    invoice.value = await financeApi.getInvoice(regattaId, invoiceId)
    paymentReference.value = invoice.value.payment_reference || ''
  } catch (err) {
    error.value = err.message || t('common.error')
  } finally {
    loading.value = false
  }
}

async function markAsPaid() {
  if (!hasValidRouteParams) {
    markError.value = t('finance.invalid_route_params')
    return
  }

  marking.value = true
  markError.value = null
  markSuccess.value = false
  try {
    const payload = {
      payment_reference: paymentReference.value.trim() || undefined
    }
    invoice.value = await financeApi.markInvoicePaid(regattaId, invoiceId, payload)
    markSuccess.value = true
    clearSuccessMessageTimeout()
    successMessageTimeoutId = setTimeout(() => {
      markSuccess.value = false
    }, SUCCESS_MESSAGE_DURATION_MS)
  } catch (err) {
    markError.value = err.message || t('finance.invoice.mark_paid_error')
  } finally {
    marking.value = false
  }
}

onMounted(() => {
  loadInvoice()
})

onUnmounted(() => {
  clearSuccessMessageTimeout()
})
</script>

<template>
  <div class="invoice-detail">
    <h2>{{ t('finance.invoice.detail_title') }}</h2>

    <div v-if="loading" class="loading">{{ t('finance.invoice.loading') }}</div>
    <div v-else-if="error" class="error" role="alert">{{ error }}</div>
    <div v-else-if="invoice" class="content">
      <dl class="invoice-info">
        <dt>{{ t('finance.invoice.invoice_id') }}</dt>
        <dd class="mono">{{ invoiceId }}</dd>

        <dt>{{ t('finance.invoice.club_name') }}</dt>
        <dd>{{ invoice.club_name || '-' }}</dd>

        <dt>{{ t('finance.invoice.amount') }}</dt>
        <dd>{{ invoice.amount?.toFixed(2) || '-' }}</dd>

        <dt>{{ t('finance.invoice.status') }}</dt>
        <dd>
          <span :class="`status-badge status-badge--${invoice.status}`">
            {{ invoice.status === 'paid' ? t('finance.invoice.status_paid') : t('finance.invoice.status_unpaid') }}
          </span>
        </dd>

        <dt>{{ t('finance.invoice.created_at') }}</dt>
        <dd>{{ invoice.created_at ? new Date(invoice.created_at).toLocaleString() : '-' }}</dd>

        <template v-if="invoice.paid_at">
          <dt>{{ t('finance.invoice.paid_at') }}</dt>
          <dd>{{ new Date(invoice.paid_at).toLocaleString() }}</dd>
        </template>

        <template v-if="invoice.payment_reference">
          <dt>{{ t('finance.invoice.payment_reference') }}</dt>
          <dd>{{ invoice.payment_reference }}</dd>
        </template>
      </dl>

      <form v-if="invoice.status === 'unpaid'" class="finance-form mark-paid-form" @submit.prevent="markAsPaid">
        <h3>{{ t('finance.invoice.mark_paid') }}</h3>

        <label>
          <span>{{ t('finance.invoice.payment_reference') }}</span>
          <input v-model="paymentReference" type="text" :disabled="marking" />
        </label>

        <div v-if="markError" class="error" role="alert">{{ markError }}</div>
        <div v-if="markSuccess" class="success" role="status">{{ t('finance.invoice.mark_paid_success') }}</div>

        <button type="submit" class="primary" :disabled="marking">
          {{ marking ? t('finance.bulk.submitting') : t('finance.invoice.mark_paid') }}
        </button>
      </form>

      <section v-if="invoice.entries && invoice.entries.length > 0" class="entries-section">
        <h3>{{ t('finance.invoice.entries') }}</h3>
        <table class="finance-table">
          <thead>
            <tr>
              <th>{{ t('finance.entry.entry_id') }}</th>
              <th>{{ t('entry.crew') }}</th>
              <th>{{ t('finance.invoice.amount') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="entry in invoice.entries" :key="entry.entry_id">
              <td class="mono">{{ entry.entry_id }}</td>
              <td>{{ entry.crew_name || '-' }}</td>
              <td>{{ entry.amount?.toFixed(2) || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </div>
  </div>
</template>

<style scoped>
@import './financeViewShared.css';

.invoice-detail {
  max-width: 56rem;
  margin: 2rem auto;
  padding: 1.5rem;
}

.invoice-info {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.5rem 1.5rem;
  margin-bottom: 2rem;
  padding: 1.5rem;
  background: var(--rd-surface);
  border: 1px solid var(--rd-border);
  border-radius: 0.5rem;
}

.invoice-info dt {
  font-weight: 600;
  color: var(--rd-text-muted);
}

.invoice-info dd {
  margin: 0;
  color: var(--rd-text);
}

.mark-paid-form {
  margin-bottom: 2rem;
}

.entries-section {
  padding: 1.5rem;
  border: 1px solid var(--rd-border);
  border-radius: 0.5rem;
  background: var(--rd-bg);
}
</style>
