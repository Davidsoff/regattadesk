<template>
  <div class="print-header">
    <div class="print-header__left">
      <h1 class="print-header__title">{{ regattaName }}</h1>
    </div>
    <div class="print-header__meta">
      <div class="print-header__meta-item">
        <strong>{{ t('print.generated') }}:</strong> {{ formattedTimestamp }}
      </div>
      <div class="print-header__meta-item" v-if="drawRevision !== null && drawRevision !== undefined">
        <strong>{{ t('print.draw_version') }}:</strong> v{{ drawRevision }}
      </div>
      <div class="print-header__meta-item" v-if="resultsRevision !== null && resultsRevision !== undefined">
        <strong>{{ t('print.results_version') }}:</strong> v{{ resultsRevision }}
      </div>
      <div class="print-header__meta-item" v-if="pageNumber !== null && pageNumber !== undefined">
        <strong>{{ t('print.page') }}:</strong> {{ pageNumber }}<span v-if="totalPages !== null && totalPages !== undefined"> {{ t('print.of') }} {{ totalPages }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { useFormatting } from '../../composables/useFormatting';

const props = defineProps({
  regattaName: {
    type: String,
    required: true
  },
  drawRevision: {
    type: Number,
    default: null
  },
  resultsRevision: {
    type: Number,
    default: null
  },
  pageNumber: {
    type: Number,
    default: null
  },
  totalPages: {
    type: Number,
    default: null
  },
  timestamp: {
    type: [String, Date],
    default: () => new Date()
  },
  regattaTimezone: {
    type: String,
    default: null
  }
});

const { t, locale } = useI18n();
const { formatTimestampDisplay } = useFormatting(locale);

const formattedTimestamp = computed(() => {
  return formatTimestampDisplay(props.timestamp, props.regattaTimezone);
});
</script>

<style scoped>
/* Styles are primarily in print.css, but include base styles for preview */
.print-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding-bottom: 0.5rem;
  margin-bottom: 1rem;
  border-bottom: 2px solid #000;
}

.print-header__title {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0;
}

.print-header__meta {
  text-align: right;
  font-size: 0.875rem;
  line-height: 1.6;
}

.print-header__meta-item {
  margin-bottom: 0.25rem;
}

@media print {
  .print-header__title {
    font-size: 14pt;
  }
  
  .print-header__meta {
    font-size: 9pt;
  }
}
</style>
