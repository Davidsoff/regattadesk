<script setup>
import { ref, computed, inject, nextTick, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { createApiClient, createDrawApi } from '../../api'

const { t } = useI18n()
const route = useRoute()

// State management
const regattaState = inject('regattaState', {
  drawGenerated: false,
  drawPublished: false,
  drawRevision: 0,
  resultsRevision: 0,
  generatedSeed: null,
  prerequisites: {
    blocksConfigured: true,
    bibPoolsAssigned: true,
    entriesExist: true
  }
})

const loading = ref(false)
const error = ref(null)
const showSeedInput = ref(false)
const customSeed = ref('')
const generatedSeed = ref(regattaState.generatedSeed || null)
const publishButton = ref(null)
const unpublishButton = ref(null)
const publishDialog = ref(null)
const unpublishDialog = ref(null)
const drawStatus = ref({
  generated: regattaState.drawGenerated || false,
  published: regattaState.drawPublished || false
})
const revisions = ref({
  draw: regattaState.drawRevision || 0,
  results: regattaState.resultsRevision || 0
})
const prerequisites = ref(regattaState.prerequisites || {
  blocksConfigured: true,
  bibPoolsAssigned: true,
  entriesExist: true
})

// Confirmation dialogs
const showPublishDialog = ref(false)
const showUnpublishDialog = ref(false)

// API client
const apiClient = createApiClient()
const drawApi = createDrawApi(apiClient)

// Computed properties
const drawStatusText = computed(() => {
  if (!drawStatus.value.generated) {
    return t('staff.draw_workflow.status.not_generated')
  }
  if (drawStatus.value.generated && !drawStatus.value.published) {
    return t('staff.draw_workflow.status.generated')
  }
  return t('staff.draw_workflow.status.published')
})

const canGenerate = computed(() => {
  return !loading.value && 
         !(drawStatus.value.generated && drawStatus.value.published) &&
         prerequisites.value.blocksConfigured &&
         prerequisites.value.bibPoolsAssigned &&
         prerequisites.value.entriesExist
})

const canPublish = computed(() => {
  return !loading.value && drawStatus.value.generated && !drawStatus.value.published
})

const canUnpublish = computed(() => {
  return !loading.value && drawStatus.value.published
})

const nextDrawRevision = computed(() => revisions.value.draw + 1)

function isInteger(value) {
  return typeof value === 'number' && Number.isInteger(value)
}

function parseSeedValue(rawValue) {
  const trimmedSeed = rawValue.trim()
  if (trimmedSeed === '') {
    return null
  }

  const numericSeed = Number(trimmedSeed)
  if (!Number.isSafeInteger(numericSeed)) {
    throw new TypeError(t('staff.draw_workflow.generate.invalid_seed'))
  }

  return numericSeed
}

function validateGenerateResponse(result) {
  if (!result || !isInteger(result.seed)) {
    throw new Error(t('staff.draw_workflow.generate.invalid_response'))
  }
}

function validateRevisionResponse(result, action) {
  if (!result || !isInteger(result.draw_revision) || !isInteger(result.results_revision)) {
    throw new Error(t(`staff.draw_workflow.${action}.invalid_response`))
  }
}

// Actions
async function generateDraw() {
  loading.value = true
  error.value = null
  
  try {
    const payload = {}
    const parsedSeed = parseSeedValue(customSeed.value)
    if (parsedSeed !== null) {
      payload.seed = parsedSeed
    }

    const result = await drawApi.generateDraw(route.params.regattaId, payload)
    validateGenerateResponse(result)
    
    drawStatus.value.generated = true
    generatedSeed.value = result.seed
    revisions.value.draw += 1
    
    // Reset seed input
    showSeedInput.value = false
    customSeed.value = ''
  } catch (err) {
    error.value = err.message || t('staff.draw_workflow.generate.error')
  } finally {
    loading.value = false
  }
}

function openPublishDialog() {
  showPublishDialog.value = true
}

function closePublishDialog() {
  showPublishDialog.value = false
  nextTick(() => {
    publishButton.value?.focus()
  })
}

async function confirmPublish() {
  loading.value = true
  error.value = null
  showPublishDialog.value = false
  
  try {
    const result = await drawApi.publishDraw(route.params.regattaId)
    validateRevisionResponse(result, 'publish')
    
    drawStatus.value.published = true
    revisions.value.draw = result.draw_revision
    revisions.value.results = result.results_revision
  } catch (err) {
    error.value = err.message || t('staff.draw_workflow.publish.error')
  } finally {
    loading.value = false
  }
}

function openUnpublishDialog() {
  showUnpublishDialog.value = true
}

function closeUnpublishDialog() {
  showUnpublishDialog.value = false
  nextTick(() => {
    unpublishButton.value?.focus()
  })
}

async function confirmUnpublish() {
  loading.value = true
  error.value = null
  showUnpublishDialog.value = false
  
  try {
    const result = await drawApi.unpublishDraw(route.params.regattaId)
    validateRevisionResponse(result, 'unpublish')
    
    drawStatus.value.published = false
    drawStatus.value.generated = false
    revisions.value.draw = result.draw_revision
    revisions.value.results = result.results_revision
    generatedSeed.value = null
  } catch (err) {
    error.value = err.message || t('staff.draw_workflow.unpublish.error')
  } finally {
    loading.value = false
  }
}

function toggleSeedInput() {
  showSeedInput.value = !showSeedInput.value
  if (!showSeedInput.value) {
    customSeed.value = ''
  }
}

function dialogFocusableElements(dialogRef) {
  if (!dialogRef?.value) {
    return []
  }

  return [...dialogRef.value.querySelectorAll('button, textarea, input, select, [tabindex]:not([tabindex="-1"])')]
    .filter((element) => !element.hasAttribute('disabled'))
}

function onDialogKeydown(event, closeDialog, dialogRef) {
  if (event.key === 'Escape') {
    event.preventDefault()
    closeDialog()
    return
  }

  if (event.key !== 'Tab') {
    return
  }

  const focusable = dialogFocusableElements(dialogRef)
  if (!focusable.length) {
    event.preventDefault()
    return
  }

  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  const active = document.activeElement

  if (event.shiftKey) {
    if (active === first || !dialogRef.value?.contains(active)) {
      event.preventDefault()
      last.focus()
    }
    return
  }

  if (active === last) {
    event.preventDefault()
    first.focus()
  }
}

watch(showPublishDialog, async (open) => {
  if (!open) {
    return
  }

  await nextTick()
  const focusable = dialogFocusableElements(publishDialog)
  ;(focusable[0] || publishDialog.value)?.focus()
})

watch(showUnpublishDialog, async (open) => {
  if (!open) {
    return
  }

  await nextTick()
  const focusable = dialogFocusableElements(unpublishDialog)
  ;(focusable[0] || unpublishDialog.value)?.focus()
})

async function copySeedToClipboard() {
  if (generatedSeed.value && navigator.clipboard && navigator.clipboard.writeText) {
    try {
      await navigator.clipboard.writeText(String(generatedSeed.value))
    } catch {
      error.value = t('staff.draw_workflow.generate.copy_failed')
    }
  }
}
</script>

<template>
  <div class="draw-workflow" data-testid="draw-workflow">
    <h2>{{ t('staff.draw_workflow.title') }}</h2>
    <p>{{ t('staff.draw_workflow.description') }}</p>

    <!-- Loading indicator -->
    <div v-if="loading" data-testid="loading">
      {{ t('common.loading') }}
    </div>

    <!-- Error display -->
    <div v-if="error" data-testid="error" role="alert">
      {{ error }}
    </div>

    <!-- Draw status -->
    <section>
      <h3>{{ t('staff.draw_workflow.status.label') }}</h3>
      <p data-testid="draw-status">{{ drawStatusText }}</p>
    </section>

    <!-- Revisions -->
    <section data-testid="revisions">
      <h3>{{ t('staff.draw_workflow.revisions.draw') }}</h3>
      <p>{{ t('staff.draw_workflow.revisions.current', { revision: revisions.draw }) }}</p>
      
      <h3>{{ t('staff.draw_workflow.revisions.results') }}</h3>
      <p>{{ t('staff.draw_workflow.revisions.current', { revision: revisions.results }) }}</p>
    </section>

    <!-- Prerequisites -->
    <section data-testid="prerequisites">
      <h3>{{ t('staff.draw_workflow.prerequisites.title') }}</h3>
      <ul>
        <li>
          <label>
            <input 
              type="checkbox" 
              :checked="prerequisites.blocksConfigured" 
              disabled 
            />
            {{ t('staff.draw_workflow.prerequisites.blocks_configured') }}
          </label>
        </li>
        <li>
          <label>
            <input 
              type="checkbox" 
              :checked="prerequisites.bibPoolsAssigned" 
              disabled 
            />
            {{ t('staff.draw_workflow.prerequisites.bib_pools_assigned') }}
          </label>
        </li>
        <li>
          <label>
            <input 
              type="checkbox" 
              :checked="prerequisites.entriesExist" 
              disabled 
            />
            {{ t('staff.draw_workflow.prerequisites.entries_exist') }}
          </label>
        </li>
      </ul>
    </section>

    <!-- Generate draw -->
    <section>
      <h3>{{ t('staff.draw_workflow.generate.title') }}</h3>
      
      <button
        data-testid="seed-toggle"
        type="button"
        @click="toggleSeedInput"
      >
        {{ showSeedInput ? t('staff.draw_workflow.generate.seed_hide') : t('staff.draw_workflow.generate.seed_show') }}
      </button>

      <div v-if="showSeedInput">
        <label>
          {{ t('staff.draw_workflow.generate.seed_label') }}
          <input
            v-model="customSeed"
            data-testid="custom-seed-input"
            type="text"
            :placeholder="t('staff.draw_workflow.generate.seed_placeholder')"
          />
        </label>
      </div>

      <button
        data-testid="generate-draw-button"
        type="button"
        :disabled="!canGenerate"
        @click="generateDraw"
      >
        {{ canGenerate ? t('staff.draw_workflow.generate.button') : t('staff.draw_workflow.generate.button_disabled') }}
      </button>

      <div v-if="generatedSeed" data-testid="generated-seed">
        <p>{{ t('staff.draw_workflow.generate.generated_seed') }}: {{ generatedSeed }}</p>
        <button type="button" @click="copySeedToClipboard">
          {{ t('staff.draw_workflow.generate.copy_seed') }}
        </button>
      </div>
    </section>

    <!-- Publish draw -->
    <section>
      <h3>{{ t('staff.draw_workflow.publish.title') }}</h3>
      <button
        ref="publishButton"
        data-testid="publish-draw-button"
        type="button"
        :disabled="!canPublish"
        @click="openPublishDialog"
      >
        {{ canPublish ? t('staff.draw_workflow.publish.button') : t('staff.draw_workflow.publish.button_disabled') }}
      </button>

      <!-- Publish confirmation dialog -->
      <dialog
        v-if="showPublishDialog"
        ref="publishDialog"
        open
        data-testid="publish-confirm-dialog"
        aria-modal="true"
        aria-labelledby="publish-dialog-title"
        tabindex="-1"
        @keydown="(event) => onDialogKeydown(event, closePublishDialog, publishDialog)"
      >
        <h4 id="publish-dialog-title">{{ t('staff.draw_workflow.publish.confirm_title') }}</h4>
        <p>{{ t('staff.draw_workflow.publish.confirm_message', { from: revisions.draw, to: nextDrawRevision }) }}</p>
        <p>{{ t('staff.draw_workflow.publish.confirm_warning') }}</p>
        <p>{{ t('staff.draw_workflow.publish.confirm_note') }}</p>
        <button type="button" @click="confirmPublish">
          {{ t('common.confirm') }}
        </button>
        <button type="button" @click="closePublishDialog">
          {{ t('common.cancel') }}
        </button>
      </dialog>
    </section>

    <!-- Unpublish draw -->
    <section>
      <h3>{{ t('staff.draw_workflow.unpublish.title') }}</h3>
      <button
        ref="unpublishButton"
        data-testid="unpublish-draw-button"
        type="button"
        :disabled="!canUnpublish"
        @click="openUnpublishDialog"
      >
        {{ canUnpublish ? t('staff.draw_workflow.unpublish.button') : t('staff.draw_workflow.unpublish.button_disabled') }}
      </button>

      <!-- Unpublish confirmation dialog -->
      <dialog
        v-if="showUnpublishDialog"
        ref="unpublishDialog"
        open
        data-testid="unpublish-confirm-dialog"
        aria-modal="true"
        aria-labelledby="unpublish-dialog-title"
        tabindex="-1"
        @keydown="(event) => onDialogKeydown(event, closeUnpublishDialog, unpublishDialog)"
      >
        <h4 id="unpublish-dialog-title">{{ t('staff.draw_workflow.unpublish.confirm_title') }}</h4>
        <p>{{ t('staff.draw_workflow.unpublish.confirm_message', { current: revisions.draw }) }}</p>
        <p>{{ t('staff.draw_workflow.unpublish.confirm_warning') }}</p>
        <p>{{ t('staff.draw_workflow.unpublish.confirm_note') }}</p>
        <button type="button" @click="confirmUnpublish">
          {{ t('common.confirm') }}
        </button>
        <button type="button" @click="closeUnpublishDialog">
          {{ t('common.cancel') }}
        </button>
      </dialog>
    </section>
  </div>
</template>

<style scoped>
.draw-workflow h2 {
  margin-bottom: var(--rd-space-3, 1rem);
}

.draw-workflow section {
  margin-bottom: var(--rd-space-4, 1.5rem);
  padding: var(--rd-space-3, 1rem);
  border: 1px solid var(--rd-border-color, #e0e0e0);
  border-radius: var(--rd-radius-md, 4px);
}

.draw-workflow button {
  margin-right: var(--rd-space-2, 0.5rem);
  margin-top: var(--rd-space-2, 0.5rem);
}

.draw-workflow button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.draw-workflow dialog {
  padding: var(--rd-space-4, 1.5rem);
  border: 1px solid var(--rd-border-color, #e0e0e0);
  border-radius: var(--rd-radius-md, 4px);
}

.draw-workflow [role="alert"] {
  padding: var(--rd-space-3, 1rem);
  margin-bottom: var(--rd-space-3, 1rem);
  background-color: var(--rd-error-bg, #fee);
  color: var(--rd-error-text, #c00);
  border: 1px solid var(--rd-error-border, #c00);
  border-radius: var(--rd-radius-md, 4px);
}
</style>
