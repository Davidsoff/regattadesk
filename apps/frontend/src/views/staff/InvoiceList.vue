<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { createApiClient, createFinanceApi } from '../../api'
import { SUCCESS_MESSAGE_DURATION_MS, validateRouteParam } from './financeViewShared'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const apiClient = createApiClient()
const financeApi = createFinanceApi(apiClient)

const INVOICE_GENERATION_MAX_REFRESH_ATTEMPTS = 6
const INVOICE_GENERATION_REFRESH_INTERVAL_MS = 1000

const regattaId = validateRouteParam(route.params.regattaId, 'regattaId')
const hasValidRouteParams = Boolean(regattaId)

const invoices = ref([])
const loading = ref(true)
const error = ref(null)
const generating = ref(false)
const generateError = ref(null)
const generateSuccess = ref(false)
let successMessageTimeoutId = null
let isUnmounted = false

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

async function loadInvoices() {
  if (!hasValidRouteParams) {
    error.value = t('finance.invalid_route_params')
    loading.value = false
    return
  }

  loading.value = true
  error.value = null
  try {
    const result = await financeApi.listInvoices(regattaId)
    invoices.value = result.data || []
  } catch (err) {
    error.value = err.message || t('common.error')
  } finally {
    loading.value = false
  }
}

async function generateInvoices() {
  if (!hasValidRouteParams) {
    generateError.value = t('finance.invalid_route_params')
    return
  }

  generating.value = true
  generateError.value = null
  generateSuccess.value = false
  try {
    const previousInvoiceCount = invoices.value.length
    await financeApi.generateInvoices(regattaId)
    generateSuccess.value = true
    clearSuccessMessageTimeout()
    successMessageTimeoutId = setTimeout(() => {
      generateSuccess.value = false
    }, SUCCESS_MESSAGE_DURATION_MS)

    // We do not have a generation job-status endpoint yet, so we use bounded polling.
    for (let attempt = 0; attempt < INVOICE_GENERATION_MAX_REFRESH_ATTEMPTS; attempt += 1) {
      if (isUnmounted) {
        return
      }

      await loadInvoices()

      if (invoices.value.length > previousInvoiceCount) {
        break
      }

      if (attempt < INVOICE_GENERATION_MAX_REFRESH_ATTEMPTS - 1) {
        await sleep(INVOICE_GENERATION_REFRESH_INTERVAL_MS)
      }
    }
  } catch (err) {
    generateError.value = err.message || t('finance.invoice.generate_error')
  } finally {
    generating.value = false
  }
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
      <h2>{{ t('finance.invoice.list_title') }}</h2>
      <button class="primary" :disabled="generating" @click="generateInvoices">
        {{ generating ? t('finance.bulk.submitting') : t('finance.invoice.generate') }}
      </button>
    </div>

    <div v-if="generateError" class="error" role="alert">{{ generateError }}</div>
    <output v-if="generateSuccess" class="success" aria-live="polite">{{ t('finance.invoice.generate_success') }}</output>

    <div v-if="loading" class="loading">{{ t('finance.invoice.loading') }}</div>
    <div v-else-if="error" class="error" role="alert">{{ error }}</div>
    <div v-else-if="invoices.length === 0" class="empty">{{ t('finance.invoice.no_invoices') }}</div>
    <table v-else class="invoices-table">
      <thead>
        <tr>
          <th>{{ t('finance.invoice.invoice_id') }}</th>
          <th>{{ t('finance.invoice.club_name') }}</th>
          <th>{{ t('finance.invoice.amount') }}</th>
          <th>{{ t('finance.invoice.status') }}</th>
          <th>{{ t('finance.invoice.created_at') }}</th>
          <th>{{ t('common.open') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="invoice in invoices" :key="invoice.invoice_id">
          <td class="mono">{{ invoice.invoice_id }}</td>
          <td>{{ invoice.club_name || '-' }}</td>
          <td>{{ invoice.amount?.toFixed(2) || '-' }}</td>
          <td>
            <span :class="`status-badge status-badge--${invoice.status}`">
              {{ invoice.status === 'paid' ? t('finance.invoice.status_paid') : t('finance.invoice.status_unpaid') }}
            </span>
          </td>
          <td>{{ invoice.created_at ? new Date(invoice.created_at).toLocaleDateString() : '-' }}</td>
          <td>
            <button class="link-button" @click="viewInvoice(invoice.invoice_id)">
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
  margin-bottom: 1.5rem;
}

h2 {
  margin: 0;
  color: var(--rd-text);
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

.primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error {
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
