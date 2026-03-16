<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { createApiClient, createOperatorApi } from '../api'
import { resolveOperatorStation, resolveOperatorToken } from '../operatorContext'
import { normalizeCaptureSession, summarizeCaptureSessionSyncState } from '../operatorCaptureSessions'
import {
  buildOperatorSessionWorkspacePath,
  buildOperatorSessionsPath,
  getSelectedCaptureSessionId
} from '../operatorSessionSelection'

const { t } = useI18n()
const route = useRoute()

const regattaId = computed(() => (typeof route.params.regattaId === 'string' ? route.params.regattaId : ''))
const captureSessionId = computed(() =>
  typeof route.params.captureSessionId === 'string' ? route.params.captureSessionId : ''
)
const operatorToken = computed(() => resolveOperatorToken())
const operatorStation = computed(() => resolveOperatorStation())
const selectedSessionSummary = ref(null)
const operatorApi = createOperatorApi(createApiClient(), {
  getOperatorToken: () => operatorToken.value
})

const sessionsPath = computed(() => (regattaId.value ? buildOperatorSessionsPath(regattaId.value) : null))
const legacyLineScanPath = computed(() =>
  regattaId.value ? `/operator/regattas/${regattaId.value}/line-scan` : null
)
const activeCaptureSessionId = computed(() =>
  captureSessionId.value || (regattaId.value ? getSelectedCaptureSessionId(regattaId.value) : null)
)
const lineScanPath = computed(() => {
  if (!regattaId.value) {
    return null
  }

  return activeCaptureSessionId.value
    ? buildOperatorSessionWorkspacePath(regattaId.value, activeCaptureSessionId.value)
    : legacyLineScanPath.value
})

const breadcrumbLabels = {
  'operator-regattas': () => t('navigation.regattas'),
  'operator-regatta-home': () => t('operator.regatta.title'),
  'operator-regatta-sessions': () => t('navigation.sessions'),
  'operator-session-line-scan': () => t('navigation.line_scan')
}

const breadcrumbs = computed(() => {
  const steps = Array.isArray(route.meta?.breadcrumb) ? route.meta.breadcrumb : []

  return steps.map((step, index) => {
    let to = null
    if (step === 'operator-regattas') {
      to = '/operator/regattas'
    } else if (step === 'operator-regatta-home' && regattaId.value) {
      to = `/operator/regattas/${regattaId.value}`
    } else if (step === 'operator-regatta-sessions' && regattaId.value) {
      to = buildOperatorSessionsPath(regattaId.value)
    } else if (step === 'operator-session-line-scan' && regattaId.value && activeCaptureSessionId.value) {
      to = buildOperatorSessionWorkspacePath(regattaId.value, activeCaptureSessionId.value)
    }

    return {
      key: `${step}-${index}`,
      label: breadcrumbLabels[step]?.() ?? step,
      to,
      current: index === steps.length - 1
    }
  })
})

const syncSummaryText = computed(() => {
  if (!selectedSessionSummary.value) {
    return null
  }

  return summarizeCaptureSessionSyncState(selectedSessionSummary.value, t)
})

watch(
  () => [regattaId.value, activeCaptureSessionId.value, operatorToken.value],
  async ([nextRegattaId, nextCaptureSessionId, nextOperatorToken]) => {
    if (!nextRegattaId || !nextCaptureSessionId || !nextOperatorToken) {
      selectedSessionSummary.value = null
      return
    }

    try {
      selectedSessionSummary.value = normalizeCaptureSession(
        await operatorApi.getCaptureSession(nextRegattaId, nextCaptureSessionId)
      )
    } catch (err) {
      console.error('Session load error:', err)
      selectedSessionSummary.value = null
    }
  },
  { immediate: true }
)
</script>

