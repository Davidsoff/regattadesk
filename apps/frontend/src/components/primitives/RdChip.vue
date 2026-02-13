<template>
  <span 
    class="rd-chip"
    :class="chipClasses"
    :role="role"
    :aria-label="ariaLabel"
  >
    <span v-if="icon" class="rd-chip-icon" aria-hidden="true">
      {{ icon }}
    </span>
    <span class="rd-chip-label">{{ label }}</span>
    <span v-if="count !== null" class="rd-chip-count">{{ count }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue';

/**
 * RdChip - Status chip/badge component
 * 
 * Displays status indicators with semantic styling.
 * Follows design principle: label + optional icon + optional count, never color alone.
 * All chips are understandable in monochrome print.
 * 
 * Entry statuses:
 * - entered: neutral outline
 * - withdrawn_before_draw: muted ("not racing")
 * - withdrawn_after_draw: warn
 * - dns: warn
 * - dnf: warn  
 * - excluded: danger
 * - dsq: danger (strongest)
 * 
 * Investigation/approval/immutability:
 * - under_investigation: info + "⚠" icon
 * - approved/immutable: success + "🔒" icon
 * - offline_queued: muted + "⏸" icon (never green)
 * 
 * Result labels:
 * - provisional: neutral outline + "⏱" icon
 * - edited: info + "✎" icon
 * - official: success + "✓" icon
 * 
 * Usage:
 * <RdChip variant="success" label="Official" icon="✓" />
 * <RdChip variant="warn" label="DNS" />
 * <RdChip variant="info" label="Under Investigation" icon="⚠" />
 */

const props = defineProps({
  /**
   * Visual variant
   * @values neutral, info, success, warn, danger, muted
   */
  variant: {
    type: String,
    default: 'neutral',
    validator: (value) => 
      ['neutral', 'info', 'success', 'warn', 'danger', 'muted'].includes(value),
  },
  /**
   * Chip label (required)
   */
  label: {
    type: String,
    required: true,
  },
  /**
   * Optional icon (emoji or symbol)
   */
  icon: {
    type: String,
    default: null,
  },
  /**
   * Optional count badge
   */
  count: {
    type: Number,
    default: null,
  },
  /**
   * ARIA role
   */
  role: {
    type: String,
    default: 'status',
  },
  /**
   * ARIA label (defaults to label + count if present)
   */
  ariaLabel: {
    type: String,
    default: null,
  },
  /**
   * Size variant
   * @values sm, base
   */
  size: {
    type: String,
    default: 'base',
    validator: (value) => ['sm', 'base'].includes(value),
  },
});

const chipClasses = computed(() => ({
  [`rd-chip--${props.variant}`]: true,
  [`rd-chip--${props.size}`]: true,
}));
</script>

<style scoped>
.rd-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--rd-space-1);
  padding: var(--rd-space-1) var(--rd-space-2);
  border-radius: var(--rd-border-radius);
  font-size: var(--rd-text-xs);
  font-weight: var(--rd-weight-medium);
  line-height: var(--rd-leading-tight);
  white-space: nowrap;
  border: 1px solid transparent;
}

.rd-chip--base {
  font-size: var(--rd-text-sm);
}

.rd-chip-icon {
  font-size: 1em;
  line-height: 1;
}

.rd-chip-label {
  line-height: 1;
}

.rd-chip-count {
  margin-left: var(--rd-space-1);
  padding: 0 var(--rd-space-1);
  background-color: rgba(0, 0, 0, 0.1);
  border-radius: var(--rd-border-radius);
  font-size: 0.9em;
  font-weight: var(--rd-weight-semibold);
}

/* Variant styles */

/* Neutral - outline */
.rd-chip--neutral {
  background-color: transparent;
  border-color: var(--rd-border);
  color: var(--rd-text);
}

/* Info */
.rd-chip--info {
  background-color: #dbeafe;
  color: #1e40af;
  border-color: #93c5fd;
}

/* Success */
.rd-chip--success {
  background-color: #d1fae5;
  color: #065f46;
  border-color: #6ee7b7;
}

/* Warning */
.rd-chip--warn {
  background-color: #fef3c7;
  color: #78350f;
  border-color: #fde68a;
}

/* Danger */
.rd-chip--danger {
  background-color: #fee2e2;
  color: #7f1d1d;
  border-color: #fca5a5;
}

/* Muted */
.rd-chip--muted {
  background-color: var(--rd-surface-2);
  color: var(--rd-text-muted);
  border-color: var(--rd-border);
}

/* High contrast mode adjustments */
:root[data-contrast="high"] .rd-chip {
  border-width: 2px;
  font-weight: var(--rd-weight-semibold);
}

:root[data-contrast="high"] .rd-chip--neutral {
  border-color: var(--rd-text);
}

:root[data-contrast="high"] .rd-chip--info {
  background-color: #bfdbfe;
  border-color: #1e40af;
}

:root[data-contrast="high"] .rd-chip--success {
  background-color: #a7f3d0;
  border-color: #065f46;
}

:root[data-contrast="high"] .rd-chip--warn {
  background-color: #fde68a;
  border-color: #78350f;
}

:root[data-contrast="high"] .rd-chip--danger {
  background-color: #fca5a5;
  border-color: #7f1d1d;
}
</style>
