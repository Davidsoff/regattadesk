<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { resolveOperatorStation, resolveOperatorToken } from '../../operatorContext'

const { t } = useI18n()
const operatorToken = computed(() => resolveOperatorToken())
const operatorStation = computed(() => resolveOperatorStation())
const maskedOperatorToken = computed(() => {
  const token = operatorToken.value
  if (!token) {
    return ''
  }

  return token.length <= 4 ? `••••${token}` : `••••${token.slice(-4)}`
})
</script>

<template>
  <div class="operator-regattas-list">
    <h2>{{ t('operator.regattas.title') }}</h2>
    <p>{{ t('operator.regattas.description') }}</p>
    <p v-if="operatorToken" class="operator-regattas-list__status">
      {{ t('operator.regattas.auth_status', { token: maskedOperatorToken, station: operatorStation }) }}
    </p>
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

.operator-regattas-list__status {
  margin-top: var(--rd-space-3);
  font-weight: 600;
}
</style>
