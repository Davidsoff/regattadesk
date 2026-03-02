<template>
  <div class="ruleset-detail">
    <div class="header">
      <h2>{{ ruleset.name }}</h2>
      <span v-if="ruleset.is_global" class="badge-global">Global Ruleset</span>
      <span v-else class="badge-local">Regatta Ruleset</span>
    </div>

    <div class="ruleset-info">
      <div class="info-section">
        <h3>Details</h3>
        <dl>
          <dt>Age Calculation:</dt>
          <dd>{{ ruleset.ageCalculation }}</dd>
          
          <dt>Gender Validation:</dt>
          <dd>{{ ruleset.genderValidation ? 'Enabled' : 'Disabled' }}</dd>
          
          <dt>Age Range:</dt>
          <dd>
            Min: {{ ruleset.minAge || 'None' }} - Max: {{ ruleset.maxAge || 'None' }}
          </dd>
        </dl>
      </div>

      <!-- Immutability warning for published draws -->
      <div v-if="isDrawPublished" class="warning-box" role="alert">
        <span class="icon">🔒</span>
        <div>
          <strong>Ruleset Locked:</strong>
          {{ immutabilityMessage }}
        </div>
      </div>
    </div>

    <div class="actions">
      <button 
        class="btn-edit"
        :disabled="!canEdit"
        :title="canEdit ? 'Edit ruleset' : immutabilityMessage"
        @click="editRuleset"
      >
        Edit
      </button>

      <button 
        class="btn-duplicate"
        @click="duplicateRuleset"
      >
        Duplicate
      </button>

      <!-- Super Admin Only: Promote to Global -->
      <button 
        v-if="showPromoteButton"
        class="btn-promote"
        :disabled="!canPromote"
        :title="getPromoteTooltip()"
        @click="promoteToGlobal"
      >
        Promote to Global
      </button>

      <!-- Error display for unauthorized attempts -->
      <div v-if="promoteError" class="error-message" role="alert">
        {{ promoteError }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserRole } from '../../composables/useUserRole'
import { isDrawPublished as checkDrawPublished, canEditAfterDraw, getImmutabilityMessage } from '../../composables/useDrawImmutability'

/**
 * Example stub component demonstrating:
 * 1. Role-based visibility (promote button only for super_admin)
 * 2. Immutability guards for rulesets after draw publication
 * 3. 403 error handling with role-specific messages
 * 
 * Key patterns:
 * - Use useUserRole() to check user role
 * - Hide super_admin features from non-super_admin users
 * - Show helpful error messages for unauthorized actions
 * - Combine role checks with immutability checks
 */

const props = defineProps({
  ruleset: {
    type: Object,
    required: true
  },
  regatta: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['edit', 'duplicate', 'promote'])

// Role management
const { isSuperAdmin, loadRole } = useUserRole()
const promoteError = ref(null)

onMounted(async () => {
  await loadRole()
})

// Immutability state
const isDrawPublished = computed(() => checkDrawPublished(props.regatta))
const canEdit = computed(() => canEditAfterDraw(props.regatta))
const immutabilityMessage = computed(() => 
  getImmutabilityMessage(props.regatta, 'ruleset')
)

// Role-based visibility
const showPromoteButton = computed(() => {
  // Only show promote button if:
  // 1. User is super_admin
  // 2. Ruleset is not already global
  return isSuperAdmin.value && !props.ruleset.is_global
})

const canPromote = computed(() => {
  // Can promote if:
  // 1. User is super_admin
  // 2. Ruleset is not already global
  // 3. Draw is not published (or allow promotion regardless?)
  return isSuperAdmin.value && !props.ruleset.is_global
})

function getPromoteTooltip() {
  if (!isSuperAdmin.value) {
    return 'This action requires super_admin role'
  }
  if (props.ruleset.is_global) {
    return 'Already a global ruleset'
  }
  return 'Promote this ruleset to global catalog'
}

// Actions
function editRuleset() {
  if (!canEdit.value) {
    alert(immutabilityMessage.value)
    return
  }
  emit('edit', props.ruleset)
}

function duplicateRuleset() {
  // Duplication is always allowed (creates new ruleset)
  emit('duplicate', props.ruleset)
}

async function promoteToGlobal() {
  promoteError.value = null

  if (!isSuperAdmin.value) {
    promoteError.value = 'This action requires super_admin role. Please contact an administrator.'
    return
  }

  try {
    // In real implementation, call API
    // await api.promoteRuleset(props.ruleset.id)

    emit('promote', props.ruleset)
  } catch (error) {
    if (error.status === 403) {
      promoteError.value = 'This action requires super_admin role. Please contact an administrator.'
    } else {
      promoteError.value = error.message || 'Failed to promote ruleset'
    }
  }
}
</script>

<style scoped>
.ruleset-detail {
  padding: 1.5rem;
  background-color: #fff;
  border: 1px solid #dee2e6;
  border-radius: 4px;
}

.header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.header h2 {
  margin: 0;
}

.badge-global,
.badge-local {
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
}

.badge-global {
  background-color: #d1ecf1;
  color: #0c5460;
}

.badge-local {
  background-color: #f8f9fa;
  color: #495057;
}

.ruleset-info {
  margin-bottom: 1.5rem;
}

.info-section h3 {
  margin-top: 0;
  margin-bottom: 0.75rem;
  font-size: 1rem;
  color: #495057;
}

.info-section dl {
  display: grid;
  grid-template-columns: 150px 1fr;
  gap: 0.5rem;
  margin: 0;
}

.info-section dt {
  font-weight: 600;
  color: #495057;
}

.info-section dd {
  margin: 0;
  color: #6c757d;
}

.warning-box {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 1rem;
  margin-top: 1rem;
  background-color: #fff3cd;
  border: 1px solid #ffc107;
  border-radius: 4px;
  color: #856404;
}

.warning-box .icon {
  font-size: 1.5rem;
  flex-shrink: 0;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: center;
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

.btn-edit {
  background-color: #0056b3;
  border-color: #0056b3;
  color: white;
}

.btn-edit:hover:not(:disabled) {
  background-color: #0056b3;
  border-color: #0056b3;
}

.btn-duplicate {
  background-color: #6c757d;
  border-color: #6c757d;
  color: white;
}

.btn-duplicate:hover {
  background-color: #5a6268;
  border-color: #545b62;
}

.btn-promote {
  background-color: #1e7e34;
  border-color: #1e7e34;
  color: white;
}

.btn-promote:hover:not(:disabled) {
  background-color: #218838;
  border-color: #1e7e34;
}

.error-message {
  flex-basis: 100%;
  padding: 0.75rem;
  background-color: #f8d7da;
  border: 1px solid #f5c6cb;
  border-radius: 4px;
  color: #721c24;
  font-size: 0.875rem;
}
</style>
