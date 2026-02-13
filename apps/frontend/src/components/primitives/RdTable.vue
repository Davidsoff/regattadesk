<template>
  <div class="rd-table-wrapper">
    <table 
      class="rd-table"
      :class="tableClasses"
    >
      <caption v-if="caption" class="rd-table-caption">
        {{ caption }}
      </caption>
      <thead class="rd-table-head">
        <slot name="header" />
      </thead>
      <tbody class="rd-table-body">
        <slot />
      </tbody>
    </table>
    <div v-if="isEmpty" class="rd-table-empty">
      <slot name="empty">
        <p class="rd-table-empty-text">{{ emptyText }}</p>
        <button 
          v-if="clearable"
          type="button"
          class="rd-table-clear-button"
          @click="$emit('clear')"
        >
          Clear filters
        </button>
      </slot>
    </div>
    <div v-if="loading" class="rd-table-loading">
      <slot name="loading">
        <div 
          v-for="n in skeletonRows" 
          :key="n" 
          class="rd-table-skeleton-row"
          role="presentation"
          aria-hidden="true"
        />
      </slot>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

/**
 * RdTable - Reusable table primitive for RegattaDesk
 * 
 * Features:
 * - Sticky header support
 * - Optional sticky first column
 * - Keyboard navigation (handled by consuming code)
 * - Empty and loading states
 * - Accessible table structure
 * 
 * Usage:
 * <RdTable caption="Race Results" sticky>
 *   <template #header>
 *     <tr>
 *       <th scope="col">Rank</th>
 *       <th scope="col" class="rd-align-right">Time</th>
 *     </tr>
 *   </template>
 *   <tr>
 *     <td>1</td>
 *     <td class="rd-tabular-nums rd-align-right">1:23.456</td>
 *   </tr>
 * </RdTable>
 */

const props = defineProps({
  /**
   * Table caption for accessibility
   */
  caption: {
    type: String,
    default: null,
  },
  /**
   * Enable sticky header
   */
  sticky: {
    type: Boolean,
    default: false,
  },
  /**
   * Enable sticky first column (typically bib/crew)
   */
  stickyFirstColumn: {
    type: Boolean,
    default: false,
  },
  /**
   * Show loading state
   */
  loading: {
    type: Boolean,
    default: false,
  },
  /**
   * Show empty state
   */
  isEmpty: {
    type: Boolean,
    default: false,
  },
  /**
   * Empty state text
   */
  emptyText: {
    type: String,
    default: 'No entries match filters',
  },
  /**
   * Show clear filters button in empty state
   */
  clearable: {
    type: Boolean,
    default: true,
  },
  /**
   * Number of skeleton rows to show when loading
   */
  skeletonRows: {
    type: Number,
    default: 5,
  },
});

defineEmits(['clear']);

const tableClasses = computed(() => ({
  'rd-table--sticky': props.sticky,
  'rd-table--sticky-first-column': props.stickyFirstColumn,
  'rd-table--loading': props.loading,
}));
</script>

<style scoped>
.rd-table-wrapper {
  position: relative;
  width: 100%;
  overflow-x: auto;
  background-color: var(--rd-bg);
}

.rd-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--rd-text-sm);
  color: var(--rd-text);
}

.rd-table-caption {
  text-align: left;
  font-size: var(--rd-text-base);
  font-weight: var(--rd-weight-semibold);
  padding: var(--rd-space-4);
  color: var(--rd-text);
}

.rd-table-head {
  background-color: var(--rd-surface);
  border-bottom: 2px solid var(--rd-border);
}

.rd-table-head :deep(th) {
  text-align: left;
  font-weight: var(--rd-weight-semibold);
  padding: var(--rd-space-3) var(--rd-space-4);
  white-space: nowrap;
  color: var(--rd-text);
  vertical-align: middle;
}

/* Sticky header */
.rd-table--sticky .rd-table-head :deep(th) {
  position: sticky;
  top: 0;
  z-index: 2;
  background-color: var(--rd-surface);
}

/* Sticky first column */
.rd-table--sticky-first-column :deep(th:first-child),
.rd-table--sticky-first-column :deep(td:first-child) {
  position: sticky;
  left: 0;
  z-index: 1;
  background-color: var(--rd-bg);
}

.rd-table--sticky-first-column.rd-table--sticky .rd-table-head :deep(th:first-child) {
  z-index: 3;
  background-color: var(--rd-surface);
}

.rd-table-body :deep(tr) {
  border-bottom: 1px solid var(--rd-border);
  transition: background-color var(--rd-transition-fast);
}

.rd-table-body :deep(tr:hover) {
  background-color: var(--rd-surface);
}

.rd-table-body :deep(tr:focus-visible) {
  outline: 2px solid var(--rd-focus);
  outline-offset: -2px;
}

.rd-table-body :deep(td) {
  padding: var(--rd-space-3) var(--rd-space-4);
  vertical-align: middle;
}

/* Alignment utilities (consumed via deep selector from parent) */
.rd-table :deep(.rd-align-right) {
  text-align: right;
}

.rd-table :deep(.rd-align-center) {
  text-align: center;
}

/* Empty state */
.rd-table-empty {
  padding: var(--rd-space-6);
  text-align: center;
  background-color: var(--rd-surface);
  border: 1px solid var(--rd-border);
  border-radius: var(--rd-border-radius);
  margin-top: var(--rd-space-4);
}

.rd-table-empty-text {
  margin: 0 0 var(--rd-space-4);
  color: var(--rd-text-muted);
}

.rd-table-clear-button {
  min-height: var(--rd-hit);
  padding: var(--rd-space-2) var(--rd-space-4);
  background-color: var(--rd-accent);
  color: white;
  border: none;
  border-radius: var(--rd-border-radius);
  font-size: var(--rd-text-sm);
  font-weight: var(--rd-weight-medium);
  cursor: pointer;
  transition: opacity var(--rd-transition-fast);
}

.rd-table-clear-button:hover {
  opacity: 0.9;
}

/* Loading state */
.rd-table-loading {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(255, 255, 255, 0.8);
  display: flex;
  flex-direction: column;
  gap: var(--rd-space-2);
  padding: var(--rd-space-4);
}

.rd-table-skeleton-row {
  height: 44px;
  background: linear-gradient(
    90deg,
    var(--rd-surface) 0%,
    var(--rd-surface-2) 50%,
    var(--rd-surface) 100%
  );
  background-size: 200% 100%;
  animation: skeleton-pulse 1.5s ease-in-out infinite;
  border-radius: var(--rd-border-radius);
}

@keyframes skeleton-pulse {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

/* High contrast mode adjustments */
:root[data-contrast="high"] .rd-table-head {
  border-bottom-width: 3px;
}

:root[data-contrast="high"] .rd-table-body :deep(tr) {
  border-bottom-width: 2px;
}
</style>
