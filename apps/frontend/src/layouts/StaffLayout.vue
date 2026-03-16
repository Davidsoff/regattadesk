<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import StaffBreadcrumbs from '../components/navigation/StaffBreadcrumbs.vue'
import { STAFF_PRIMARY_NAV_ITEMS, getStaffRegattaNavItems } from '../navigation/staffNavigation.js'

const { t } = useI18n()
const route = useRoute()

const regattaId = computed(() => route.params.regattaId)
const primaryNavItems = STAFF_PRIMARY_NAV_ITEMS
const regattaNavItems = computed(() => getStaffRegattaNavItems(regattaId.value))

function isRouteActive(navItem) {
  return navItem.routeNames.includes(String(route.name))
}

function getNavigationLabel(navItem) {
  switch (navItem.key) {
    case 'regattas':
      return t('navigation.regattas')
    case 'rulesets':
      return t('navigation.rulesets')
    case 'setup':
      return t('navigation.setup')
    case 'draw':
      return t('navigation.draw')
    case 'finance':
      return t('navigation.finance')
    case 'operator_access':
      return t('navigation.operator_access')
    case 'blocks':
      return t('navigation.blocks')
    default:
      return navItem.key
  }
}
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
          v-for="navItem in primaryNavItems"
          :key="navItem.key"
          :to="navItem.to"
          class="staff-layout__nav-item"
          :aria-current="isRouteActive(navItem) ? 'page' : undefined"
        >
          {{ getNavigationLabel(navItem) }}
        </router-link>
      </nav>
    </header>

    <nav
      v-if="regattaId"
      class="staff-layout__subnav"
      aria-label="Regatta navigation"
    >
      <router-link
        v-for="navItem in regattaNavItems"
        :key="navItem.key"
        :to="navItem.to"
        class="staff-layout__subnav-item"
        :aria-current="isRouteActive(navItem) ? 'page' : undefined"
      >
        {{ getNavigationLabel(navItem) }}
      </router-link>
      <router-link
        :to="`/staff/regattas/${regattaId}/printables`"
        class="staff-layout__subnav-item"
        :aria-current="route.name === 'staff-regatta-printables' ? 'page' : undefined"
      >
        {{ t('navigation.printables') }}
      </router-link>
    </nav>
    
    <main id="main-content" class="staff-layout__main">
      <StaffBreadcrumbs />
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

.staff-layout__nav-item[aria-current='page'] {
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

.staff-layout__subnav-item[aria-current='page'] {
  background: var(--rd-accent);
  color: white;
}

.staff-layout__main {
  flex: 1;
  padding: var(--rd-space-5);
}
</style>
