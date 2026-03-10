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
    <nav class="operator-regatta-detail__actions" aria-label="Regatta actions">
      <router-link
        :to="`/operator/regattas/${route.params.regattaId}/line-scan`"
        class="operator-regatta-detail__action"
      >
        {{ t('navigation.line_scan') }}
      </router-link>
    </nav>
  </div>
</template>

<style scoped>
.operator-regatta-detail h2 {
  margin-bottom: var(--rd-space-3);
  font-size: 1.75rem;
}

.operator-regatta-detail__actions {
  margin-top: var(--rd-space-5);
  display: flex;
  gap: var(--rd-space-4);
}

.operator-regatta-detail__action {
  display: inline-flex;
  align-items: center;
  padding: var(--rd-space-3) var(--rd-space-5);
  background: var(--rd-accent);
  color: white;
  text-decoration: none;
  border-radius: 4px;
  font-size: 1rem;
  font-weight: 600;
  min-height: var(--rd-hit-operator);
}

.operator-regatta-detail__action:hover {
  opacity: 0.9;
}
</style>
