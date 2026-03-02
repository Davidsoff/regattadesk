<template>
  <div class="bib-pool-form">
    <h3>{{ isEditing ? 'Edit' : 'Add' }} Bib Pool</h3>

    <!-- Immutability warning -->
    <div v-if="isDrawPublished" class="warning-banner" role="alert">
      <span class="icon">🔒</span>
      <div>
        <strong>Bib pools are locked after draw publication.</strong>
        {{ immutabilityMessage }}
      </div>
    </div>

    <!-- Validation error display -->
    <div v-if="validationError" class="validation-error" role="alert">
      <div class="error-header">
        <span class="error-icon">⚠️</span>
        <strong>Validation Error</strong>
      </div>
      <div class="error-content">
        <p class="error-message">{{ validationError.message }}</p>
        
        <div v-if="validationError.overlappingBibs.length > 0" class="overlap-details">
          <p>
            <strong>Overlapping bib numbers:</strong>
            <span class="bib-numbers">{{ formattedOverlappingBibs }}</span>
          </p>
          
          <p v-if="validationError.conflictingPoolName">
            <strong>Conflicts with:</strong>
            {{ validationError.conflictingPoolName }}
          </p>
          
          <p class="error-action">
            Change the bib range or delete the conflicting pool to proceed.
          </p>
        </div>
      </div>
    </div>

    <form @submit.prevent="handleSubmit">
      <div class="form-group">
        <label for="pool-name">Pool Name</label>
        <input
          id="pool-name"
          v-model="formData.name"
          type="text"
          :disabled="!canEdit"
          required
        />
      </div>

      <div class="form-row">
        <div class="form-group">
          <label for="start-bib">Start Bib</label>
          <input
            id="start-bib"
            v-model.number="formData.startBib"
            type="number"
            :disabled="!canEdit"
            :class="{ 'has-error': hasOverlapError }"
            min="1"
            required
          />
        </div>

        <div class="form-group">
          <label for="end-bib">End Bib</label>
          <input
            id="end-bib"
            v-model.number="formData.endBib"
            type="number"
            :disabled="!canEdit"
            :class="{ 'has-error': hasOverlapError }"
            min="1"
            required
          />
        </div>
      </div>

      <div class="form-actions">
        <button
          type="submit"
          class="btn-submit"
          :disabled="!canEdit || isSubmitting"
        >
          {{ isSubmitting ? 'Saving...' : 'Save' }}
        </button>
        <button
          type="button"
          class="btn-cancel"
          @click="handleCancel"
        >
          Cancel
        </button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { isDrawPublished as checkDrawPublished, canEditAfterDraw, getImmutabilityMessage } from '../../composables/useDrawImmutability'
import { 
  parseBibPoolValidationError, 
  formatOverlappingBibs,
  isBibPoolValidationError 
} from '../../composables/useBibPoolValidation'

/**
 * Example stub component demonstrating:
 * 1. Bib pool validation error display
 * 2. Overlapping bib numbers formatting
 * 3. Conflicting pool information display
 * 4. Form field error highlighting
 * 5. Immutability guards for bib pools
 * 
 * Key patterns:
 * - Parse validation errors with parseBibPoolValidationError()
 * - Format bib numbers with formatOverlappingBibs()
 * - Highlight invalid fields with CSS classes
 * - Show clear call-to-action for fixing errors
 */

