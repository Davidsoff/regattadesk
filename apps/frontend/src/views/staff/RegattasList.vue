<script setup>
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="regattas-list">
    <h2>{{ t('staff.regattas.title') }}</h2>
    <p>{{ t('staff.regattas.description') }}</p>
  </div>
</template>

<style scoped>
.regattas-list h2 {
  margin-bottom: var(--rd-space-3);
}
</style>
