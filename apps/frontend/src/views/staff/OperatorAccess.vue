<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { createApiClient, createStaffOperatorAccessApi } from '../../api'
import { useUserRole } from '../../composables/useUserRole'

const { t } = useI18n()
const route = useRoute()
const regattaId = computed(() => route.params.regattaId)

const operatorAccessApi = createStaffOperatorAccessApi(createApiClient())
const { role, isSuperAdmin, isRegattaAdmin, loadRole } = useUserRole()

const loading = ref(true)
const errorMessage = ref('')
const actionMessage = ref('')
const tokens = ref([])
const pendingHandoffs = ref([])
const handoffError = ref('')
const handoffLoading = ref(true)

const tokenForm = reactive({
  station: '',
  block_id: '',
  valid_from: '',
  valid_until: '',
})

const handoffFilters = reactive({
  station: '',
  token_id: '',
})

const pendingTokenAction = ref(null)

const canManageOperatorAccess = computed(() => isSuperAdmin.value || isRegattaAdmin.value)
const showAuthorizationMessage = computed(() => role.value !== null && !canManageOperatorAccess.value)

function toIsoString(value) {
  if (!value) {
    return null
  }

  return new Date(value).toISOString()
}

function isInvalidValidityWindow(validFrom, validUntil) {
  if (!validFrom || !validUntil) {
    return false
  }

  return new Date(validUntil).getTime() < new Date(validFrom).getTime()
}

function downloadBlob(blob, filename) {
  const objectUrl = globalThis.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  globalThis.URL.revokeObjectURL(objectUrl)
}

async function loadTokens() {
  loading.value = true
  errorMessage.value = ''

  try {
    const response = await operatorAccessApi.listTokens(regattaId.value)
    tokens.value = Array.isArray(response?.data) ? response.data : []
  } catch (error) {
    errorMessage.value = error?.message || t('operator_access.errors.load_tokens')
  } finally {
    loading.value = false
  }
}

async function loadPendingHandoffs() {
  handoffLoading.value = true
  handoffError.value = ''

  try {
    pendingHandoffs.value = await operatorAccessApi.listPendingHandoffs(regattaId.value, {
      station: handoffFilters.station,
      token_id: handoffFilters.token_id,
    })
  } catch (error) {
    pendingHandoffs.value = []
    handoffError.value = error?.message || t('operator_access.errors.load_handoffs')
  } finally {
    handoffLoading.value = false
  }
}

async function submitTokenForm() {
  actionMessage.value = ''
  errorMessage.value = ''

  if (isInvalidValidityWindow(tokenForm.valid_from, tokenForm.valid_until)) {
    errorMessage.value = t('operator_access.errors.invalid_validity_window')
    return
  }

  try {
    await operatorAccessApi.createToken(regattaId.value, {
      station: tokenForm.station,
      block_id: tokenForm.block_id || null,
      valid_from: toIsoString(tokenForm.valid_from),
      valid_until: toIsoString(tokenForm.valid_until),
    })
    actionMessage.value = t('operator_access.messages.token_created')
    await loadTokens()
  } catch (error) {
    errorMessage.value = error?.message || t('operator_access.errors.create_token')
  }
}

async function revokeToken(tokenId) {
  actionMessage.value = ''
  errorMessage.value = ''

  try {
    await operatorAccessApi.revokeToken(regattaId.value, tokenId)
    actionMessage.value = t('operator_access.messages.token_revoked')
    await loadTokens()
  } catch (error) {
    errorMessage.value = error?.message || t('operator_access.errors.revoke_token')
  }
}

async function exportTokenPdf(tokenId) {
  actionMessage.value = ''
  errorMessage.value = ''

  try {
    const result = await operatorAccessApi.exportTokenPdf(regattaId.value, tokenId)
    if (!(result?.blob instanceof Blob)) {
      throw new TypeError(t('operator_access.errors.export_token'))
    }
    downloadBlob(result.blob, result.filename)
    actionMessage.value = t('operator_access.messages.token_exported', { filename: result.filename })
  } catch (error) {
    errorMessage.value = error?.message || t('operator_access.errors.export_token')
  }
}

async function revealAdminPin(handoffId) {
  handoffError.value = ''

  try {
    const revealedHandoff = await operatorAccessApi.adminRevealPin(regattaId.value, handoffId)
    pendingHandoffs.value = pendingHandoffs.value.map((pendingHandoff) =>
      pendingHandoff.id === handoffId ? revealedHandoff : pendingHandoff
    )
  } catch (error) {
    handoffError.value = error?.message || t('operator_access.errors.reveal_pin')
  }
}

