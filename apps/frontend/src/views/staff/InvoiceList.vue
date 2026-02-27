<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { createApiClient, createFinanceApi } from '../../api'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const apiClient = createApiClient()
const financeApi = createFinanceApi(apiClient)

const SUCCESS_MESSAGE_DURATION_MS = 3000
const INVOICE_GENERATION_RELOAD_DELAY_MS = 2000

const regattaId = route.params.regattaId

const invoices = ref([])
const loading = ref(true)
const error = ref(null)
const generating = ref(false)
const generateError = ref(null)
const generateSuccess = ref(false)

async function loadInvoices() {
  loading.value = true
  error.value = null
  try {
    const result = await financeApi.listInvoices(regattaId)
    invoices.value = result.invoices || []
  } catch (err) {
    error.value = err.message || t('common.error')
  } finally {
    loading.value = false
  }
}

async function generateInvoices() {
  generating.value = true
  generateError.value = null
  generateSuccess.value = false
  try {
    await financeApi.generateInvoices(regattaId)
    generateSuccess.value = true
    setTimeout(async () => {
      generateSuccess.value = false
      await loadInvoices()
    }, INVOICE_GENERATION_RELOAD_DELAY_MS)
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
    <div v-if="generateSuccess" class="success" role="status">{{ t('finance.invoice.generate_success') }}</div>

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
  color: #1d3557;
}

.primary {
  font: inherit;
  border: 1px solid #1d3557;
  border-radius: 0.5rem;
  padding: 0.6rem 1.5rem;
  cursor: pointer;
  background: #1d3557;
  color: #ffffff;
}

.primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.loading,
.error,
.empty {
  padding: 1rem;
  border-radius: 0.5rem;
}

.loading,
.empty {
  background: #f0f4f8;
  color: #34506f;
}

.error {
  background: #fce4e8;
  color: #8b2531;
  margin-bottom: 1rem;
}

.success {
  padding: 1rem;
  border-radius: 0.5rem;
  background: #dff5e6;
  color: #0a6d35;
  margin-bottom: 1rem;
}

.invoices-table {
  width: 100%;
  border-collapse: collapse;
  background: #ffffff;
  border: 1px solid #d7dee7;
  border-radius: 0.5rem;
  overflow: hidden;
}

.invoices-table th,
.invoices-table td {
  text-align: left;
  padding: 0.75rem;
  border-bottom: 1px solid #d7dee7;
}

.invoices-table th {
  font-weight: 600;
  color: #34506f;
  background: #f8fbff;
}

.invoices-table tbody tr:last-child td {
  border-bottom: none;
}

.invoices-table tbody tr:hover {
  background: #f8fbff;
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

.link-button {
  background: none;
  border: none;
  color: #1d3557;
  text-decoration: underline;
  cursor: pointer;
  font: inherit;
  padding: 0;
}

.link-button:hover {
  color: #457b9d;
}
</style>
