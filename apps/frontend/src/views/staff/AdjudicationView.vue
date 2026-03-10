<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { createAdjudicationApi, createApiClient } from '../../api'

const { t } = useI18n()
const route = useRoute()

const api = createAdjudicationApi(createApiClient())
const regattaId = computed(() => route.params.regattaId)

const investigations = ref([])
const detail = ref(null)
const selectedEntryId = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const openForm = ref({ entry_id: '', description: '' })
const actionForm = ref({ reason: '', note: '', penalty_seconds: 15 })

function resetMessages() {
  error.value = ''
  success.value = ''
}

async function loadInvestigations() {
  loading.value = true
  resetMessages()
  try {
    await refreshInvestigations()
  } finally {
    loading.value = false
  }
}

async function refreshInvestigations(options = {}) {
  const { preserveRevisionImpact = null } = options
  try {
    const response = await api.listInvestigations(regattaId.value)
    investigations.value = response || []
    const nextEntryId = selectedEntryId.value || investigations.value[0]?.entry_id
    if (nextEntryId) {
      await selectEntry(nextEntryId, { resetFeedback: false, preserveRevisionImpact })
    } else {
      detail.value = null
    }
  } catch (err) {
    error.value = err.message || t('adjudication.errors.load')
  }
}

async function selectEntry(entryId, options = {}) {
  const { resetFeedback = true, preserveRevisionImpact = null } = options
  if (!entryId) {
    detail.value = null
    selectedEntryId.value = ''
    openForm.value.entry_id = ''
    return
  }
  if (resetFeedback) {
    resetMessages()
  }
  selectedEntryId.value = entryId
  const nextDetail = await api.getEntryDetail(regattaId.value, entryId)
  if (preserveRevisionImpact) {
    nextDetail.revision_impact = preserveRevisionImpact
  }
  detail.value = nextDetail
  openForm.value.entry_id = entryId
}

async function handleEntrySelection(entryId) {
  try {
    await selectEntry(entryId)
  } catch (err) {
    error.value = err.message || t('adjudication.errors.load')
  }
}

async function submitInvestigation() {
  if (!openForm.value.entry_id || !openForm.value.description.trim()) {
    error.value = t('adjudication.errors.open_required')
    return
  }

  saving.value = true
  resetMessages()
  try {
    detail.value = await api.openInvestigation(regattaId.value, {
      entry_id: openForm.value.entry_id,
      description: openForm.value.description.trim()
    })
    selectedEntryId.value = openForm.value.entry_id
    openForm.value.description = ''
    success.value = t('adjudication.messages.investigation_opened')
    await refreshInvestigations()
  } catch (err) {
    error.value = err.message || t('adjudication.errors.open_failed')
  } finally {
    saving.value = false
  }
}

async function submitAction(action) {
  if (!selectedEntryId.value || !actionForm.value.reason.trim()) {
    error.value = t('adjudication.errors.action_required')
    return
  }

  const payload = {
    reason: actionForm.value.reason.trim(),
    note: actionForm.value.note.trim() || undefined
  }
  if (action === 'penalty') {
    const seconds = Number(actionForm.value.penalty_seconds)
    if (!Number.isFinite(seconds) || !Number.isInteger(seconds) || seconds <= 0) {
      error.value = t('adjudication.errors.invalid_penalty_seconds')
      return
    }
    payload.penalty_seconds = seconds
  }

  const actionMap = {
    penalty: api.applyPenalty,
    dsq: api.applyDsq,
    exclusion: api.applyExclusion,
    revert_dsq: api.revertDsq
  }

  saving.value = true
  resetMessages()
  try {
    const result = await actionMap[action](regattaId.value, selectedEntryId.value, payload)
    detail.value = result
    success.value = result.revision_impact.message
    const revisionImpact = result.revision_impact ? { ...result.revision_impact } : null
    await refreshInvestigations({ preserveRevisionImpact: revisionImpact })
    if (revisionImpact && detail.value) {
      detail.value.revision_impact = revisionImpact
    }
  } catch (err) {
    error.value = err.message || t('adjudication.errors.action_failed')
  } finally {
    saving.value = false
  }
}

watch(regattaId, () => {
  selectedEntryId.value = ''
  openForm.value.entry_id = ''
  loadInvestigations()
})

onMounted(loadInvestigations)
</script>

