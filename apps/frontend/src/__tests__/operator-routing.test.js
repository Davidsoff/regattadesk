import { beforeEach, describe, expect, it, vi } from 'vitest'

const SELECTED_CAPTURE_SESSIONS_STORAGE_KEY = 'rd_operator_selected_capture_sessions'

function installStorage() {
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

async function loadRouter() {
  vi.resetModules()
  const module = await import('../router/index.js')
  return module.default
}

describe('Operator routing for issue #138', () => {
  beforeEach(() => {
    const storage = installStorage()
    storage.clear()
    globalThis.window?.history?.replaceState({}, '', '/')
    globalThis.__REGATTADESK_AUTH__ = {
      operatorToken: 'token-138'
    }
  })

  it('registers operator regatta home, sessions, and canonical session workspace routes with breadcrumb metadata', async () => {
    const router = await loadRouter()
    const routes = router.getRoutes()

    const regattaHome = routes.find((route) => route.name === 'operator-regatta-home')
    const captureSessions = routes.find((route) => route.name === 'operator-regatta-sessions')
    const lineScanWorkspace = routes.find((route) => route.name === 'operator-session-line-scan')

    expect(regattaHome?.path).toBe('/operator/regattas/:regattaId')
    expect(regattaHome?.meta?.breadcrumb).toEqual(['operator-regattas', 'operator-regatta-home'])

    expect(captureSessions?.path).toBe('/operator/regattas/:regattaId/sessions')
    expect(captureSessions?.meta?.breadcrumb).toEqual([
      'operator-regattas',
      'operator-regatta-home',
      'operator-regatta-sessions'
    ])

    expect(lineScanWorkspace?.path).toBe('/operator/regattas/:regattaId/sessions/:captureSessionId/line-scan')
    expect(lineScanWorkspace?.meta?.breadcrumb).toEqual([
      'operator-regattas',
      'operator-regatta-home',
      'operator-regatta-sessions',
      'operator-session-line-scan'
    ])
  })

  it('redirects the ambiguous line-scan entry route to the selected capture session workspace for that regatta', async () => {
    globalThis.localStorage.setItem(
      SELECTED_CAPTURE_SESSIONS_STORAGE_KEY,
      JSON.stringify({
        'regatta-138': 'session-138-a'
      })
    )

    const router = await loadRouter()
    await router.push('/operator/regattas/regatta-138/line-scan')
    await router.isReady()

    expect(router.currentRoute.value.fullPath).toBe(
      '/operator/regattas/regatta-138/sessions/session-138-a/line-scan'
    )
    expect(router.currentRoute.value.name).toBe('operator-session-line-scan')
  })

  it('redirects the ambiguous line-scan entry route to the sessions list when no selected capture session exists', async () => {
    const router = await loadRouter()
    await router.push('/operator/regattas/regatta-138/line-scan')
    await router.isReady()

    expect(router.currentRoute.value.fullPath).toBe('/operator/regattas/regatta-138/sessions')
    expect(router.currentRoute.value.name).toBe('operator-regatta-sessions')
  })
})
