<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { createApiClient, createOperatorApi } from '../../api'
import {
  resolveOperatorBlockId,
  resolveOperatorDeviceId,
  resolveOperatorStation,
  resolveOperatorToken
} from '../../operatorContext'
import {
  normalizeCaptureSession,
  normalizeCaptureSessionList,
  summarizeCaptureSessionSyncState
} from '../../operatorCaptureSessions'
import {
  buildOperatorSessionWorkspacePath,
  buildOperatorSessionsPath,
  clearSelectedCaptureSessionId,
  getSelectedCaptureSessionId,
  setSelectedCaptureSessionId
} from '../../operatorSessionSelection'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const regattaId = computed(() => String(route.params.regattaId || ''))
const isLoading = ref(false)
const errorMessage = ref('')
const captureSessions = ref([])

const operatorToken = computed(() => resolveOperatorToken())
const operatorStation = computed(() => resolveOperatorStation())

const operatorApi = createOperatorApi(createApiClient(), {
  getOperatorToken: () => operatorToken.value
})

function summarizeSyncState(session) {
  return summarizeCaptureSessionSyncState(session, t)
}

async function loadCaptureSessions() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    captureSessions.value = normalizeCaptureSessionList(await operatorApi.listCaptureSessions(regattaId.value))
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : 'Failed to load capture sessions.'
  } finally {
    isLoading.value = false
  }
}

async function openCaptureSession(captureSessionId) {
  setSelectedCaptureSessionId(regattaId.value, captureSessionId)
  await router.push(buildOperatorSessionWorkspacePath(regattaId.value, captureSessionId))
}

async function createCaptureSession() {
  errorMessage.value = ''

  try {
    const blockId = resolveOperatorBlockId()
    if (!blockId) {
      errorMessage.value = t('operator.regatta.missing_block_scope')
      return
    }

    const createdSession = await operatorApi.createCaptureSession(regattaId.value, {
      block_id: blockId,
      station: operatorStation.value,
      device_id: resolveOperatorDeviceId(),
      session_type: 'finish',
      fps: 25
    })
    const normalizedSession = normalizeCaptureSession(createdSession)
    if (!normalizedSession) {
      errorMessage.value = t('operator.regatta.create_failed')
      return
    }

    captureSessions.value = [normalizedSession, ...captureSessions.value]
    await openCaptureSession(normalizedSession.id)
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('operator.regatta.create_failed')
  }
}

async function closeCaptureSession(captureSessionId) {
  errorMessage.value = ''

  try {
    const updatedSession = normalizeCaptureSession(await operatorApi.closeCaptureSession(regattaId.value, captureSessionId, {
      close_reason: 'capture_complete'
    }))
    if (!updatedSession) {
      errorMessage.value = t('operator.regatta.close_failed')
      return
    }
    captureSessions.value = captureSessions.value.map((session) =>
      session.id === captureSessionId ? updatedSession : session
    )

    if (getSelectedCaptureSessionId(regattaId.value) === captureSessionId) {
      clearSelectedCaptureSessionId(regattaId.value, captureSessionId)
    }

    await router.push(buildOperatorSessionsPath(regattaId.value))
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('operator.regatta.close_failed')
  }
}

onMounted(() => {
  loadCaptureSessions()
})
</script>

<template>
  <div class="operator-regatta-detail">
    <h2>{{ t('operator.regatta.title') }}</h2>
    <p>{{ t('operator.regatta.id') }}: {{ regattaId }}</p>
    <p data-testid="operator-token-status">
      {{ t('operator.regatta.token_status', { token: operatorToken || t('operator.regatta.no_token') }) }}
    </p>
    <p data-testid="operator-station-context">
      {{ t('operator.regatta.station_context', { station: operatorStation }) }}
    </p>

    <nav class="operator-regatta-detail__actions" aria-label="Regatta actions">
      <button
        type="button"
        class="operator-regatta-detail__action"
        data-testid="capture-session-create"
        @click="createCaptureSession"
      >
        {{ t('operator.regatta.create_session') }}
      </button>
    </nav>

    <p v-if="isLoading">{{ t('operator.regatta.loading_sessions') }}</p>
    <p v-if="errorMessage" class="operator-regatta-detail__error">{{ errorMessage }}</p>

    <ul
      v-if="captureSessions.length > 0"
      class="operator-regatta-detail__sessions"
      data-testid="capture-session-list"
    >
      <li
        v-for="session in captureSessions"
        :key="session.id"
        class="operator-regatta-detail__session"
      >
        <div class="operator-regatta-detail__session-summary">
          <strong>{{ session.id }}</strong>
          <p>{{ t('operator.regatta.session_summary', session) }}</p>
          <p :data-testid="`capture-session-sync-summary-${session.id}`">
            {{ summarizeSyncState(session) }}
          </p>
        </div>
        <div class="operator-regatta-detail__session-actions">
          <button
            type="button"
            :data-testid="`capture-session-select-${session.id}`"
            @click="openCaptureSession(session.id)"
          >
            {{ t('operator.regatta.open_session') }}
          </button>
          <button
            v-if="session.state !== 'closed'"
            type="button"
            :data-testid="`capture-session-close-${session.id}`"
            @click="closeCaptureSession(session.id)"
          >
            {{ t('operator.regatta.close_session') }}
          </button>
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.operator-regatta-detail h2 {
  margin-bottom: var(--rd-space-3);
  font-size: 1.75rem;
}

.operator-regatta-detail__actions {
  margin-top: var(--rd-space-5);
  display: flex;
  gap: var(--rd-space-4);
}

.operator-regatta-detail__action,
.operator-regatta-detail__session-actions button {
  display: inline-flex;
  align-items: center;
  padding: var(--rd-space-3) var(--rd-space-5);
  background: var(--rd-accent);
  color: white;
  text-decoration: none;
  border-radius: 4px;
  font-size: 1rem;
  font-weight: 600;
  min-height: var(--rd-hit-operator);
  border: 0;
  cursor: pointer;
}

.operator-regatta-detail__action:hover,
.operator-regatta-detail__session-actions button:hover {
  opacity: 0.9;
}

.operator-regatta-detail__sessions {
  margin-top: var(--rd-space-5);
  padding: 0;
  list-style: none;
  display: grid;
  gap: var(--rd-space-4);
}

.operator-regatta-detail__session {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--rd-space-4);
  padding: var(--rd-space-4);
  border: 1px solid var(--rd-border);
  border-radius: 4px;
  background: var(--rd-surface);
}

.operator-regatta-detail__session-summary p {
  margin: var(--rd-space-1) 0 0;
}

.operator-regatta-detail__session-actions {
  display: flex;
  gap: var(--rd-space-3);
}

.operator-regatta-detail__error {
  color: var(--rd-danger);
}
</style>