<template>
  <div class="operator-layout" data-contrast="high">
    <a href="#main-content" class="skip-link">{{ t('common.skip_to_content') }}</a>

    <header class="operator-layout__header">
      <div class="operator-layout__brand">
        <h1>RegattaDesk</h1>
        <span class="operator-layout__surface-label">{{ t('common.operator') }}</span>
      </div>

      <nav class="operator-layout__nav" aria-label="Operator navigation">
        <router-link
          to="/operator/regattas"
          class="operator-layout__nav-item"
          :aria-current="route.name === 'operator-regattas' ? 'page' : undefined"
        >
          {{ t('navigation.regattas') }}
        </router-link>
        <router-link
          v-if="regattaId"
          :to="sessionsPath"
          class="operator-layout__nav-item"
          :aria-current="route.name === 'operator-regatta-sessions' ? 'page' : undefined"
        >
          {{ t('navigation.sessions') }}
        </router-link>
        <router-link
          v-if="regattaId && lineScanPath"
          :to="lineScanPath"
          class="operator-layout__nav-item"
          :aria-current="
            route.name === 'operator-session-line-scan' || route.name === 'operator-line-scan-legacy'
              ? 'page'
              : undefined
          "
        >
          {{ t('navigation.line_scan') }}
        </router-link>
      </nav>
    </header>

    <div v-if="regattaId" class="operator-layout__status" data-testid="operator-shell-status">
      <span data-testid="operator-shell-station">
        {{ t('operator.regatta.station_context', { station: operatorStation }) }}
      </span>
      <span v-if="activeCaptureSessionId" data-testid="operator-shell-session">
        {{ t('operator.regatta.session_label', { id: activeCaptureSessionId }) }}
      </span>
      <span v-if="syncSummaryText" data-testid="operator-shell-sync-summary">
        {{ syncSummaryText }}
      </span>
    </div>

    <nav
      v-if="breadcrumbs.length > 0"
      class="operator-layout__breadcrumbs"
      aria-label="Breadcrumb"
      data-testid="operator-breadcrumbs"
    >
      <ol class="operator-layout__breadcrumbs-list">
        <li
          v-for="breadcrumb in breadcrumbs"
          :key="breadcrumb.key"
          class="operator-layout__breadcrumbs-item"
        >
          <router-link
            v-if="breadcrumb.to && !breadcrumb.current"
            :to="breadcrumb.to"
            class="operator-layout__breadcrumbs-link"
          >
            {{ breadcrumb.label }}
          </router-link>
          <span v-else aria-current="page">{{ breadcrumb.label }}</span>
        </li>
      </ol>
    </nav>

    <main id="main-content" class="operator-layout__main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.operator-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--rd-bg);
}

.skip-link {
  position: absolute;
  top: -40px;
  left: 0;
  background: var(--rd-accent);
  color: white;
  padding: var(--rd-space-3);
  text-decoration: none;
  z-index: 100;
  min-height: var(--rd-hit-operator);
  display: flex;
  align-items: center;
}

.skip-link:focus {
  top: 0;
}

.operator-layout__header {
  background: var(--rd-surface);
  border-bottom: 2px solid var(--rd-border);
  padding: var(--rd-space-4);
  min-height: var(--rd-hit-operator);
  display: flex;
  align-items: center;
  gap: var(--rd-space-6);
}

.operator-layout__brand {
  display: flex;
  align-items: center;
  gap: var(--rd-space-3);
}

.operator-layout__brand h1 {
  font-size: 1.5rem;
  font-weight: 700;
  margin: 0;
}

.operator-layout__surface-label {
  font-size: 1rem;
  color: var(--rd-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
}

.operator-layout__nav {
  display: flex;
  gap: var(--rd-space-4);
}

.operator-layout__nav-item {
  padding: var(--rd-space-2) var(--rd-space-3);
  text-decoration: none;
  color: var(--rd-text);
  border-radius: 4px;
  min-height: var(--rd-hit-operator);
  display: flex;
  align-items: center;
  font-size: 1rem;
  font-weight: 500;
}

.operator-layout__nav-item:hover {
  background: var(--rd-surface-2);
}

.operator-layout__nav-item.router-link-exact-active {
  background: var(--rd-accent);
  color: white;
}

.operator-layout__status {
  display: flex;
  flex-wrap: wrap;
  gap: var(--rd-space-3);
  padding: 0 var(--rd-space-4) var(--rd-space-2);
  color: var(--rd-text-muted);
  font-weight: 600;
}

.operator-layout__breadcrumbs {
  padding: 0 var(--rd-space-4);
}

.operator-layout__breadcrumbs-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--rd-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.operator-layout__breadcrumbs-item {
  display: inline-flex;
  align-items: center;
  gap: var(--rd-space-2);
}

.operator-layout__breadcrumbs-item:not(:last-child)::after {
  content: '/';
  color: var(--rd-text-muted);
}

.operator-layout__breadcrumbs-link {
  color: var(--rd-accent);
  text-decoration: none;
}

.operator-layout__main {
  flex: 1;
  padding: var(--rd-space-4);
}
</style>
