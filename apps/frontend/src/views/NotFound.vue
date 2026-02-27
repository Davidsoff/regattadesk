<script setup>
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <div class="not-found">
    <h2>{{ t('errors.not_found.title') }}</h2>
    <p>{{ t('errors.not_found.description') }}</p>
    <button @click="router.push('/')">{{ t('common.go_home') }}</button>
  </div>
</template>

<style scoped>
.not-found {
  max-width: 600px;
  margin: 4rem auto;
  padding: var(--rd-space-5);
  text-align: center;
}

.not-found h2 {
  margin-bottom: var(--rd-space-3);
}

.not-found button {
  margin-top: var(--rd-space-4);
  padding: var(--rd-space-3) var(--rd-space-5);
  background: var(--rd-accent);
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 1rem;
}

.not-found button:hover {
  opacity: 0.9;
}
</style>
