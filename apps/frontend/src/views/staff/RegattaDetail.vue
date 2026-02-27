<script setup>
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

const { t } = useI18n()
const route = useRoute()
</script>

<template>
  <div class="regatta-detail">
    <h2>{{ t('staff.regatta.title') }}</h2>
    <p>{{ t('staff.regatta.id') }}: {{ route.params.regattaId }}</p>
  </div>
</template>

<style scoped>
.regatta-detail h2 {
  margin-bottom: var(--rd-space-3);
}
</style>
