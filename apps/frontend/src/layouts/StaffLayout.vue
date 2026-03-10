<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

const { t } = useI18n()
const route = useRoute()

const regattaId = computed(() => route.params.regattaId)
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
        <router-link
          to="/staff/regattas"
          class="staff-layout__nav-item"
          :aria-current="route.name === 'staff-regattas' ? 'page' : undefined"
        >
          {{ t('navigation.regattas') }}
        </router-link>
        <router-link
          to="/staff/rulesets"
          class="staff-layout__nav-item"
          :aria-current="route.name === 'staff-rulesets' ? 'page' : undefined"
        >
          {{ t('navigation.rulesets') }}
        </router-link>
      </nav>
    </header>

    <nav
      v-if="regattaId"
      class="staff-layout__subnav"
      aria-label="Regatta navigation"
    >
      <router-link
        :to="`/staff/regattas/${regattaId}`"
        class="staff-layout__subnav-item"
        :aria-current="route.name === 'staff-regatta-detail' ? 'page' : undefined"
      >
        {{ t('navigation.setup') }}
      </router-link>
      <router-link
        :to="`/staff/regattas/${regattaId}/draw`"
        class="staff-layout__subnav-item"
        :aria-current="route.name === 'staff-regatta-draw' ? 'page' : undefined"
      >
        {{ t('navigation.draw') }}
      </router-link>
      <router-link
        :to="`/staff/regattas/${regattaId}/adjudication`"
        class="staff-layout__subnav-item"
        :aria-current="route.name === 'staff-regatta-adjudication' ? 'page' : undefined"
      >
        {{ t('navigation.adjudication') }}
      </router-link>
      <router-link
        :to="`/staff/regattas/${regattaId}/finance`"
        class="staff-layout__subnav-item"
        :aria-current="route.name === 'staff-regatta-finance' ? 'page' : undefined"
      >
        {{ t('navigation.finance') }}
      </router-link>
      <router-link
        :to="`/staff/regattas/${regattaId}/blocks`"
        class="staff-layout__subnav-item"
        :aria-current="route.name === 'staff-blocks-management' ? 'page' : undefined"
      >
        {{ t('navigation.blocks') }}
      </router-link>
    </nav>
    
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

.staff-layout__subnav {
  background: var(--rd-surface);
  border-bottom: 1px solid var(--rd-border);
  padding: var(--rd-space-2) var(--rd-space-4);
  display: flex;
  gap: var(--rd-space-3);
}

.staff-layout__subnav-item {
  padding: var(--rd-space-1) var(--rd-space-3);
  text-decoration: none;
  color: var(--rd-text);
  border-radius: 4px;
  font-size: 0.875rem;
}

.staff-layout__subnav-item:hover {
  background: var(--rd-surface-2);
}

.staff-layout__subnav-item.router-link-exact-active {
  background: var(--rd-accent);
  color: white;
}

.staff-layout__main {
  flex: 1;
  padding: var(--rd-space-5);
}
</style>
