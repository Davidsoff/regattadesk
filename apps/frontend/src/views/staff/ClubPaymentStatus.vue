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
const clubId = validateRouteParam(route.params.clubId, 'clubId')
const hasValidRouteParams = Boolean(regattaId && clubId)

const club = ref(null)
const loading = ref(true)
const error = ref(null)
const updating = ref(false)
const updateError = ref(null)
const updateSuccess = ref(false)
const updatedEntriesCount = ref(0)

const paymentStatus = ref('paid')
const paymentReference = ref('')
let successMessageTimeoutId = null

function clearSuccessMessageTimeout() {
  if (successMessageTimeoutId !== null) {
    clearTimeout(successMessageTimeoutId)
    successMessageTimeoutId = null
  }
}

async function loadClub() {
  if (!hasValidRouteParams) {
    error.value = t('finance.invalid_route_params')
    loading.value = false
    return
  }

  loading.value = true
  error.value = null
  try {
    club.value = await financeApi.getClubPaymentStatus(regattaId, clubId)
  } catch (err) {
    error.value = err.message || t('finance.club.not_found')
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
    const result = await financeApi.updateClubPaymentStatus(regattaId, clubId, payload)
    const updatedEntries = Number(result?.updated_entries ?? result?.updated_count ?? 0)
    updatedEntriesCount.value = Number.isFinite(updatedEntries) ? updatedEntries : 0
    updateSuccess.value = true
    await loadClub()
    clearSuccessMessageTimeout()
    successMessageTimeoutId = setTimeout(() => {
      updateSuccess.value = false
    }, SUCCESS_MESSAGE_DURATION_MS)
  } catch (err) {
    updateError.value = err.message || t('finance.club.update_error')
  } finally {
    updating.value = false
  }
}

onMounted(() => {
  loadClub()
})

onUnmounted(() => {
  clearSuccessMessageTimeout()
})
</script>

<template>
  <div class="club-payment-status">
    <h2>{{ t('finance.club.title') }}</h2>

    <div v-if="loading" class="loading">{{ t('finance.club.loading') }}</div>
    <div v-else-if="error" class="error" role="alert">{{ error }}</div>
    <div v-else-if="club" class="content">
      <dl class="club-info">
        <dt>{{ t('finance.club.club_id') }}</dt>
        <dd class="mono">{{ clubId }}</dd>

        <dt>{{ t('finance.club.total_entries') }}</dt>
        <dd>{{ club.total_entries }}</dd>

        <dt>{{ t('finance.club.paid_entries') }}</dt>
        <dd>{{ club.paid_entries }}</dd>

        <dt>{{ t('finance.club.unpaid_entries') }}</dt>
        <dd>{{ club.unpaid_entries }}</dd>
      </dl>

      <form class="finance-form update-form" @submit.prevent="updateStatus">
        <h3>{{ t('finance.club.update_all') }}</h3>

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
        <output v-if="updateSuccess" class="success" aria-live="polite">
          {{ t('finance.club.update_success', { count: updatedEntriesCount }) }}
        </output>

        <button type="submit" class="primary" :disabled="updating">
          {{ updating ? t('finance.bulk.submitting') : t('common.submit') }}
        </button>
      </form>

      <section v-if="club.entries && club.entries.length > 0" class="entries-list">
        <h3>{{ t('finance.club.entries_list') }}</h3>
        <table class="finance-table">
          <thead>
            <tr>
              <th>{{ t('finance.entry.entry_id') }}</th>
              <th>{{ t('finance.entry.payment_status') }}</th>
              <th>{{ t('finance.entry.payment_reference') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="entry in club.entries" :key="entry.entry_id">
              <td class="mono">{{ entry.entry_id }}</td>
              <td>
                <span :class="`status-badge status-badge--${entry.payment_status}`">
                  {{ entry.payment_status === 'paid' ? t('finance.bulk.status_paid') : t('finance.bulk.status_unpaid') }}
                </span>
              </td>
              <td>{{ entry.payment_reference || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </div>
  </div>
</template>

<style scoped>
@import './financeViewShared.css';

.club-payment-status {
  max-width: 56rem;
  margin: 2rem auto;
  padding: 1.5rem;
}

.club-info {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.5rem 1.5rem;
  margin-bottom: 2rem;
  padding: 1.5rem;
  background: var(--rd-surface);
  border: 1px solid var(--rd-border);
  border-radius: 0.5rem;
}

.club-info dt {
  font-weight: 600;
  color: var(--rd-text-muted);
}

.club-info dd {
  margin: 0;
  color: var(--rd-text);
}

.update-form {
  margin-bottom: 2rem;
}

.entries-list {
  padding: 1.5rem;
  border: 1px solid var(--rd-border);
  border-radius: 0.5rem;
  background: var(--rd-bg);
}
</style>
