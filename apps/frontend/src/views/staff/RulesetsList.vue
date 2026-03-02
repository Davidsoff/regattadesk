<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { createApiClient, createDrawApi } from '../../api/index.js'
import RdTable from '../../components/primitives/RdTable.vue'
import RdChip from '../../components/primitives/RdChip.vue'

const { t } = useI18n()
const router = useRouter()

const client = createApiClient()
const api = createDrawApi(client)

const rulesets = ref([])
const loading = ref(false)
const error = ref(null)
const filterGlobal = ref(null)

async function loadRulesets() {
  loading.value = true
  error.value = null
  try {
    const params = {}
    if (filterGlobal.value !== null) {
      params.is_global = filterGlobal.value
    }
    const response = await api.listRulesets(params)
    rulesets.value = response.data
  } catch (err) {
    error.value = err
    console.error('Failed to load rulesets:', err)
  } finally {
    loading.value = false
  }
}

function navigateToDetail(rulesetId) {
  router.push({
    name: 'staff-ruleset-detail',
    params: { rulesetId }
  })
}

function createNewRuleset() {
  router.push({ name: 'staff-ruleset-create' })
}

const AGE_CALCULATION_LABELS = {
  actual_at_start: 'rulesets.ageCalculation.actualAtStart',
  age_as_of_jan_1: 'rulesets.ageCalculation.ageAsOfJan1'
}

function ageCalculationLabel(type) {
  return t(AGE_CALCULATION_LABELS[type] || type)
}

onMounted(() => {
  loadRulesets()
})
</script>

<template>
  <div class="staff-rulesets-list">
    <div class="page-header">
      <h1>{{ t('rulesets.title') }}</h1>
      <button @click="createNewRuleset" class="btn-primary">
        {{ t('rulesets.createButton') }}
      </button>
    </div>

    <div class="filters">
      <label>
        <input
          type="radio"
          :value="null"
          v-model="filterGlobal"
          @change="loadRulesets"
        />
        {{ t('rulesets.filter.all') }}
      </label>
      <label>
        <input
          type="radio"
          :value="true"
          v-model="filterGlobal"
          @change="loadRulesets"
        />
        {{ t('rulesets.filter.global') }}
      </label>
      <label>
        <input
          type="radio"
          :value="false"
          v-model="filterGlobal"
          @change="loadRulesets"
        />
        {{ t('rulesets.filter.regattaOwned') }}
      </label>
    </div>

    <div v-if="error" class="error-message" role="alert">
      <span>{{ t('rulesets.loadError') }}</span>
      <button class="btn-retry" @click="loadRulesets" :disabled="loading">
        {{ t('rulesets.retryButton') }}
      </button>
    </div>

    <RdTable
      :caption="t('rulesets.tableCaption')"
      :loading="loading"
      :isEmpty="!loading && rulesets.length === 0"
      :emptyText="t('rulesets.emptyState')"
      sticky
    >
      <template #header>
        <tr>
          <th scope="col">{{ t('rulesets.columns.name') }}</th>
          <th scope="col">{{ t('rulesets.columns.version') }}</th>
          <th scope="col">{{ t('rulesets.columns.ageCalculation') }}</th>
          <th scope="col">{{ t('rulesets.columns.scope') }}</th>
          <th scope="col">{{ t('rulesets.columns.description') }}</th>
        </tr>
      </template>

      <tr
        v-for="ruleset in rulesets"
        :key="ruleset.id"
        @click="navigateToDetail(ruleset.id)"
        class="clickable-row"
        tabindex="0"
        @keydown.enter="navigateToDetail(ruleset.id)"
        @keydown.space.prevent="navigateToDetail(ruleset.id)"
      >
        <td>
          <strong>{{ ruleset.name }}</strong>
        </td>
        <td>{{ ruleset.version }}</td>
        <td>{{ ageCalculationLabel(ruleset.age_calculation_type) }}</td>
        <td>
          <RdChip
            v-if="ruleset.is_global"
            variant="info"
            :label="t('rulesets.scope.global')"
            size="sm"
          />
          <RdChip
            v-else
            variant="neutral"
            :label="t('rulesets.scope.regattaOwned')"
            size="sm"
          />
        </td>
        <td>{{ ruleset.description || '—' }}</td>
      </tr>
    </RdTable>
  </div>
</template>

<style scoped>
.staff-rulesets-list {
  padding: var(--rd-space-4);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--rd-space-4);
}

.page-header h1 {
  margin: 0;
  font-size: var(--rd-text-xl);
  font-weight: var(--rd-weight-semibold);
}

.filters {
  display: flex;
  gap: var(--rd-space-3);
  margin-bottom: var(--rd-space-3);
  padding: var(--rd-space-3);
  background: var(--rd-surface);
  border: 1px solid var(--rd-border);
  border-radius: 4px;
}

.filters label {
  display: flex;
  align-items: center;
  gap: var(--rd-space-1);
  cursor: pointer;
}

.filters input[type='radio'] {
  cursor: pointer;
}

.btn-primary {
  background: var(--rd-accent);
  color: white;
  border: none;
  padding: var(--rd-space-2) var(--rd-space-4);
  border-radius: 4px;
  font-weight: var(--rd-weight-medium);
  cursor: pointer;
  min-height: var(--rd-hit);
}

.btn-primary:hover {
  opacity: 0.9;
}

.btn-primary:focus {
  outline: 2px solid var(--rd-focus);
  outline-offset: 2px;
}

.error-message {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--rd-space-3);
  padding: var(--rd-space-3);
  background: var(--rd-danger);
  color: white;
  border-radius: 4px;
  margin-bottom: var(--rd-space-3);
}

.btn-retry {
  border: 1px solid currentColor;
  background: transparent;
  color: inherit;
  border-radius: 4px;
  padding: var(--rd-space-1) var(--rd-space-3);
  font-weight: var(--rd-weight-medium);
  cursor: pointer;
  min-height: var(--rd-hit-sm);
}

.btn-retry:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.clickable-row {
  cursor: pointer;
}

.clickable-row:hover {
  background: var(--rd-surface);
}

.clickable-row:focus {
  outline: 2px solid var(--rd-focus);
  outline-offset: -2px;
}
</style>
