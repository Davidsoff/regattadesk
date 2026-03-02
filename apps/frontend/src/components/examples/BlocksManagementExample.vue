<template>
  <div class="blocks-management">
    <h2>Blocks Management</h2>
    
    <!-- Immutability Warning Banner -->
    <div v-if="isDrawPublished" class="warning-banner" role="alert">
      <span class="icon">🔒</span>
      <div class="message">
        <strong>Draw Published:</strong> Blocks are immutable.
        {{ immutabilityMessage }}
      </div>
    </div>

    <div class="blocks-list">
      <div v-for="block in blocks" :key="block.id" class="block-item">
        <div class="block-header">
          <h3>{{ block.name }}</h3>
          <span v-if="isDrawPublished" class="immutable-indicator" title="Cannot edit after draw publication">
            🔒 Locked
          </span>
        </div>
        
        <div class="block-details">
          <p>Start Time: {{ block.startTime }}</p>
          <p>Crew Interval: {{ block.crewInterval }}s</p>
          <p>Event Interval: {{ block.eventInterval }}s</p>
        </div>

        <div class="block-actions">
          <button 
            class="btn-edit"
            :disabled="!canEdit"
            :title="canEdit ? 'Edit block' : immutabilityMessage"
            @click="editBlock(block)"
          >
            Edit
          </button>
          <button 
            class="btn-delete"
            :disabled="!canEdit"
            :title="canEdit ? 'Delete block' : immutabilityMessage"
            @click="deleteBlock(block)"
          >
            Delete
          </button>
        </div>
      </div>
    </div>

    <button 
      class="btn-add"
      :disabled="!canEdit"
      :title="canEdit ? 'Add new block' : immutabilityMessage"
      @click="addBlock"
    >
      Add Block
    </button>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { isDrawPublished as checkDrawPublished, canEditAfterDraw, getImmutabilityMessage } from '../../composables/useDrawImmutability'

/**
 * Example stub component demonstrating immutability guards for blocks.
 * 
 * Key patterns:
 * 1. Use isDrawPublished to check publication state
 * 2. Use canEditAfterDraw to enable/disable editing
 * 3. Use getImmutabilityMessage for user-friendly tooltips
 * 4. Show visual indicators (lock icon) for immutable state
 * 5. Display warning banner when draw is published
 */

const props = defineProps({
  regatta: {
    type: Object,
    required: true
  }
})

// Sample blocks data (in real implementation, fetch from API)
const blocks = ref([
  {
    id: '1',
    name: 'Block A',
    startTime: '08:00',
    crewInterval: 30,
    eventInterval: 120
  },
  {
    id: '2',
    name: 'Block B',
    startTime: '10:00',
    crewInterval: 30,
    eventInterval: 120
  }
])

// Immutability state
const isDrawPublished = computed(() => checkDrawPublished(props.regatta))
const canEdit = computed(() => canEditAfterDraw(props.regatta))
const immutabilityMessage = computed(() => getImmutabilityMessage(props.regatta, 'blocks'))

// Actions (stubs)
function editBlock(block) {
  if (!canEdit.value) {
    alert(immutabilityMessage.value)
    return
  }
  console.log('Edit block:', block)
}

function deleteBlock(block) {
  if (!canEdit.value) {
    alert(immutabilityMessage.value)
    return
  }
  console.log('Delete block:', block)
}

function addBlock() {
  if (!canEdit.value) {
    alert(immutabilityMessage.value)
    return
  }
  console.log('Add new block')
}
</script>

<style scoped>
.blocks-management {
  padding: 1rem;
}

.warning-banner {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem;
  margin-bottom: 1.5rem;
  background-color: #fff3cd;
  border: 1px solid #ffc107;
  border-radius: 4px;
  color: #856404;
}

.warning-banner .icon {
  font-size: 1.5rem;
  flex-shrink: 0;
}

.warning-banner .message {
  flex: 1;
}

.blocks-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin-bottom: 1rem;
}

.block-item {
  padding: 1rem;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  background-color: #fff;
}

.block-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.block-header h3 {
  margin: 0;
  font-size: 1.125rem;
}

.immutable-indicator {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.25rem 0.5rem;
  background-color: #f8f9fa;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  font-size: 0.875rem;
  color: #495057;
}

.block-details {
  margin-bottom: 1rem;
}

.block-details p {
  margin: 0.25rem 0;
  font-size: 0.875rem;
  color: #495057;
}

.block-actions {
  display: flex;
  gap: 0.5rem;
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

.btn-delete {
  background-color: #dc3545;
  border-color: #dc3545;
  color: white;
}

.btn-delete:hover:not(:disabled) {
  background-color: #c82333;
  border-color: #bd2130;
}

.btn-add {
  background-color: #1e7e34;
  border-color: #1e7e34;
  color: white;
}

.btn-add:hover:not(:disabled) {
  background-color: #218838;
  border-color: #1e7e34;
}
</style>