<template>
  <div class="adjudication-view">
    <header class="adjudication-header">
      <div>
        <h2>{{ t('adjudication.title') }}</h2>
        <p>{{ t('adjudication.subtitle') }}</p>
      </div>
      <p v-if="detail" class="revision-chip">
        {{ t('adjudication.revision.current', { revision: detail.revision_impact.current_results_revision }) }}
      </p>
    </header>

    <p v-if="error" class="banner banner-error" role="alert">{{ error }}</p>
    <p v-if="success" class="banner banner-success">{{ success }}</p>

    <div class="adjudication-grid">
      <section class="panel">
        <h3>{{ t('adjudication.sections.investigations') }}</h3>
        <form class="stack" @submit.prevent="submitInvestigation">
          <label>
            <span>{{ t('adjudication.open.entry_id') }}</span>
            <input v-model="openForm.entry_id" data-testid="open-entry-id" type="text" />
          </label>
          <label>
            <span>{{ t('adjudication.open.description') }}</span>
            <textarea v-model="openForm.description" rows="3" />
          </label>
          <button type="submit" :disabled="saving">{{ t('adjudication.open.submit') }}</button>
        </form>

        <p v-if="loading">{{ t('common.loading') }}</p>
        <ul v-else class="investigation-list">
          <li v-for="item in investigations" :key="item.investigation_id">
            <button
              type="button"
              class="investigation-item"
              :class="{ active: selectedEntryId === item.entry_id }"
              @click="handleEntrySelection(item.entry_id)"
            >
              <strong>{{ item.crew_name }}</strong>
              <span>{{ item.description }}</span>
              <small>{{ item.status }}</small>
            </button>
          </li>
        </ul>
      </section>

      <section class="panel">
        <h3>{{ t('adjudication.sections.detail') }}</h3>
        <div v-if="detail" class="stack">
          <div class="entry-card">
            <strong>{{ detail.entry.crew_name }}</strong>
            <span>{{ t('adjudication.entry.status') }}: {{ detail.entry.status }}</span>
            <span>{{ t('adjudication.entry.result_label') }}: {{ detail.entry.result_label }}</span>
            <span v-if="detail.entry.penalty_seconds !== null">
              {{ t('adjudication.entry.penalty_seconds') }}: {{ detail.entry.penalty_seconds }}
            </span>
          </div>

          <form class="stack" data-testid="penalty-form" @submit.prevent="submitAction('penalty')">
            <label>
              <span>{{ t('adjudication.actions.reason') }}</span>
              <input v-model="actionForm.reason" data-testid="action-reason" type="text" />
            </label>
            <label>
              <span>{{ t('adjudication.actions.note') }}</span>
              <textarea v-model="actionForm.note" rows="2" />
            </label>
            <label>
              <span>{{ t('adjudication.actions.penalty_seconds') }}</span>
              <input v-model="actionForm.penalty_seconds" data-testid="penalty-seconds" type="number" min="1" />
            </label>
            <div class="button-row">
              <button type="submit" :disabled="saving">{{ t('adjudication.actions.penalty') }}</button>
              <button type="button" :disabled="saving" @click="submitAction('dsq')">{{ t('adjudication.actions.dsq') }}</button>
              <button type="button" :disabled="saving" @click="submitAction('exclusion')">{{ t('adjudication.actions.exclusion') }}</button>
              <button type="button" :disabled="saving" @click="submitAction('revert_dsq')">{{ t('adjudication.actions.revert_dsq') }}</button>
            </div>
          </form>

          <p class="revision-message">{{ detail.revision_impact.message }}</p>

          <div>
            <h4>{{ t('adjudication.sections.history') }}</h4>
            <ul class="history-list">
              <li v-for="item in detail.history" :key="`${item.action}-${item.created_at}`">
                <strong>{{ item.action }}</strong>
                <span>{{ item.reason }}</span>
                <small>{{ item.actor }} · r{{ item.results_revision }}</small>
              </li>
            </ul>
          </div>
        </div>
        <p v-else>{{ t('adjudication.empty') }}</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.adjudication-view {
  display: grid;
  gap: 1.5rem;
}

.adjudication-header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
}

.adjudication-grid {
  display: grid;
  gap: 1rem;
  grid-template-columns: minmax(18rem, 24rem) minmax(0, 1fr);
}

.panel {
  border: 1px solid var(--rd-border);
  border-radius: 0.75rem;
  background: var(--rd-surface);
  padding: 1rem;
}

.stack {
  display: grid;
  gap: 0.75rem;
}

.banner {
  margin: 0;
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
}

.banner-error {
  background: #fff1f2;
  color: #9f1239;
}

.banner-success,
.revision-chip,
.revision-message {
  background: #ecfdf3;
  color: #166534;
}

.revision-chip {
  border-radius: 999px;
  padding: 0.5rem 0.9rem;
  margin: 0;
}

.investigation-list,
.history-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 0.5rem;
}

.investigation-item {
  width: 100%;
  text-align: left;
  display: grid;
  gap: 0.25rem;
  border: 1px solid var(--rd-border);
  background: var(--rd-bg);
  border-radius: 0.5rem;
  padding: 0.75rem;
}

.investigation-item.active {
  border-color: var(--rd-accent);
}

.entry-card {
  display: grid;
  gap: 0.3rem;
  background: var(--rd-bg);
  border-radius: 0.5rem;
  padding: 0.75rem;
}

.button-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

label {
  display: grid;
  gap: 0.35rem;
}

input,
textarea,
button {
  font: inherit;
}

input,
textarea {
  border: 1px solid var(--rd-border);
  border-radius: 0.5rem;
  padding: 0.65rem 0.75rem;
}

button {
  border: 1px solid var(--rd-text);
  background: var(--rd-bg);
  color: var(--rd-text);
  border-radius: 0.5rem;
  padding: 0.65rem 0.9rem;
}

@media (max-width: 900px) {
  .adjudication-grid {
    grid-template-columns: 1fr;
  }
}
</style>
