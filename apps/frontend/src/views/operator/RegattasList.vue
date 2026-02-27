<script setup>
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="operator-regattas-list">
    <h2>{{ t('operator.regattas.title') }}</h2>
    <p>{{ t('operator.regattas.description') }}</p>
  </div>
</template>

<style scoped>
.operator-regattas-list h2 {
  margin-bottom: var(--rd-space-3);
  font-size: 1.75rem;
}
</style>
