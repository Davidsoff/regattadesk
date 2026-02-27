<script setup>
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

const { t } = useI18n()
const route = useRoute()
</script>

<template>
  <div class="public-results">
    <h2>{{ t('public.results.title') }}</h2>
    <p>{{ t('public.results.description') }}</p>
    <p class="version-info">
      {{ t('public.version.draw') }}: {{ route.params.drawRevision }},
      {{ t('public.version.results') }}: {{ route.params.resultsRevision }}
    </p>
  </div>
</template>

<style scoped>
.public-results h2 {
  margin-bottom: var(--rd-space-3);
}

.version-info {
  margin-top: var(--rd-space-4);
  font-size: 0.875rem;
  color: var(--rd-text-muted);
}
</style>
