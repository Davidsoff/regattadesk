<script setup>
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { createApiClient, createFinanceApi } from '../../api'
import FinanceBulkPaymentWorkflow from '../../components/FinanceBulkPaymentWorkflow.vue'
import { validateRouteParam } from './financeViewShared'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const financeApi = createFinanceApi(createApiClient())

const regattaId = validateRouteParam(route.params.regattaId, 'regattaId')
const hasValidRouteParams = Boolean(regattaId)
const search = ref('')
const paymentStatus = ref('')
const entries = ref([])
const clubs = ref([])
const loading = ref(true)
const loadError = ref('')

async function loadFinanceHome() {
  if (!hasValidRouteParams) {
    loadError.value = t('finance.invalid_route_params')
    loading.value = false
    entries.value = []
    clubs.value = []
    return
  }

  loading.value = true
  loadError.value = ''

  try {
    const baseFilters = {
      search: search.value
    }
    const entryFilters =
      paymentStatus.value === 'partial'
        ? baseFilters
        : {
            ...baseFilters,
            payment_status: paymentStatus.value
          }
    const clubFilters = {
      ...baseFilters,
      payment_status: paymentStatus.value
    }
    const [entryResponse, clubResponse] = await Promise.all([
      financeApi.listFinanceEntries(regattaId, entryFilters),
      financeApi.listFinanceClubs(regattaId, clubFilters)
    ])
    entries.value = entryResponse?.entries ?? []
    clubs.value = clubResponse?.clubs ?? []
  } catch (error) {
    loadError.value = error?.message || t('finance.home.load_error')
  } finally {
    loading.value = false
  }
}

function navigateToInvoices() {
  router.push({
    name: 'staff-regatta-finance-invoices',
    params: { regattaId }
  })
}

onMounted(() => {
  loadFinanceHome()
})
</script>

