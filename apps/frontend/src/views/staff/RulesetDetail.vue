<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { createApiClient, createDrawApi } from '../../api'
import { useUserRole } from '../../composables/useUserRole'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

// API setup
const client = createApiClient()
const drawApi = createDrawApi(client)

// State
const loading = ref(true)
const saving = ref(false)
const duplicating = ref(false)
const promoting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const ruleset = ref(null)
const formData = ref({
  name: '',
  version: '',
  description: '',
  age_calculation_type: ''
})
const formErrors = ref({
  name: '',
  version: '',
  age_calculation_type: ''
})

// Duplicate dialog state
const showDuplicateDialog = ref(false)
const duplicateTriggerButton = ref(null)
const duplicateDialog = ref(null)
const duplicateForm = ref({
  new_name: '',
  new_version: ''
})
const duplicateFormErrors = ref({
  new_name: '',
  new_version: ''
})

// Promote dialog state
const showPromoteDialog = ref(false)
const promoteTriggerButton = ref(null)
const promoteDialog = ref(null)
const saveSuccessTimerId = ref(null)
const promoteSuccessTimerId = ref(null)

const { isSuperAdmin, loadRole } = useUserRole()

const canPromote = computed(() => {
  return isSuperAdmin.value && ruleset.value && !ruleset.value.is_global
})

// Load ruleset data
async function loadRuleset() {
  loading.value = true
  errorMessage.value = ''
  
  try {
    const rulesetId = route.params.rulesetId
    const data = await drawApi.getRuleset(rulesetId)
    
    ruleset.value = data
    formData.value = {
      name: data.name,
      version: data.version,
      description: data.description || '',
      age_calculation_type: data.age_calculation_type
    }
  } catch (error) {
    errorMessage.value = t('ruleset.messages.load_error')
    console.error('Failed to load ruleset:', error)
  } finally {
    loading.value = false
  }
}

// Form validation
function validateForm() {
  formErrors.value = {
    name: '',
    version: '',
    age_calculation_type: ''
  }
  
  let hasErrors = false
  
  if (!formData.value.name || formData.value.name.trim() === '') {
    formErrors.value.name = t('validation.ruleset.nameRequired')
    hasErrors = true
  }
  
  if (!formData.value.version || formData.value.version.trim() === '') {
    formErrors.value.version = t('validation.ruleset.versionRequired')
    hasErrors = true
  }
  
  if (!formData.value.age_calculation_type || formData.value.age_calculation_type.trim() === '') {
    formErrors.value.age_calculation_type = t('validation.ruleset.ageCalculationTypeRequired')
    hasErrors = true
  }
  
  return !hasErrors
}

function getFirstErrorMessage() {
  return formErrors.value.name || formErrors.value.version || formErrors.value.age_calculation_type || ''
}

// Save ruleset
async function saveRuleset() {
  if (!validateForm()) {
    return
  }
  
  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''
  
  try {
    const payload = {
      name: formData.value.name.trim(),
      version: formData.value.version.trim(),
      description: formData.value.description.trim(),
      age_calculation_type: formData.value.age_calculation_type
    }
    
    const updated = await drawApi.updateRuleset(route.params.rulesetId, payload)
    ruleset.value = updated
    successMessage.value = t('ruleset.messages.save_success')
    
    if (saveSuccessTimerId.value) {
      clearTimeout(saveSuccessTimerId.value)
    }
    saveSuccessTimerId.value = setTimeout(() => {
      successMessage.value = ''
      saveSuccessTimerId.value = null
    }, 3000)
  } catch (error) {
    errorMessage.value = t('ruleset.messages.save_error')
    console.error('Failed to save ruleset:', error)
  } finally {
    saving.value = false
  }
}

// Duplicate dialog management
function openDuplicateDialog() {
  duplicateForm.value = {
    new_name: '',
    new_version: ''
  }
  duplicateFormErrors.value = {
    new_name: '',
    new_version: ''
  }
  showDuplicateDialog.value = true
}

function closeDuplicateDialog() {
  showDuplicateDialog.value = false
  nextTick(() => {
    duplicateTriggerButton.value?.focus()
  })
}

