<script setup>
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import FinanceBulkPaymentWorkflow from '../../components/FinanceBulkPaymentWorkflow.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const regattaId = route.params.regattaId

function navigateToInvoices() {
  router.push({
    name: 'staff-regatta-finance-invoices',
    params: { regattaId }
  })
}
</script>

<template>
  <div class="regatta-finance">
    <h2>{{ t('finance.title') }}</h2>
    <nav class="finance-nav" aria-label="Finance sections">
      <button class="nav-button" @click="navigateToInvoices">
        {{ t('finance.navigation.invoices') }}
      </button>
    </nav>
    <FinanceBulkPaymentWorkflow :regatta-id="regattaId" />
  </div>
</template>

<style scoped>
.regatta-finance {
  padding: 1.5rem;
}

.regatta-finance h2 {
  margin-bottom: var(--rd-space-4);
}

.finance-nav {
  display: flex;
  gap: 1rem;
  margin-bottom: 2rem;
  padding: 1rem;
  background: #f8fbff;
  border: 1px solid #d7dee7;
  border-radius: 0.5rem;
}

.nav-button {
  font: inherit;
  border: 1px solid #1d3557;
  border-radius: 0.5rem;
  padding: 0.6rem 1.5rem;
  cursor: pointer;
  background: #ffffff;
  color: #1d3557;
}

.nav-button:hover {
  background: #1d3557;
  color: #ffffff;
}
</style>
