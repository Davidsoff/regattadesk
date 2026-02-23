<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FinanceBulkPaymentWorkflow from './components/FinanceBulkPaymentWorkflow.vue'

const { t } = useI18n()
const regattaId = ref('')
const submittedRegattaId = ref('')

function applyRegattaId() {
  submittedRegattaId.value = regattaId.value.trim()
}
</script>

<template>
  <div v-if="!submittedRegattaId" class="regatta-picker">
    <label class="regatta-picker__label">
      <span>{{ t('finance.bulk.regatta_id_label') }}</span>
      <input
        v-model="regattaId"
        type="text"
        :placeholder="t('finance.bulk.regatta_id_placeholder')"
      />
    </label>
    <button class="regatta-picker__open" @click="applyRegattaId">{{ t('common.open') }}</button>
  </div>
  <FinanceBulkPaymentWorkflow v-else :regatta-id="submittedRegattaId" />
</template>

<style scoped>
.regatta-picker {
  max-width: 32rem;
  margin: 4rem auto;
  padding: 1rem;
}

.regatta-picker__label {
  display: grid;
  gap: 0.5rem;
}

.regatta-picker__open {
  margin-top: 0.75rem;
}
</style>
