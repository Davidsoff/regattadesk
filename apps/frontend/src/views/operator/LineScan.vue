<script setup>
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

const { t } = useI18n()
const route = useRoute()
</script>

<template>
  <div class="operator-line-scan">
    <h2>{{ t('operator.line_scan.title') }}</h2>
    <p>{{ t('operator.line_scan.description') }}</p>
    <p>{{ t('operator.regatta.id') }}: {{ route.params.regattaId }}</p>
  </div>
</template>

<style scoped>
.operator-line-scan h2 {
  margin-bottom: var(--rd-space-3);
  font-size: 1.75rem;
}
</style>
