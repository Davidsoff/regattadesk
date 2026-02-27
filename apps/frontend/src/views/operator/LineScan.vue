<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { ApiError, createApiClient, createOperatorApi } from '../../api'

const { t } = useI18n()
const route = useRoute()

const handoff = ref(null)
const handoffPin = ref('')
const revealedPin = ref('')
const invalidPinError = ref(false)
const conflictError = ref(false)
const handoffErrorMessage = ref('')
const operatorAccessMode = ref('active')
const isSubmitting = ref(false)
const DEVICE_ID_STORAGE_KEY = 'rd_operator_device_id'
const STATION_STORAGE_KEY = 'rd_operator_station'

function resolveOperatorDeviceId() {
  const storage = globalThis.window?.localStorage
  if (typeof storage?.getItem !== 'function' || typeof storage?.setItem !== 'function') {
    return 'operator-device'
  }

  const existing = (storage.getItem(DEVICE_ID_STORAGE_KEY) || '').trim()
  if (existing) {
    return existing
  }

  const generated =
    typeof globalThis.crypto?.randomUUID === 'function'
      ? globalThis.crypto.randomUUID()
      : 'operator-device'
  storage.setItem(DEVICE_ID_STORAGE_KEY, generated)
  return generated
}

const operatorToken = computed(() => {
  const contextToken =
    typeof globalThis.__REGATTADESK_AUTH__?.operatorToken === 'string'
      ? globalThis.__REGATTADESK_AUTH__.operatorToken.trim()
      : ''
  const storageToken =
    typeof globalThis.window?.localStorage?.getItem === 'function'
      ? (globalThis.window.localStorage.getItem('rd_operator_token') || '').trim()
      : ''

  return contextToken || storageToken
})

const operatorApi = createOperatorApi(createApiClient(), {
  getOperatorToken: () => operatorToken.value
})
const currentDeviceId = ref(resolveOperatorDeviceId())
const operatorStation = computed(() => {
  const contextStation =
    typeof globalThis.__REGATTADESK_AUTH__?.operatorStation === 'string'
      ? globalThis.__REGATTADESK_AUTH__.operatorStation.trim()
      : ''
  const storageStation =
    typeof globalThis.window?.localStorage?.getItem === 'function'
      ? (globalThis.window.localStorage.getItem(STATION_STORAGE_KEY) || '').trim()
      : ''

  return contextStation || storageStation || 'finish-line'
})

const showHandoffToast = computed(() => handoff.value?.status === 'pending')
const showReadOnlyBanner = computed(() => operatorAccessMode.value === 'read_only')
const isCaptureDisabled = computed(() => operatorAccessMode.value === 'read_only')

async function requestHandoff() {
  if (isSubmitting.value) {
    return
  }

  const regattaId = route.params.regattaId

  if (!operatorToken.value) {
    handoffErrorMessage.value = 'Operator token is required to request handoff.'
    return
  }

  handoffErrorMessage.value = ''
  isSubmitting.value = true

  try {
    handoff.value = await operatorApi.requestStationHandoff(regattaId, {
      station: operatorStation.value,
      requesting_device_id: currentDeviceId.value
    })
  } catch (error) {
    handoffErrorMessage.value = error instanceof Error ? error.message : 'Failed to request handoff'
  } finally {
    isSubmitting.value = false
  }
}

async function revealPin() {
  if (!handoff.value || isSubmitting.value) {
    return
  }

  if (!operatorToken.value) {
    handoffErrorMessage.value = 'Operator token is required to reveal PIN.'
    return
  }

  handoffErrorMessage.value = ''
  isSubmitting.value = true

  try {
    const response = await operatorApi.revealStationHandoffPin(route.params.regattaId, handoff.value.id)
    revealedPin.value = response?.pin || ''
  } catch (error) {
    handoffErrorMessage.value = error instanceof Error ? error.message : 'Failed to reveal PIN'
  } finally {
    isSubmitting.value = false
  }
}

async function getHandoffStatus() {
  if (!handoff.value || isSubmitting.value) {
    return
  }

  if (!operatorToken.value) {
    handoffErrorMessage.value = 'Operator token is required to refresh handoff status.'
    return
  }

  handoffErrorMessage.value = ''
  isSubmitting.value = true

  try {
    handoff.value = await operatorApi.getStationHandoffStatus(route.params.regattaId, handoff.value.id)
  } catch (error) {
    handoffErrorMessage.value = error instanceof Error ? error.message : 'Failed to refresh handoff status'
  } finally {
    isSubmitting.value = false
  }
}

