<template>
  <div v-if="status !== 'idle'" class="export-job-status" aria-live="polite">
    <!-- Pending state -->
    <div
      v-if="status === 'pending'"
      data-testid="export-status-pending"
      class="export-job-status__item export-job-status__item--pending"
    >
      <span data-testid="export-spinner" class="export-job-status__spinner" aria-hidden="true">⏳</span>
      <span class="export-job-status__message">{{ t('export.status.preparing') }}</span>
    </div>

    <!-- Processing state -->
    <div
      v-if="status === 'processing'"
      data-testid="export-status-processing"
      class="export-job-status__item export-job-status__item--processing"
    >
      <span data-testid="export-spinner" class="export-job-status__spinner" aria-hidden="true">⏳</span>
      <span class="export-job-status__message">{{ t('export.status.generating') }}</span>
    </div>

    <!-- Completed state -->
    <div
      v-if="status === 'completed'"
      data-testid="export-status-completed"
      class="export-job-status__item export-job-status__item--completed"
    >
      <span class="export-job-status__message">{{ t('export.status.ready') }}</span>
      <a
        v-if="downloadUrl"
        :href="downloadUrl"
        data-testid="export-download-link"
        class="export-job-status__download-link"
        download
      >
        {{ t('common.download') }}
      </a>
      <p data-testid="export-expiration-notice" class="export-job-status__expiration">
        {{ t('export.expiration_notice') }}
      </p>
      <button
        v-if="!downloadUrl"
        type="button"
        data-testid="export-regenerate-button"
        class="export-job-status__retry-button"
        @click="handleRetry"
      >
        {{ t('export.retry') }}
      </button>
    </div>

    <!-- Failed state -->
    <div
      v-if="status === 'failed'"
      data-testid="export-status-failed"
      class="export-job-status__item export-job-status__item--failed"
      role="alert"
    >
      <span class="export-job-status__message">{{ t('export.status.failed') }}</span>
      <p data-testid="export-error-message" class="export-job-status__error">{{ error }}</p>
      <button
        type="button"
        data-testid="export-retry-button"
        class="export-job-status__retry-button"
        @click="handleRetry"
      >
        {{ t('export.retry') }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps({
  status: {
    type: String,
    required: true,
    validator: (value) => ['idle', 'pending', 'processing', 'completed', 'failed'].includes(value)
  },
  jobId: {
    type: String,
    default: null
  },
  downloadUrl: {
    type: String,
    default: null
  },
  error: {
    type: String,
    default: null
  },
  onStart: {
    type: Function,
    required: true
  }
})

function handleRetry() {
  props.onStart()
}
</script>

<style scoped>
.export-job-status {
  margin: var(--rd-space-3, 1rem) 0;
  padding: var(--rd-space-3, 1rem);
  border: 1px solid var(--rd-color-border, #e0e0e0);
  border-radius: var(--rd-radius-md, 0.375rem);
  background-color: var(--rd-color-surface, #fff);
}

.export-job-status__item {
  display: flex;
  flex-direction: column;
  gap: var(--rd-space-2, 0.5rem);
}

.export-job-status__item--pending,
.export-job-status__item--processing {
  color: var(--rd-color-info, #0066cc);
}

.export-job-status__item--completed {
  color: var(--rd-color-success, #008000);
}

.export-job-status__item--failed {
  color: var(--rd-color-error, #d32f2f);
}

.export-job-status__spinner {
  font-size: 1.5rem;
  display: inline-block;
  animation: spin 2s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.export-job-status__message {
  font-weight: 600;
  font-size: 1rem;
}

.export-job-status__download-link {
  display: inline-block;
  padding: var(--rd-space-2, 0.5rem) var(--rd-space-3, 1rem);
  background-color: var(--rd-color-primary, #0066cc);
  color: #fff;
  text-decoration: none;
  border-radius: var(--rd-radius-sm, 0.25rem);
  font-weight: 600;
  align-self: flex-start;
}

.export-job-status__download-link:hover {
  background-color: var(--rd-color-primary-hover, #0052a3);
}

.export-job-status__download-link:focus {
  outline: 2px solid var(--rd-color-focus, #0066cc);
  outline-offset: 2px;
}

.export-job-status__expiration {
  font-size: 0.875rem;
  color: var(--rd-color-text-secondary, #666);
  margin: 0;
}

.export-job-status__error {
  font-size: 0.875rem;
  margin: 0;
  padding: var(--rd-space-2, 0.5rem);
  background-color: var(--rd-color-error-bg, #fef2f2);
  border-radius: var(--rd-radius-sm, 0.25rem);
}

.export-job-status__retry-button {
  padding: var(--rd-space-2, 0.5rem) var(--rd-space-3, 1rem);
  background-color: var(--rd-color-primary, #0066cc);
  color: #fff;
  border: none;
  border-radius: var(--rd-radius-sm, 0.25rem);
  font-weight: 600;
  cursor: pointer;
  align-self: flex-start;
}

.export-job-status__retry-button:hover {
  background-color: var(--rd-color-primary-hover, #0052a3);
}

.export-job-status__retry-button:focus {
  outline: 2px solid var(--rd-color-focus, #0066cc);
  outline-offset: 2px;
}
</style>
