<script setup>
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import FinanceBulkPaymentWorkflow from '../../components/FinanceBulkPaymentWorkflow.vue'

const { t } = useI18n()
const route = useRoute()
</script>

<template>
  <div class="regatta-finance">
    <h2>{{ t('finance.title') }}</h2>
    <FinanceBulkPaymentWorkflow :regatta-id="route.params.regattaId" />
  </div>
</template>

<style scoped>
.regatta-finance h2 {
  margin-bottom: var(--rd-space-4);
}
</style>