async function cancelHandoff() {
  if (!handoff.value || isSubmitting.value) {
    return
  }

  if (!operatorToken.value) {
    handoffErrorMessage.value = 'Operator token is required to cancel handoff.'
    return
  }

  handoffErrorMessage.value = ''
  isSubmitting.value = true

  try {
    handoff.value = await operatorApi.cancelStationHandoff(route.params.regattaId, handoff.value.id)
  } catch (error) {
    handoffErrorMessage.value = error instanceof Error ? error.message : 'Failed to cancel handoff'
  } finally {
    isSubmitting.value = false
  }
}

async function completeHandoff() {
  if (!handoff.value || isSubmitting.value) {
    return
  }

  invalidPinError.value = false
  conflictError.value = false
  handoffErrorMessage.value = ''
  isSubmitting.value = true

  try {
    if (!operatorToken.value) {
      handoffErrorMessage.value = 'Operator token is required to complete handoff.'
      return
    }

    const response = await operatorApi.completeStationHandoff(route.params.regattaId, handoff.value.id, {
      pin: handoffPin.value,
      completing_device_id: currentDeviceId.value
    })
    handoff.value = response
    if (response?.new_device_mode === 'active' || response?.new_device_mode === 'read_only') {
      operatorAccessMode.value = response.new_device_mode
    } else {
      operatorAccessMode.value =
        response?.active_device_id && response.active_device_id !== currentDeviceId.value
          ? 'read_only'
          : 'active'
    }
  } catch (error) {
    if (error instanceof ApiError && error.status === 400 && error.code === 'INVALID_PIN') {
      invalidPinError.value = true
    } else if (error instanceof ApiError && error.status === 409) {
      conflictError.value = true
    } else {
      handoffErrorMessage.value = error instanceof Error ? error.message : 'Handoff failed'
    }
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="operator-line-scan">
    <h2>{{ t('operator.line_scan.title') }}</h2>
    <p>{{ t('operator.line_scan.description') }}</p>
    <p>{{ t('operator.regatta.id') }}: {{ route.params.regattaId }}</p>

    <div v-if="showReadOnlyBanner" data-testid="operator-readonly-banner" class="readonly-banner">
      <strong>Read-only:</strong> Station control moved to another device, re-auth to take control.
    </div>

    <div class="handoff-start">
      <button type="button" data-testid="handoff-request-start" @click="requestHandoff">
        Request station handoff
      </button>
    </div>

    <div v-if="showHandoffToast" data-testid="handoff-request-toast" class="handoff-toast">
      <span>Station handoff requested. Show PIN to continue.</span>
      <button type="button" @click="revealPin">Show PIN</button>
      <button type="button" @click="cancelHandoff">Cancel</button>
      <button type="button" @click="getHandoffStatus">Refresh</button>
      <span v-if="revealedPin" data-testid="handoff-revealed-pin">PIN: {{ revealedPin }}</span>
    </div>

    <div class="handoff-actions">
      <label for="handoff-pin-input">Handoff PIN</label>
      <input
        id="handoff-pin-input"
        data-testid="handoff-pin-input"
        v-model="handoffPin"
        inputmode="numeric"
        autocomplete="one-time-code"
      />
      <button
        type="button"
        data-testid="handoff-complete-submit"
        :disabled="!handoff || isSubmitting"
        @click="completeHandoff"
      >
        Complete handoff
      </button>
    </div>

    <p v-if="invalidPinError" data-testid="handoff-invalid-pin-error" class="handoff-error">
      Invalid PIN
    </p>
    <p v-if="conflictError" data-testid="handoff-conflict-banner" class="handoff-conflict">
      This handoff is no longer available
    </p>
    <p v-if="handoffErrorMessage" class="handoff-error">{{ handoffErrorMessage }}</p>

    <div class="line-scan-actions">
      <button type="button" data-testid="line-scan-capture" :disabled="isCaptureDisabled">
        Capture frame
      </button>
      <span v-if="operatorToken" class="operator-token-state">Operator token active</span>
    </div>
  </div>
</template>

<style scoped>
.operator-line-scan h2 {
  margin-bottom: var(--rd-space-3);
  font-size: 1.75rem;
}

.readonly-banner,
.handoff-toast,
.handoff-actions,
.line-scan-actions {
  margin-top: var(--rd-space-3);
}

.readonly-banner {
  padding: var(--rd-space-2);
  border: 1px solid var(--rd-color-warning, #e1b100);
  background: var(--rd-color-warning-soft, #fff7d1);
}

.handoff-toast {
  display: flex;
  flex-wrap: wrap;
  gap: var(--rd-space-2);
  align-items: center;
  padding: var(--rd-space-2);
  border: 1px solid var(--rd-color-border, #8a8a8a);
}

.handoff-actions {
  display: flex;
  gap: var(--rd-space-2);
  align-items: center;
}

.handoff-error {
  color: #b30000;
}

.handoff-conflict {
  padding: var(--rd-space-2);
  border: 1px solid #b30000;
}

.line-scan-actions {
  display: flex;
  gap: var(--rd-space-2);
  align-items: center;
}
</style>
