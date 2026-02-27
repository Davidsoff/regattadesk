<script setup>
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

const { t } = useI18n()
const route = useRoute()
</script>

<template>
  <div class="public-layout">
    <a href="#main-content" class="skip-link">{{ t('common.skip_to_content') }}</a>
    
    <header class="public-layout__header">
      <div class="public-layout__brand">
        <h1>RegattaDesk</h1>
      </div>
      
      <nav class="public-layout__nav" aria-label="Primary navigation">
        <router-link 
          :to="`/public/v${route.params.drawRevision}-${route.params.resultsRevision}/schedule`"
          class="public-layout__nav-item"
        >
          {{ t('navigation.schedule') }}
        </router-link>
        <router-link 
          :to="`/public/v${route.params.drawRevision}-${route.params.resultsRevision}/results`"
          class="public-layout__nav-item"
        >
          {{ t('navigation.results') }}
        </router-link>
      </nav>
    </header>
    
    <main id="main-content" class="public-layout__main">
      <router-view />
    </main>
    
    <footer class="public-layout__footer">
      <p>{{ t('common.powered_by_regattadesk') }}</p>
    </footer>
  </div>
</template>

<style scoped>
.public-layout {
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

.public-layout__header {
  background: var(--rd-surface);
  border-bottom: 1px solid var(--rd-border);
  padding: var(--rd-space-3) var(--rd-space-4);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.public-layout__brand h1 {
  font-size: 1.5rem;
  font-weight: 700;
  margin: 0;
}

.public-layout__nav {
  display: flex;
  gap: var(--rd-space-4);
}

.public-layout__nav-item {
  padding: var(--rd-space-2) var(--rd-space-4);
  text-decoration: none;
  color: var(--rd-text);
  border-radius: 4px;
  font-weight: 500;
}

.public-layout__nav-item:hover {
  background: var(--rd-surface-2);
}

.public-layout__nav-item.router-link-active {
  background: var(--rd-accent);
  color: white;
}

.public-layout__main {
  flex: 1;
  padding: var(--rd-space-5);
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
}

.public-layout__footer {
  background: var(--rd-surface);
  border-top: 1px solid var(--rd-border);
  padding: var(--rd-space-4);
  text-align: center;
  color: var(--rd-text-muted);
  font-size: 0.875rem;
}
</style>