const props = defineProps({
  regatta: {
    type: Object,
    required: true
  },
  pool: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['save', 'cancel'])

// Form state
const isEditing = computed(() => props.pool !== null)
const isSubmitting = ref(false)
const validationError = ref(null)

const formData = ref({
  name: props.pool?.name || '',
  startBib: props.pool?.startBib || 1,
  endBib: props.pool?.endBib || 50
})

// Immutability state
const isDrawPublished = computed(() => checkDrawPublished(props.regatta))
const canEdit = computed(() => canEditAfterDraw(props.regatta))
const immutabilityMessage = computed(() => 
  getImmutabilityMessage(props.regatta, 'bib pools')
)

// Validation error state
const hasOverlapError = computed(() => {
  return validationError.value?.overlappingBibs?.length > 0
})

const formattedOverlappingBibs = computed(() => {
  if (!validationError.value) return ''
  return formatOverlappingBibs(validationError.value.overlappingBibs)
})

// Form handlers
async function handleSubmit() {
  if (!canEdit.value) {
    alert(immutabilityMessage.value)
    return
  }

  validationError.value = null
  isSubmitting.value = true

  try {
    // In real implementation, call API
    // await api.saveBibPool(formData.value)
    
    // Simulate validation error for demonstration
    // Remove this in real implementation
    if (formData.value.startBib === 50) {
      const validationErr = new Error('Bib range overlaps with existing pool')
      validationErr.code = 'BIB_POOL_VALIDATION_ERROR'
      validationErr.details = {
        overlapping_bibs: [50, 51, 52, 100],
        conflicting_pool_id: 'pool-123',
        conflicting_pool_name: 'Block A Pool'
      }
      throw validationErr
    }

    emit('save', formData.value)
  } catch (error) {
    // Parse validation error if it's a bib pool validation error
    if (isBibPoolValidationError(error)) {
      validationError.value = parseBibPoolValidationError(error)
    } else {
      // Handle other errors
      alert(error.message || 'Failed to save bib pool')
    }
  } finally {
    isSubmitting.value = false
  }
}

function handleCancel() {
  validationError.value = null
  emit('cancel')
}
</script>

<style scoped>
.bib-pool-form {
  padding: 1.5rem;
  background-color: #fff;
  border: 1px solid #dee2e6;
  border-radius: 4px;
}

.bib-pool-form h3 {
  margin-top: 0;
  margin-bottom: 1rem;
}

.warning-banner {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 1rem;
  margin-bottom: 1rem;
  background-color: #fff3cd;
  border: 1px solid #ffc107;
  border-radius: 4px;
  color: #856404;
}

.warning-banner .icon {
  font-size: 1.5rem;
  flex-shrink: 0;
}

.validation-error {
  margin-bottom: 1rem;
  padding: 1rem;
  background-color: #f8d7da;
  border: 1px solid #f5c6cb;
  border-radius: 4px;
  color: #721c24;
}

.error-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

.error-icon {
  font-size: 1.25rem;
}

.error-content {
  font-size: 0.875rem;
}

.error-message {
  margin: 0 0 0.75rem 0;
  font-weight: 600;
}

.overlap-details {
  margin-top: 0.75rem;
}

.overlap-details p {
  margin: 0.5rem 0;
}

.bib-numbers {
  display: inline-block;
  padding: 0.25rem 0.5rem;
  margin-left: 0.5rem;
  background-color: #fff;
  border: 1px solid #f5c6cb;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-weight: 600;
  color: #721c24;
}

.error-action {
  margin-top: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid #f5c6cb;
  font-style: italic;
  color: #6c757d;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.25rem;
  font-weight: 600;
  font-size: 0.875rem;
  color: #495057;
}

.form-group input {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #ced4da;
  border-radius: 4px;
  font-size: 0.875rem;
}

.form-group input:focus {
  outline: none;
  border-color: #80bdff;
  box-shadow: 0 0 0 0.2rem rgba(0, 123, 255, 0.25);
}

.form-group input:disabled {
  background-color: #e9ecef;
  cursor: not-allowed;
}

.form-group input.has-error {
  border-color: #dc3545;
}

.form-group input.has-error:focus {
  border-color: #dc3545;
  box-shadow: 0 0 0 0.2rem rgba(220, 53, 69, 0.25);
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.form-actions {
  display: flex;
  gap: 0.75rem;
  margin-top: 1.5rem;
}

button {
  padding: 0.5rem 1rem;
  border: 1px solid;
  border-radius: 4px;
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.2s;
}

button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-submit {
  background-color: #1e7e34;
  border-color: #1e7e34;
  color: white;
}

.btn-submit:hover:not(:disabled) {
  background-color: #218838;
  border-color: #1e7e34;
}

.btn-cancel {
  background-color: #6c757d;
  border-color: #6c757d;
  color: white;
}

.btn-cancel:hover {
  background-color: #5a6268;
  border-color: #545b62;
}
</style>
