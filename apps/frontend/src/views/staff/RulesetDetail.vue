<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const isCreateMode = computed(() => route.name === 'staff-ruleset-create')
const rulesetId = computed(() => route.params.rulesetId || '')

function goBack() {
  router.push({ name: 'staff-rulesets' })
}
</script>

<template>
  <section class="ruleset-detail" aria-label="Ruleset detail">
    <header class="page-header">
      <h1>{{ isCreateMode ? t('rulesets.createButton') : t('rulesets.title') }}</h1>
      <button type="button" class="btn-secondary" @click="goBack">
        {{ t('common.back') }}
      </button>
    </header>

    <div class="content-card">
      <p v-if="isCreateMode">{{ t('rulesets.emptyState') }}</p>
      <p v-else>Ruleset ID: {{ rulesetId }}</p>
    </div>
  </section>
</template>

<style scoped>
.ruleset-detail {
  padding: var(--rd-space-4);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--rd-space-4);
}

.page-header h1 {
  margin: 0;
  font-size: var(--rd-text-xl);
  font-weight: var(--rd-weight-semibold);
}

.content-card {
  background: var(--rd-surface);
  border: 1px solid var(--rd-border);
  border-radius: 4px;
  padding: var(--rd-space-4);
}

.btn-secondary {
  border: 1px solid var(--rd-border);
  border-radius: 4px;
  padding: var(--rd-space-2) var(--rd-space-3);
  background: transparent;
  color: var(--rd-text);
  cursor: pointer;
  min-height: var(--rd-hit-sm);
}

.btn-secondary:focus {
  outline: 2px solid var(--rd-focus);
  outline-offset: 2px;
}
</style>
