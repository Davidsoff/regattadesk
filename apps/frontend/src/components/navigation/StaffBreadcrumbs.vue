<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { getStaffBreadcrumbs } from '../../navigation/staffNavigation.js'

const route = useRoute()
const { t } = useI18n()

const breadcrumbs = computed(() => getStaffBreadcrumbs(route, t))
</script>

<template>
  <nav
    v-if="breadcrumbs.length > 0"
    aria-label="Breadcrumb"
    class="staff-breadcrumbs"
    data-testid="staff-breadcrumbs"
  >
    <ol class="staff-breadcrumbs__list">
      <li
        v-for="breadcrumb in breadcrumbs"
        :key="`${breadcrumb.label}-${breadcrumb.to ?? 'current'}`"
        class="staff-breadcrumbs__item"
      >
        <router-link v-if="breadcrumb.to" :to="breadcrumb.to">
          {{ breadcrumb.label }}
        </router-link>
        <span v-else aria-current="page">{{ breadcrumb.label }}</span>
      </li>
    </ol>
  </nav>
</template>

<style scoped>
.staff-breadcrumbs {
  margin-bottom: var(--rd-space-4);
}

.staff-breadcrumbs__list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--rd-space-2);
  list-style: none;
  margin: 0;
  padding: 0;
  color: var(--rd-text-muted);
  font-size: 0.875rem;
}

.staff-breadcrumbs__item {
  display: inline-flex;
  align-items: center;
  gap: var(--rd-space-2);
}

.staff-breadcrumbs__item:not(:last-child)::after {
  content: '/';
}

.staff-breadcrumbs__item a {
  color: inherit;
  text-decoration: none;
}
</style>
