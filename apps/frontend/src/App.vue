<script setup>
import { ref } from 'vue'
import FinanceBulkPaymentWorkflow from './components/FinanceBulkPaymentWorkflow.vue'

const regattaId = ref('')
const submittedRegattaId = ref('')

function applyRegattaId() {
  submittedRegattaId.value = regattaId.value.trim()
}
</script>

<template>
  <div v-if="!submittedRegattaId" style="max-width: 32rem; margin: 4rem auto; padding: 1rem">
    <label style="display: grid; gap: 0.5rem">
      <span>Regatta ID (UUID)</span>
      <input v-model="regattaId" type="text" placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" />
    </label>
    <button style="margin-top: 0.75rem" @click="applyRegattaId">Open</button>
  </div>
  <FinanceBulkPaymentWorkflow v-else :regatta-id="submittedRegattaId" />
</template>
