<script setup>
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="operator-regattas-list">
    <h2>{{ t('operator.regattas.title') }}</h2>
    <p>{{ t('operator.regattas.description') }}</p>
    <p class="operator-regattas-list__hint">{{ t('operator.regattas.access_hint') }}</p>
  </div>
</template>

<style scoped>
.operator-regattas-list h2 {
  margin-bottom: var(--rd-space-3);
  font-size: 1.75rem;
}

.operator-regattas-list__hint {
  margin-top: var(--rd-space-4);
  color: var(--rd-text-muted);
  font-size: 0.9375rem;
}
</style>