function validateDuplicateForm() {
  duplicateFormErrors.value = {
    new_name: '',
    new_version: ''
  }
  
  let hasErrors = false
  
  if (!duplicateForm.value.new_name || duplicateForm.value.new_name.trim() === '') {
    duplicateFormErrors.value.new_name = t('validation.ruleset.nameRequired')
    hasErrors = true
  }
  
  if (!duplicateForm.value.new_version || duplicateForm.value.new_version.trim() === '') {
    duplicateFormErrors.value.new_version = t('validation.ruleset.versionRequired')
    hasErrors = true
  }
  
  return !hasErrors
}

function getDuplicateErrorMessage() {
  return duplicateFormErrors.value.new_name || duplicateFormErrors.value.new_version || ''
}

async function confirmDuplicate() {
  if (!validateDuplicateForm()) {
    return
  }
  
  duplicating.value = true
  errorMessage.value = ''
  
  try {
    const payload = {
      new_name: duplicateForm.value.new_name.trim(),
      new_version: duplicateForm.value.new_version.trim()
    }
    
    const newRuleset = await drawApi.duplicateRuleset(route.params.rulesetId, payload)
    
    // Navigate to the new ruleset
    router.push(`/staff/rulesets/${newRuleset.id}`)
    successMessage.value = t('ruleset.messages.duplicate_success')
    closeDuplicateDialog()
  } catch (error) {
    duplicateFormErrors.value.new_name = t('ruleset.messages.duplicate_error')
    console.error('Failed to duplicate ruleset:', error)
  } finally {
    duplicating.value = false
  }
}

// Promote dialog management
function openPromoteDialog() {
  showPromoteDialog.value = true
}

function closePromoteDialog() {
  showPromoteDialog.value = false
  nextTick(() => {
    promoteTriggerButton.value?.focus()
  })
}

async function confirmPromote() {
  promoting.value = true
  errorMessage.value = ''
  
  try {
    const promoted = await drawApi.promoteRuleset(route.params.rulesetId)
    ruleset.value = promoted
    successMessage.value = t('ruleset.messages.promote_success')
    closePromoteDialog()
    
    if (promoteSuccessTimerId.value) {
      clearTimeout(promoteSuccessTimerId.value)
    }
    promoteSuccessTimerId.value = setTimeout(() => {
      successMessage.value = ''
      promoteSuccessTimerId.value = null
    }, 3000)
  } catch (error) {
    if (error.status === 403) {
      errorMessage.value = t('ruleset.messages.promote_forbidden')
    } else {
      errorMessage.value = t('ruleset.messages.promote_error')
    }
    closePromoteDialog()
    console.error('Failed to promote ruleset:', error)
  } finally {
    promoting.value = false
  }
}

// Dialog keyboard navigation
function dialogFocusableElements(dialogRef) {
  if (!dialogRef) {
    return []
  }
  
  return [...dialogRef.querySelectorAll('button, textarea, input, select, [tabindex]:not([tabindex="-1"])')]
    .filter((element) => !element.hasAttribute('disabled'))
}