<template>
  <div class="regatta-finance">
    <h2>{{ t('finance.title') }}</h2>

    <p v-if="loadError" class="error" role="alert">{{ loadError }}</p>
    <template v-if="hasValidRouteParams">
      <nav class="finance-nav" aria-label="Finance sections">
        <a class="nav-chip" href="#finance-entries">{{ t('finance.navigation.entries') }}</a>
        <a class="nav-chip" href="#finance-clubs">{{ t('finance.navigation.clubs') }}</a>
        <a class="nav-chip" href="#finance-bulk">{{ t('finance.navigation.bulk') }}</a>
        <button class="nav-button" @click="navigateToInvoices">
          {{ t('finance.navigation.invoices') }}
        </button>
      </nav>

      <form class="finance-filters" @submit.prevent="loadFinanceHome">
        <label>
          <span>{{ t('finance.home.search_label') }}</span>
          <input v-model="search" type="search" name="finance_search" />
        </label>

        <label>
          <span>{{ t('finance.home.payment_status_label') }}</span>
          <select v-model="paymentStatus" name="payment_status_filter">
            <option value="">{{ t('finance.home.all_statuses') }}</option>
            <option value="paid">{{ t('finance.home.status_paid') }}</option>
            <option value="unpaid">{{ t('finance.home.status_unpaid') }}</option>
            <option value="partial">{{ t('finance.home.status_partial') }}</option>
          </select>
        </label>

        <button type="submit" class="nav-button">{{ t('common.search') }}</button>
      </form>

      <div v-if="loading" class="loading">{{ t('common.loading') }}</div>

      <div v-else class="finance-grid">
        <section id="finance-entries" class="finance-panel">
          <div class="panel-header">
            <h3>{{ t('finance.home.entries_heading') }}</h3>
          </div>

          <table v-if="entries.length" class="finance-table">
            <thead>
              <tr>
                <th>{{ t('finance.entry.entry_id') }}</th>
                <th>{{ t('entry.crew') }}</th>
                <th>{{ t('finance.invoice.club_name') }}</th>
                <th>{{ t('finance.entry.payment_status') }}</th>
                <th>{{ t('common.open') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="entry in entries" :key="entry.entry_id">
                <td class="mono">{{ entry.entry_id }}</td>
                <td>{{ entry.crew_name }}</td>
                <td>{{ entry.club_name }}</td>
                <td>{{ entry.payment_status }}</td>
                <td>
                  <router-link
                    class="detail-link"
                    :to="{
                      name: 'staff-regatta-finance-entry',
                      params: { regattaId, entryId: entry.entry_id }
                    }"
                  >
                    {{ t('finance.home.view_entry') }}
                  </router-link>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-else>{{ t('finance.home.entry_empty') }}</p>
        </section>

        <section id="finance-clubs" class="finance-panel">
          <div class="panel-header">
            <h3>{{ t('finance.home.clubs_heading') }}</h3>
          </div>

          <table v-if="clubs.length" class="finance-table">
            <thead>
              <tr>
                <th>{{ t('finance.club.club_id') }}</th>
                <th>{{ t('finance.invoice.club_name') }}</th>
                <th>{{ t('finance.entry.payment_status') }}</th>
                <th>{{ t('finance.club.paid_entries') }}</th>
                <th>{{ t('finance.club.unpaid_entries') }}</th>
                <th>{{ t('common.open') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="club in clubs" :key="club.club_id">
                <td class="mono">{{ club.club_id }}</td>
                <td>{{ club.club_name }}</td>
                <td>{{ club.payment_status }}</td>
                <td>{{ club.paid_entries }}</td>
                <td>{{ club.unpaid_entries }}</td>
                <td>
                  <router-link
                    class="detail-link"
                    :to="{
                      name: 'staff-regatta-finance-club',
                      params: { regattaId, clubId: club.club_id }
                    }"
                  >
                    {{ t('finance.home.view_club') }}
                  </router-link>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-else>{{ t('finance.home.club_empty') }}</p>
        </section>
      </div>

      <section id="finance-bulk" class="finance-panel finance-panel--bulk">
        <div class="panel-header">
          <h3>{{ t('finance.home.bulk_heading') }}</h3>
        </div>
        <FinanceBulkPaymentWorkflow :regatta-id="regattaId" />
      </section>

      <section class="finance-panel">
        <div class="panel-header">
          <h3>{{ t('finance.home.invoices_heading') }}</h3>
        </div>
        <p>{{ t('finance.invoice.no_invoices') }}</p>
        <button class="nav-button" @click="navigateToInvoices">
          {{ t('finance.navigation.invoices') }}
        </button>
      </section>
    </template>
  </div>
</template>

<style scoped>
@import './financeViewShared.css';

.regatta-finance {
  display: grid;
  gap: 1.5rem;
  padding: 1.5rem;
}

.regatta-finance h2 {
  margin: 0;
}

.finance-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: center;
  padding: 1rem;
  background: var(--rd-surface);
  border: 1px solid var(--rd-border);
  border-radius: 0.5rem;
}

.nav-chip {
  color: inherit;
  text-decoration: none;
  padding: 0.4rem 0.8rem;
  border: 1px solid var(--rd-border);
  border-radius: 999px;
  background: var(--rd-bg);
}

.nav-button {
  font: inherit;
  border: 1px solid var(--rd-text);
  border-radius: 0.5rem;
  padding: 0.6rem 1rem;
  cursor: pointer;
  background: var(--rd-bg);
  color: var(--rd-text);
}

.finance-filters {
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
  padding: 1rem;
  border: 1px solid var(--rd-border);
  border-radius: 0.5rem;
  background: var(--rd-surface);
}

.finance-filters label {
  display: grid;
  gap: 0.35rem;
}

.finance-filters input,
.finance-filters select {
  font: inherit;
  padding: 0.65rem 0.75rem;
  border: 1px solid var(--rd-border);
  border-radius: 0.5rem;
}

.finance-grid {
  display: grid;
  gap: 1.5rem;
  grid-template-columns: repeat(auto-fit, minmax(20rem, 1fr));
}

.finance-panel {
  padding: 1rem;
  border: 1px solid var(--rd-border);
  border-radius: 0.75rem;
  background: var(--rd-surface);
}

.finance-panel--bulk {
  padding: 0;
  overflow: hidden;
}

.panel-header {
  margin-bottom: 1rem;
}

.detail-link {
  color: var(--rd-text);
}

.loading,
.error {
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
}

.loading {
  background: var(--rd-surface);
}

.error {
  color: #8f1d1d;
  background: #ffe8e8;
}
</style>
