<script setup>
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { useLocale } from '../composables/useLocale'

const { t } = useI18n()
const route = useRoute()
const { currentLocale, supportedLocales, switchLocale, getLocaleName } = useLocale()
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
          :to="{
            path: `/public/v${route.params.drawRevision}-${route.params.resultsRevision}/schedule`,
            query: route.query,
          }"
          class="public-layout__nav-item"
        >
          {{ t('navigation.schedule') }}
        </router-link>
        <router-link 
          :to="{
            path: `/public/v${route.params.drawRevision}-${route.params.resultsRevision}/results`,
            query: route.query,
          }"
          class="public-layout__nav-item"
        >
          {{ t('navigation.results') }}
        </router-link>
      </nav>

      <fieldset class="public-layout__locale" role="group">
        <legend class="rd-sr-only">Language</legend>
        <button
          v-for="locale in supportedLocales"
          :key="locale"
          class="public-layout__locale-btn"
          :aria-pressed="currentLocale === locale"
          @click="switchLocale(locale)"
        >
          {{ getLocaleName(locale) }}
        </button>
      </fieldset>
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
  gap: var(--rd-space-4);
}

.public-layout__brand {
  flex: 1;
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

.public-layout__locale {
  border: 0;
  margin: 0;
  min-inline-size: 0;
  padding: 0;
  display: flex;
  gap: var(--rd-space-2);
}

.public-layout__locale-btn {
  padding: var(--rd-space-1) var(--rd-space-3);
  border: 1px solid var(--rd-border);
  border-radius: 4px;
  background: transparent;
  color: var(--rd-text);
  cursor: pointer;
  font: inherit;
  font-size: 0.875rem;
}

.public-layout__locale-btn[aria-pressed="true"] {
  background: var(--rd-accent);
  color: white;
  border-color: var(--rd-accent);
}

.public-layout__locale-btn:hover:not([aria-pressed="true"]) {
  background: var(--rd-surface-2);
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