function clearHandoffFilters() {
  handoffFilters.station = ''
  handoffFilters.token_id = ''
  void loadPendingHandoffs()
}

function requestTokenAction(action, token) {
  pendingTokenAction.value = {
    action,
    token,
  }
}

function cancelPendingTokenAction() {
  pendingTokenAction.value = null
}

function getConfirmationMessage(action, station) {
  switch (action) {
    case 'export':
      return t('operator_access.confirmation.export', { station })
    case 'revoke':
      return t('operator_access.confirmation.revoke', { station })
    default:
      return ''
  }
}

async function confirmPendingTokenAction() {
  if (!pendingTokenAction.value) {
    return
  }

  const { action, token } = pendingTokenAction.value
  pendingTokenAction.value = null

  if (action === 'export') {
    await exportTokenPdf(token.id)
    return
  }

  if (action === 'revoke') {
    await revokeToken(token.id)
  }
}

onMounted(async () => {
  await loadRole()

  if (!canManageOperatorAccess.value) {
    loading.value = false
    handoffLoading.value = false
    return
  }

  await Promise.all([loadTokens(), loadPendingHandoffs()])
})
</script>

<template>
  <section class="operator-access">
    <header class="operator-access__header">
      <div>
        <p class="operator-access__eyebrow">{{ t('navigation.operator_access') }}</p>
        <h2>{{ t('operator_access.title') }}</h2>
        <p class="operator-access__description">{{ t('operator_access.description') }}</p>
      </div>
    </header>

    <p
      v-if="showAuthorizationMessage"
      data-testid="operator-access-authorization"
      class="operator-access__authorization"
    >
      {{ t('operator_access.authorization_denied') }}
    </p>
    <p v-if="actionMessage" class="operator-access__message">{{ actionMessage }}</p>
    <p v-if="errorMessage" role="alert" class="operator-access__error">{{ errorMessage }}</p>
    <section
      v-if="pendingTokenAction"
      data-testid="token-action-confirmation"
      class="operator-access__panel"
    >
      <h3>{{ t('operator_access.confirmation.title') }}</h3>
      <p>
        {{ getConfirmationMessage(pendingTokenAction.action, pendingTokenAction.token.station) }}
      </p>
      <div class="operator-access__actions">
        <button data-testid="confirm-token-action" type="button" @click="confirmPendingTokenAction">
          {{ t('common.confirm') }}
        </button>
        <button data-testid="cancel-token-action" type="button" @click="cancelPendingTokenAction">
          {{ t('common.cancel') }}
        </button>
      </div>
    </section>

    <template v-if="canManageOperatorAccess">
      <form
        data-testid="create-token-form"
        class="operator-access__panel operator-access__form"
        @submit.prevent="submitTokenForm"
      >
        <h3>{{ t('operator_access.create.title') }}</h3>
        <label>
          <span>{{ t('operator_access.create.station') }}</span>
          <input v-model="tokenForm.station" name="station" required />
        </label>
        <label>
          <span>{{ t('operator_access.create.block_id') }}</span>
          <input v-model="tokenForm.block_id" name="block_id" />
        </label>
        <label>
          <span>{{ t('operator_access.create.valid_from') }}</span>
          <input v-model="tokenForm.valid_from" name="valid_from" type="datetime-local" required />
        </label>
        <label>
          <span>{{ t('operator_access.create.valid_until') }}</span>
          <input v-model="tokenForm.valid_until" name="valid_until" type="datetime-local" required />
        </label>
        <button type="submit">{{ t('operator_access.create.submit') }}</button>
      </form>

      <section class="operator-access__panel">
        <h3>{{ t('operator_access.tokens.title') }}</h3>
        <p class="operator-access__fallback">{{ t('operator_access.tokens.fallback_instructions') }}</p>
        <p v-if="loading">{{ t('common.loading') }}</p>
        <table v-else class="operator-access__table">
          <thead>
            <tr>
              <th>{{ t('operator_access.tokens.station') }}</th>
              <th>{{ t('operator_access.tokens.validity') }}</th>
              <th>{{ t('operator_access.tokens.status') }}</th>
              <th>{{ t('operator_access.tokens.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="token in tokens"
              :key="token.id"
              :data-testid="`token-row-${token.id}`"
            >
              <td>{{ token.station }}</td>
              <td>
                {{ t('operator_access.tokens.validity_window', {
                  start: token.valid_from,
                  end: token.valid_until
                }) }}
              </td>
              <td>{{ token.is_active ? t('operator_access.tokens.active') : t('operator_access.tokens.revoked') }}</td>
              <td class="operator-access__actions">
                <button
                  :data-testid="`export-token-${token.id}`"
                  type="button"
                  @click="requestTokenAction('export', token)"
                >
                  {{ t('operator_access.tokens.export_pdf') }}
                </button>
                <button
                  :data-testid="`revoke-token-${token.id}`"
                  type="button"
                  @click="requestTokenAction('revoke', token)"
                >
                  {{ t('operator_access.tokens.revoke') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="operator-access__panel">
        <h3>{{ t('operator_access.handoff.title') }}</h3>
        <p>{{ t('operator_access.handoff.description') }}</p>
        <form class="operator-access__handoff-form" @submit.prevent="loadPendingHandoffs">
          <label>
            <span>{{ t('operator_access.handoff.station_filter') }}</span>
            <input v-model="handoffFilters.station" name="handoff_station" />
          </label>
          <label>
            <span>{{ t('operator_access.handoff.token_filter') }}</span>
            <input v-model="handoffFilters.token_id" name="handoff_token_id" />
          </label>
          <div class="operator-access__actions">
            <button data-testid="load-pending-handoffs" type="submit">
              {{ handoffLoading ? t('common.loading') : t('operator_access.handoff.lookup_submit') }}
            </button>
            <button data-testid="clear-handoff-filters" type="button" @click="clearHandoffFilters">
              {{ t('operator_access.handoff.clear_filters') }}
            </button>
          </div>
        </form>

        <p v-if="handoffError" role="alert" class="operator-access__error">{{ handoffError }}</p>

        <p v-if="handoffLoading">{{ t('common.loading') }}</p>
        <p v-else-if="pendingHandoffs.length === 0" data-testid="handoff-empty-state">
          {{ t('operator_access.handoff.empty') }}
        </p>
        <table v-else class="operator-access__table">
          <thead>
            <tr>
              <th>{{ t('operator_access.handoff.station') }}</th>
              <th>{{ t('operator_access.handoff.requesting_device') }}</th>
              <th>{{ t('operator_access.handoff.expires_at') }}</th>
              <th>{{ t('operator_access.handoff.token_id') }}</th>
              <th>{{ t('operator_access.tokens.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="pendingHandoff in pendingHandoffs"
              :key="pendingHandoff.id"
              :data-testid="`pending-handoff-row-${pendingHandoff.id}`"
            >
              <td>
                <strong>{{ pendingHandoff.station }}</strong>
                <div>{{ pendingHandoff.status }}</div>
              </td>
              <td>{{ pendingHandoff.requestingDeviceId }}</td>
              <td>{{ pendingHandoff.expiresAt }}</td>
              <td>{{ pendingHandoff.tokenId }}</td>
              <td class="operator-access__actions operator-access__actions--stacked">
                <button
                  :data-testid="`admin-reveal-pin-${pendingHandoff.id}`"
                  type="button"
                  @click="revealAdminPin(pendingHandoff.id)"
                >
                  {{ t('operator_access.handoff.admin_reveal_pin') }}
                </button>
                <span v-if="pendingHandoff.pin" :data-testid="`handoff-pin-${pendingHandoff.id}`">
                  {{ t('operator_access.handoff.revealed_pin', { pin: pendingHandoff.pin }) }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </section>
</template>

<style scoped>
.operator-access {
  display: grid;
  gap: var(--rd-space-4);
}

.operator-access__header h2,
.operator-access__panel h3 {
  margin: 0 0 var(--rd-space-2);
}

.operator-access__description,
.operator-access__eyebrow,
.operator-access__fallback {
  margin: 0;
  color: var(--rd-text-muted);
}

.operator-access__eyebrow {
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-size: 0.75rem;
}

.operator-access__panel,
.operator-access__authorization,
.operator-access__message,
.operator-access__error {
  border: 1px solid var(--rd-border);
  border-radius: 0.75rem;
  padding: var(--rd-space-4);
  background: var(--rd-surface);
}

.operator-access__authorization,
.operator-access__error {
  color: var(--rd-danger);
}

.operator-access__message {
  color: var(--rd-success);
}

.operator-access__form,
.operator-access__handoff-form {
  display: grid;
  gap: var(--rd-space-3);
}

.operator-access__form label,
.operator-access__handoff-form label {
  display: grid;
  gap: var(--rd-space-1);
}

.operator-access__table {
  width: 100%;
  border-collapse: collapse;
  margin-top: var(--rd-space-3);
}

.operator-access__table th,
.operator-access__table td {
  border-top: 1px solid var(--rd-border);
  padding: var(--rd-space-3);
  text-align: left;
  vertical-align: top;
}

.operator-access__actions {
  display: flex;
  gap: var(--rd-space-2);
  flex-wrap: wrap;
}

.operator-access__actions--stacked {
  flex-direction: column;
  align-items: flex-start;
}
</style>
