<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { createApiClient, createFinanceApi } from '../../api'
import { SUCCESS_MESSAGE_DURATION_MS, validateRouteParam } from './financeViewShared'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const apiClient = createApiClient()
const financeApi = createFinanceApi(apiClient)

const INVOICE_GENERATION_MAX_POLL_ATTEMPTS = 6
const INVOICE_GENERATION_POLL_INTERVAL_MS = 1000

const regattaId = validateRouteParam(route.params.regattaId, 'regattaId')
const hasValidRouteParams = Boolean(regattaId)

const invoices = ref([])
const loading = ref(true)
const refreshing = ref(false)
const error = ref(null)
const refreshError = ref(null)
const generating = ref(false)
const generateError = ref(null)
const generateSuccess = ref('')
const generationJob = ref(null)
let successMessageTimeoutId = null
let isUnmounted = false

const generationStatusMessage = computed(() => {
  switch (generationJob.value?.status) {
    case 'pending':
      return t('finance.invoice.generate_pending')
    case 'running':
      return t('finance.invoice.generate_running')
    case 'completed':
      return t('finance.invoice.generate_completed')
    default:
      return ''
  }
})

function clearSuccessMessageTimeout() {
  if (successMessageTimeoutId !== null) {
    clearTimeout(successMessageTimeoutId)
    successMessageTimeoutId = null
  }
}

async function sleep(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms)
  })
}

function formatAmount(amount, currency = 'EUR') {
  if (typeof amount !== 'number') {
    return '-'
  }

  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency
    }).format(amount)
  } catch {
    return amount.toFixed(2)
  }
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }

  return new Date(value).toLocaleString()
}

function invoiceStatusLabel(status) {
  switch (status) {
    case 'draft':
      return t('finance.invoice.status_draft')
    case 'sent':
      return t('finance.invoice.status_sent')
    case 'paid':
      return t('finance.invoice.status_paid')
    case 'cancelled':
      return t('finance.invoice.status_cancelled')
    default:
      return status || '-'
  }
}

