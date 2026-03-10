import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'

export const SELECTED_CAPTURE_SESSIONS_STORAGE_KEY = 'rd_operator_selected_capture_sessions'

export function installStorage() {
  const values = new Map()
  const storage = {
    getItem(key) {
      return values.has(key) ? values.get(key) : null
    },
    setItem(key, value) {
      values.set(key, String(value))
    },
    removeItem(key) {
      values.delete(key)
    },
    clear() {
      values.clear()
    }
  }

  if (globalThis.window) {
    Object.defineProperty(globalThis.window, 'localStorage', {
      value: storage,
      configurable: true
    })
  }

  Object.defineProperty(globalThis, 'localStorage', {
    value: storage,
    configurable: true
  })

  return storage
}

function mergeDeep(base, overrides) {
  if (!overrides || typeof overrides !== 'object' || Array.isArray(overrides)) {
    return base
  }

  const result = { ...base }
  for (const [key, value] of Object.entries(overrides)) {
    const currentValue = result[key]
    if (
      value &&
      typeof value === 'object' &&
      !Array.isArray(value) &&
      currentValue &&
      typeof currentValue === 'object' &&
      !Array.isArray(currentValue)
    ) {
      result[key] = mergeDeep(currentValue, value)
      continue
    }

    result[key] = value
  }

  return result
}

const defaultMessages = {
  en: {
    common: {
      operator: 'Operator'
    },
    navigation: {
      line_scan: 'Line Scan'
    },
    operator: {
      regattas: {
        title: 'Regattas',
        description: 'Select a regatta to work with',
        token_status: 'Active token {token} at station {station}.',
        access_hint: 'Open the assigned link.'
      },
      regatta: {
        title: 'Regatta',
        id: 'Regatta ID',
        token_status: 'Operator token: {token}',
        no_token: 'Unavailable',
        station_context: 'Station: {station}',
        create_session: 'Create Capture Session',
        loading_sessions: 'Loading capture sessions...',
        create_failed: 'Failed to create capture session.',
        close_failed: 'Failed to close capture session.',
        errors: {
          load_sessions_failed: 'Failed to load capture sessions.',
          create_failed: 'Failed to create capture session.',
          close_failed: 'Failed to close capture session.'
        },
        missing_block_scope: 'Operator token must include a block scope before starting a capture session.',
        open_session: 'Open Session',
        close_session: 'Close Session',
        session_summary: '{station} · {session_type} · {state}',
        sync_summary: 'Pending {pending_operations}, failed {failed_operations}',
        sync_synced: 'Sync status: synced',
        sync_pending: 'Sync status: pending ({reason})',
        sync_pending_default: 'awaiting upload',
        sync_attention: 'Sync status: attention required'
      },
      line_scan: {
        title: 'Line Scan',
        description: 'Capture line scan images and mark finish times'
      },
      capture: {
        title: 'Line Scan Capture',
        create_marker: 'Create Marker',
        no_markers: 'No markers yet'
      }
    }
  }
}

export function createOperatorI18n(overrides = {}) {
  return createI18n({
    legacy: false,
    locale: 'en',
    messages: mergeDeep(defaultMessages, overrides)
  })
}

export function createOperatorRegattaRouter(component) {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/operator/regattas/:regattaId',
        name: 'operator-regatta-home',
        component
      },
      {
        path: '/operator/regattas/:regattaId/sessions',
        name: 'operator-regatta-sessions',
        component: { template: '<div>Sessions</div>' }
      },
      {
        path: '/operator/regattas/:regattaId/sessions/:captureSessionId/line-scan',
        name: 'operator-session-line-scan',
        component: { template: '<div>Workspace</div>' }
      }
    ]
  })
}

export async function mountOperatorRegattaHome(component, options = {}) {
  const { path = '/operator/regattas/regatta-138', messages } = options
  const router = createOperatorRegattaRouter(component)
  await router.push(path)
  await router.isReady()

  const wrapper = mount(component, {
    global: {
      plugins: [router, createOperatorI18n(messages)]
    }
  })

  await flushPromises()

  return { router, wrapper }
}
