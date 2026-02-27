<script setup>
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="operator-layout" data-contrast="high">
    <a href="#main-content" class="skip-link">{{ t('common.skip_to_content') }}</a>
    
    <header class="operator-layout__header">
      <div class="operator-layout__brand">
        <h1>RegattaDesk</h1>
        <span class="operator-layout__surface-label">{{ t('common.operator') }}</span>
      </div>
    </header>
    
    <main id="main-content" class="operator-layout__main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.operator-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--rd-bg);
}

.skip-link {
  position: absolute;
  top: -40px;
  left: 0;
  background: var(--rd-accent);
  color: white;
  padding: var(--rd-space-3);
  text-decoration: none;
  z-index: 100;
  min-height: var(--rd-hit-operator);
  display: flex;
  align-items: center;
}

.skip-link:focus {
  top: 0;
}

.operator-layout__header {
  background: var(--rd-surface);
  border-bottom: 2px solid var(--rd-border);
  padding: var(--rd-space-4);
  min-height: var(--rd-hit-operator);
  display: flex;
  align-items: center;
}

.operator-layout__brand {
  display: flex;
  align-items: center;
  gap: var(--rd-space-3);
}

.operator-layout__brand h1 {
  font-size: 1.5rem;
  font-weight: 700;
  margin: 0;
}

.operator-layout__surface-label {
  font-size: 1rem;
  color: var(--rd-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
}

.operator-layout__main {
  flex: 1;
  padding: var(--rd-space-4);
}
</style>
