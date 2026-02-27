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
const invoiceId = route.params.invoiceId

const invoice = ref(null)
const loading = ref(true)
const error = ref(null)
const marking = ref(false)
const markError = ref(null)
const markSuccess = ref(false)

const paymentReference = ref('')

async function loadInvoice() {
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
  marking.value = true
  markError.value = null
  markSuccess.value = false
  try {
    const payload = {
      payment_reference: paymentReference.value.trim() || undefined
    }
    invoice.value = await financeApi.markInvoicePaid(regattaId, invoiceId, payload)
    markSuccess.value = true
    setTimeout(() => {
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

      <form v-if="invoice.status === 'unpaid'" class="mark-paid-form" @submit.prevent="markAsPaid">
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
        <table>
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
.invoice-detail {
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

.invoice-info {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.5rem 1.5rem;
  margin-bottom: 2rem;
  padding: 1.5rem;
  background: #f8fbff;
  border: 1px solid #d7dee7;
  border-radius: 0.5rem;
}

.invoice-info dt {
  font-weight: 600;
  color: #34506f;
}

.invoice-info dd {
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

.mark-paid-form {
  padding: 1.5rem;
  border: 1px solid #d7dee7;
  border-radius: 0.5rem;
  background: #ffffff;
  margin-bottom: 2rem;
}

.mark-paid-form label {
  display: grid;
  gap: 0.4rem;
  margin-bottom: 1rem;
}

.mark-paid-form input {
  font: inherit;
  border: 1px solid #b5c4d5;
  border-radius: 0.5rem;
  padding: 0.6rem 0.75rem;
}

.mark-paid-form button {
  font: inherit;
  border: 1px solid #1d3557;
  border-radius: 0.5rem;
  padding: 0.6rem 1.5rem;
  cursor: pointer;
  background: #1d3557;
  color: #ffffff;
}

.mark-paid-form button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.entries-section {
  padding: 1.5rem;
  border: 1px solid #d7dee7;
  border-radius: 0.5rem;
  background: #ffffff;
}

.entries-section table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 1rem;
}

.entries-section th,
.entries-section td {
  text-align: left;
  padding: 0.5rem;
  border-bottom: 1px solid #d7dee7;
}

.entries-section th {
  font-weight: 600;
  color: #34506f;
  background: #f8fbff;
}
</style>