function onDialogKeydown(event, closeHandler) {
  if (event.key === 'Escape') {
    event.preventDefault()
    closeHandler()
    return
  }
  
  if (event.key !== 'Tab') {
    return
  }
  
  const dialogEl = event.currentTarget
  const focusable = dialogFocusableElements(dialogEl)
  if (!focusable.length) {
    event.preventDefault()
    return
  }
  
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  const active = document.activeElement
  
  if (event.shiftKey) {
    if (active === first || !dialogEl.contains(active)) {
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

watch(showDuplicateDialog, async (open) => {
  if (!open) {
    return
  }

  await nextTick()
  const focusable = dialogFocusableElements(duplicateDialog.value)
  ;(focusable[0] || duplicateDialog.value)?.focus()
})

watch(showPromoteDialog, async (open) => {
  if (!open) {
    return
  }

  await nextTick()
  const focusable = dialogFocusableElements(promoteDialog.value)
  ;(focusable[0] || promoteDialog.value)?.focus()
})

watch(
  () => route.params.rulesetId,
  (newRulesetId, previousRulesetId) => {
    if (!newRulesetId || newRulesetId === previousRulesetId) {
      return
    }

    errorMessage.value = ''
    successMessage.value = ''
    closeDuplicateDialog()
    closePromoteDialog()
    loadRuleset()
  }
)

onUnmounted(() => {
  if (saveSuccessTimerId.value) {
    clearTimeout(saveSuccessTimerId.value)
  }
  if (promoteSuccessTimerId.value) {
    clearTimeout(promoteSuccessTimerId.value)
  }
})

// Initialize
onMounted(() => {
  loadRole()
  loadRuleset()
})
</script>

<template>
  <div class="ruleset-detail">
    <h2>{{ t('ruleset.title') }}</h2>
    
    <!-- Loading state -->
    <div v-if="loading" data-testid="loading">
      {{ t('common.loading') }}
    </div>
    
    <!-- Error message -->
    <div v-if="errorMessage" role="alert" class="error-message">
      {{ errorMessage }}
    </div>
    
    <!-- Success message -->
    <div v-if="successMessage" class="success-message">
      {{ successMessage }}
    </div>
    
    <!-- Main content -->
    <div v-if="!loading && ruleset">
      <!-- Global status indicator -->
      <div data-testid="global-status" class="status-badge">
        {{ ruleset.is_global ? t('ruleset.status.global') : t('ruleset.status.regatta_owned') }}
      </div>
      
      <!-- Form -->
      <form @submit.prevent="saveRuleset">
        <div class="form-group">
          <label for="ruleset-name">
            {{ t('ruleset.name') }}
            <input
              id="ruleset-name"
              v-model="formData.name"
              name="name"
              type="text"
              :aria-invalid="formErrors.name ? 'true' : undefined"
            />
          </label>
        </div>
        
        <div class="form-group">
          <label for="ruleset-version">
            {{ t('ruleset.version') }}
            <input
              id="ruleset-version"
              v-model="formData.version"
              name="version"
              type="text"
              :aria-invalid="formErrors.version ? 'true' : undefined"
            />
          </label>
        </div>
        
        <div class="form-group">
          <label for="ruleset-description">
            {{ t('ruleset.description') }}
            <textarea
              id="ruleset-description"
              v-model="formData.description"
              name="description"
              rows="3"
            />
          </label>
        </div>
        
        <div class="form-group">
          <label for="ruleset-age-calc">
            {{ t('ruleset.age_calculation_type') }}
            <select
              id="ruleset-age-calc"
              v-model="formData.age_calculation_type"
              name="age_calculation_type"
              :aria-invalid="formErrors.age_calculation_type ? 'true' : undefined"
            >
              <option value="">{{ t('staff.regatta_detail.form.selectPlaceholder') }}</option>
              <option value="actual_at_start">
                {{ t('ruleset.age_calculation.actual_at_start') }}
              </option>
              <option value="age_as_of_jan_1">
                {{ t('ruleset.age_calculation.age_as_of_jan_1') }}
              </option>
            </select>
          </label>
        </div>
        
        <!-- Validation errors -->
        <div v-if="getFirstErrorMessage()" role="alert" class="validation-error">
          {{ getFirstErrorMessage() }}
        </div>
        
        <!-- Action buttons -->
        <div class="action-buttons">
          <button
            type="submit"
            data-testid="save-button"
            :disabled="saving"
          >
            {{ saving ? t('common.loading') : t('common.save') }}
          </button>
          
          <button
            type="button"
            data-testid="duplicate-button"
            ref="duplicateTriggerButton"
            @click="openDuplicateDialog"
          >
            {{ t('ruleset.actions.duplicate') }}
          </button>
          
          <button
            v-if="canPromote"
            type="button"
            data-testid="promote-button"
            ref="promoteTriggerButton"
            @click="openPromoteDialog"
          >
            {{ t('ruleset.actions.promote') }}
          </button>
        </div>
      </form>
    </div>
    
    <!-- Duplicate dialog -->
    <dialog
      v-if="showDuplicateDialog"
      ref="duplicateDialog"
      open
      data-testid="duplicate-dialog"
      aria-modal="true"
      aria-labelledby="duplicate-dialog-title"
      tabindex="-1"
      @keydown="onDialogKeydown($event, closeDuplicateDialog)"
    >
      <h3 id="duplicate-dialog-title">{{ t('ruleset.actions.duplicate_dialog_title') }}</h3>
      
      <div class="form-group">
        <label for="duplicate-name">
          {{ t('ruleset.actions.new_name') }}
          <input
            id="duplicate-name"
            v-model="duplicateForm.new_name"
            name="new_name"
            type="text"
          />
        </label>
      </div>
      
      <div class="form-group">
        <label for="duplicate-version">
          {{ t('ruleset.actions.new_version') }}
          <input
            id="duplicate-version"
            v-model="duplicateForm.new_version"
            name="new_version"
            type="text"
          />
        </label>
      </div>
      
      <div v-if="getDuplicateErrorMessage()" role="alert" class="validation-error">
        {{ getDuplicateErrorMessage() }}
      </div>
      
      <div class="dialog-actions">
        <button
          type="button"
          data-testid="duplicate-confirm"
          :disabled="duplicating"
          @click="confirmDuplicate"
        >
          {{ duplicating ? t('common.loading') : t('common.confirm') }}
        </button>
        <button
          type="button"
          @click="closeDuplicateDialog"
        >
          {{ t('common.cancel') }}
        </button>
      </div>
    </dialog>
    
    <!-- Promote dialog -->
    <dialog
      v-if="showPromoteDialog"
      ref="promoteDialog"
      open
      data-testid="promote-dialog"
      aria-modal="true"
      aria-labelledby="promote-dialog-title"
      tabindex="-1"
      @keydown="onDialogKeydown($event, closePromoteDialog)"
    >
      <h3 id="promote-dialog-title">{{ t('ruleset.actions.promote') }}</h3>
      <p>{{ t('ruleset.actions.promote_confirm') }}</p>
      
      <div class="dialog-actions">
        <button
          type="button"
          data-testid="promote-confirm"
          :disabled="promoting"
          @click="confirmPromote"
        >
          {{ promoting ? t('common.loading') : t('common.confirm') }}
        </button>
        <button
          type="button"
          @click="closePromoteDialog"
        >
          {{ t('common.cancel') }}
        </button>
      </div>
    </dialog>
  </div>
</template>

<style scoped>
.ruleset-detail h2 {
  margin-bottom: var(--rd-space-3);
}

.status-badge {
  display: inline-block;
  padding: var(--rd-space-2);
  margin-bottom: var(--rd-space-3);
  background: var(--rd-color-neutral-100);
  border-radius: var(--rd-radius-1);
}

.form-group {
  margin-bottom: var(--rd-space-3);
}

.form-group label {
  display: block;
  font-weight: var(--rd-font-weight-medium);
  margin-bottom: var(--rd-space-1);
}

.form-group input,
.form-group textarea,
.form-group select {
  width: 100%;
  padding: var(--rd-space-2);
  border: 1px solid var(--rd-color-neutral-300);
  border-radius: var(--rd-radius-1);
}

.form-group input[aria-invalid="true"],
.form-group select[aria-invalid="true"] {
  border-color: var(--rd-color-error);
}

.error-message {
  padding: var(--rd-space-2);
  margin-bottom: var(--rd-space-3);
  background: var(--rd-color-error-light);
  color: var(--rd-color-error);
  border-radius: var(--rd-radius-1);
}

.success-message {
  padding: var(--rd-space-2);
  margin-bottom: var(--rd-space-3);
  background: var(--rd-color-success-light);
  color: var(--rd-color-success);
  border-radius: var(--rd-radius-1);
}

.validation-error {
  padding: var(--rd-space-2);
  margin-bottom: var(--rd-space-3);
  color: var(--rd-color-error);
}

.action-buttons {
  display: flex;
  gap: var(--rd-space-2);
  margin-top: var(--rd-space-4);
}

.action-buttons button {
  padding: var(--rd-space-2) var(--rd-space-3);
}

dialog {
  padding: var(--rd-space-4);
  border: 1px solid var(--rd-color-neutral-300);
  border-radius: var(--rd-radius-2);
  box-shadow: var(--rd-shadow-lg);
}

dialog h3 {
  margin-top: 0;
  margin-bottom: var(--rd-space-3);
}

.dialog-actions {
  display: flex;
  gap: var(--rd-space-2);
  margin-top: var(--rd-space-4);
}
</style>
