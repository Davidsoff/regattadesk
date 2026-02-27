<script setup>
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

const { t } = useI18n()
const route = useRoute()
</script>

<template>
  <div class="operator-regatta-detail">
    <h2>{{ t('operator.regatta.title') }}</h2>
    <p>{{ t('operator.regatta.id') }}: {{ route.params.regattaId }}</p>
  </div>
</template>

<style scoped>
.operator-regatta-detail h2 {
  margin-bottom: var(--rd-space-3);
  font-size: 1.75rem;
}
</style>