async function loadInvoices({ background = false } = {}) {
  if (!hasValidRouteParams) {
    error.value = t('finance.invalid_route_params')
    loading.value = false
    return
  }

  const preserveContent = background || invoices.value.length > 0

  if (preserveContent) {
    refreshing.value = true
  } else {
    loading.value = true
    error.value = null
  }

  refreshError.value = null
  try {
    const result = await financeApi.listInvoices(regattaId)
    invoices.value = result.data || []
    return true
  } catch (err) {
    const message = err.message || t('common.error')

    if (preserveContent) {
      refreshError.value = message
      return false
    }

    error.value = message
    return false
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

async function pollGenerationJob(jobId) {
  for (let attempt = 0; attempt < INVOICE_GENERATION_MAX_POLL_ATTEMPTS; attempt += 1) {
    if (isUnmounted) {
      return generationJob.value
    }

    if (attempt > 0) {
      await sleep(INVOICE_GENERATION_POLL_INTERVAL_MS)
    }

    const job = await financeApi.getInvoiceGenerationJob(regattaId, jobId)
    generationJob.value = job

    if (job.status === 'completed' || job.status === 'failed') {
      return job
    }
  }

  return generationJob.value
}

async function generateInvoices() {
  if (!hasValidRouteParams) {
    generateError.value = t('finance.invalid_route_params')
    return
  }

  generating.value = true
  generateError.value = null
  generateSuccess.value = ''
  generationJob.value = null
  try {
    const acceptedJob = await financeApi.generateInvoices(regattaId, {})
    generationJob.value = acceptedJob

    const finalJob =
      acceptedJob?.job_id && acceptedJob.status !== 'completed' && acceptedJob.status !== 'failed'
        ? await pollGenerationJob(acceptedJob.job_id)
        : acceptedJob

    if (finalJob?.status === 'failed') {
      generateError.value = finalJob.error_message || t('finance.invoice.generate_error')
      return
    }

    if (finalJob?.status === 'completed') {
      await loadInvoices({ background: invoices.value.length > 0 })
      generateSuccess.value = t('finance.invoice.generate_success')
      clearSuccessMessageTimeout()
      successMessageTimeoutId = setTimeout(() => {
        generateSuccess.value = ''
      }, SUCCESS_MESSAGE_DURATION_MS)
      return
    }

    generateSuccess.value = t('finance.invoice.generate_pending')
  } catch (err) {
    generateError.value = err.message || t('finance.invoice.generate_error')
  } finally {
    generating.value = false
  }
}

async function refreshInvoices() {
  await loadInvoices({ background: invoices.value.length > 0 })
}

function viewInvoice(invoiceId) {
  router.push({
    name: 'staff-regatta-finance-invoice',
    params: { regattaId, invoiceId }
  })
}

onMounted(() => {
  loadInvoices()
})

onUnmounted(() => {
  isUnmounted = true
  clearSuccessMessageTimeout()
})
</script>

<template>
  <div class="invoice-list">
    <div class="header">
      <div>
        <h2>{{ t('finance.invoice.list_title') }}</h2>
        <p class="subtle">{{ t('finance.invoice.list_description') }}</p>
      </div>
      <div class="actions">
        <button class="secondary" :disabled="loading || refreshing || generating" @click="refreshInvoices">
          {{ refreshing ? t('common.loading') : t('finance.invoice.refresh') }}
        </button>
        <button class="primary" :disabled="generating || refreshing" @click="generateInvoices">
          {{ generating ? t('finance.bulk.submitting') : t('finance.invoice.generate') }}
        </button>
      </div>
    </div>

    <div v-if="generateError" class="error" role="alert">{{ generateError }}</div>
    <div v-else-if="refreshError" class="warning" role="status">{{ refreshError }}</div>
    <output v-if="generateSuccess" class="success" aria-live="polite">{{ generateSuccess }}</output>
    <p v-if="generationStatusMessage && !generateSuccess" class="info" aria-live="polite">
      {{ generationStatusMessage }}
    </p>

    <div v-if="loading" class="loading">{{ t('finance.invoice.loading') }}</div>
    <div v-else-if="error" class="error" role="alert">{{ error }}</div>
    <div v-else-if="invoices.length === 0" class="empty">{{ t('finance.invoice.no_invoices') }}</div>
    <table v-else class="invoices-table">
      <thead>
        <tr>
          <th>{{ t('finance.invoice.invoice_number') }}</th>
          <th>{{ t('finance.invoice.invoice_id') }}</th>
          <th>{{ t('finance.invoice.club_id') }}</th>
          <th>{{ t('finance.invoice.amount') }}</th>
          <th>{{ t('finance.invoice.status') }}</th>
          <th>{{ t('finance.invoice.generated_at') }}</th>
          <th>{{ t('common.open') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="invoice in invoices" :key="invoice.id">
          <td>{{ invoice.invoice_number || '-' }}</td>
          <td class="mono">{{ invoice.id }}</td>
          <td class="mono">{{ invoice.club_id || '-' }}</td>
          <td>{{ formatAmount(invoice.total_amount, invoice.currency) }}</td>
          <td>
            <span :class="`status-badge status-badge--${invoice.status}`">
              {{ invoiceStatusLabel(invoice.status) }}
            </span>
          </td>
          <td>{{ formatDateTime(invoice.generated_at) }}</td>
          <td>
            <button class="link-button" @click="viewInvoice(invoice.id)">
              {{ t('finance.invoice.view_details') }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
@import './financeViewShared.css';

.invoice-list {
  max-width: 72rem;
  margin: 2rem auto;
  padding: 1.5rem;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

h2 {
  margin: 0;
  color: var(--rd-text);
}

.subtle {
  margin: 0.25rem 0 0;
  color: var(--rd-text-muted);
}

.actions {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.primary {
  font: inherit;
  border: 1px solid var(--rd-text);
  border-radius: 0.5rem;
  padding: 0.6rem 1.5rem;
  cursor: pointer;
  background: var(--rd-text);
  color: var(--rd-bg);
}

.secondary {
  font: inherit;
  border: 1px solid var(--rd-border);
  border-radius: 0.5rem;
  padding: 0.6rem 1.5rem;
  cursor: pointer;
  background: var(--rd-bg);
  color: var(--rd-text);
}

.primary:disabled,
.secondary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error,
.warning,
.info {
  margin-bottom: 1rem;
}

.invoices-table {
  width: 100%;
  border-collapse: collapse;
  background: var(--rd-bg);
  border: 1px solid var(--rd-border);
  border-radius: 0.5rem;
  overflow: hidden;
}

.invoices-table th,
.invoices-table td {
  text-align: left;
  padding: 0.75rem;
  border-bottom: 1px solid var(--rd-border);
}

.invoices-table th {
  font-weight: 600;
  color: var(--rd-text-muted);
  background: var(--rd-surface);
}

.invoices-table tbody tr:last-child td {
  border-bottom: none;
}

.invoices-table tbody tr:hover {
  background: var(--rd-surface);
}

.link-button {
  background: none;
  border: none;
  color: var(--rd-text);
  text-decoration: underline;
  cursor: pointer;
  font: inherit;
  padding: 0;
}

.link-button:hover {
  color: var(--rd-accent);
}
</style>
