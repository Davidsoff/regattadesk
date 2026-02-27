<script setup>
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <div class="staff-layout">
    <a href="#main-content" class="skip-link">{{ t('common.skip_to_content') }}</a>
    
    <header class="staff-layout__header">
      <div class="staff-layout__brand">
        <h1>RegattaDesk</h1>
        <span class="staff-layout__surface-label">{{ t('common.staff') }}</span>
      </div>
      
      <nav class="staff-layout__nav" aria-label="Primary navigation">
        <router-link to="/staff/regattas" class="staff-layout__nav-item">
          {{ t('navigation.regattas') }}
        </router-link>
      </nav>
    </header>
    
    <main id="main-content" class="staff-layout__main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.staff-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.skip-link {
  position: absolute;
  top: -40px;
  left: 0;
  background: var(--rd-accent);
  color: white;
  padding: var(--rd-space-2) var(--rd-space-4);
  text-decoration: none;
  z-index: 100;
}

.skip-link:focus {
  top: 0;
}

.staff-layout__header {
  background: var(--rd-surface);
  border-bottom: 1px solid var(--rd-border);
  padding: var(--rd-space-3) var(--rd-space-4);
  display: flex;
  align-items: center;
  gap: var(--rd-space-6);
}

.staff-layout__brand {
  display: flex;
  align-items: center;
  gap: var(--rd-space-3);
}

.staff-layout__brand h1 {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0;
}

.staff-layout__surface-label {
  font-size: 0.875rem;
  color: var(--rd-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.staff-layout__nav {
  display: flex;
  gap: var(--rd-space-4);
}

.staff-layout__nav-item {
  padding: var(--rd-space-2) var(--rd-space-3);
  text-decoration: none;
  color: var(--rd-text);
  border-radius: 4px;
}

.staff-layout__nav-item:hover {
  background: var(--rd-surface-2);
}

.staff-layout__nav-item.router-link-active {
  background: var(--rd-accent);
  color: white;
}

.staff-layout__main {
  flex: 1;
  padding: var(--rd-space-5);
}
</style>
