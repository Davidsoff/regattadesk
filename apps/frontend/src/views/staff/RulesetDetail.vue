<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const rulesetId = computed(() => route.params.rulesetId)
const isCreateMode = computed(() => !rulesetId.value)
</script>

<template>
  <div class="staff-ruleset-detail">
    <h1>{{ isCreateMode ? 'Create Ruleset' : 'Ruleset Detail' }}</h1>
    <p v-if="isCreateMode">Ruleset creation form will be implemented in follow-up work.</p>
    <p v-else>Ruleset ID: {{ rulesetId }}</p>
  </div>
</template>

<style scoped>
.staff-ruleset-detail {
  padding: var(--rd-space-4);
}

h1 {
  margin: 0 0 var(--rd-space-3) 0;
  font-size: var(--rd-text-xl);
  font-weight: var(--rd-weight-semibold);
}

p {
  margin: 0;
}
</style>
