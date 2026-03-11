/**
 * Tests for useLocale composable and locale persistence.
 *
 * Tests cover:
 * - currentLocale reflects i18n instance locale
 * - switchLocale changes the locale and persists to localStorage
 * - Fallback to 'nl' for unknown locales
 * - getLocaleName returns display names
 * - supportedLocales contains expected values
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

// ─── localStorage mock ────────────────────────────────────────────────────────

function makeLocalStorageMock() {
  const store = new Map()
  return {
    getItem: (key) => store.has(key) ? store.get(key) : null,
    setItem: (key, value) => store.set(key, String(value)),
    removeItem: (key) => store.delete(key),
    clear: () => store.clear(),
    _store: store
  }
}

// ─── Test wrapper ─────────────────────────────────────────────────────────────

/**
 * Mount a minimal component that uses useLocale() inside a vue-i18n context.
 */
async function mountWithLocale(i18nInstance, setup) {
  const TestComponent = defineComponent({
    setup,
    template: '<div />'
  })
  const wrapper = mount(TestComponent, {
    global: { plugins: [i18nInstance] }
  })
  return wrapper
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('useLocale', () => {
  let storageMock
  let i18n

  beforeEach(async () => {
    storageMock = makeLocalStorageMock()
    vi.stubGlobal('localStorage', storageMock)

    i18n = createI18n({
      legacy: false,
      locale: 'nl',
      messages: {
        nl: { hello: 'Hallo' },
        en: { hello: 'Hello' }
      }
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('currentLocale reflects the active i18n locale', async () => {
    const { useLocale } = await import('../composables/useLocale.js')
    let captured

    await mountWithLocale(i18n, () => {
      captured = useLocale()
    })

    expect(captured.currentLocale.value).toBe('nl')
  })

  it('supportedLocales contains nl and en', async () => {
    const { useLocale } = await import('../composables/useLocale.js')
    let captured

    await mountWithLocale(i18n, () => {
      captured = useLocale()
    })

    expect(captured.supportedLocales).toContain('nl')
    expect(captured.supportedLocales).toContain('en')
  })

  it('getLocaleName returns display name for nl', async () => {
    const { useLocale } = await import('../composables/useLocale.js')
    let captured

    await mountWithLocale(i18n, () => {
      captured = useLocale()
    })

    expect(captured.getLocaleName('nl')).toBe('Nederlands')
  })

  it('getLocaleName returns display name for en', async () => {
    const { useLocale } = await import('../composables/useLocale.js')
    let captured

    await mountWithLocale(i18n, () => {
      captured = useLocale()
    })

    expect(captured.getLocaleName('en')).toBe('English')
  })

  it('getLocaleName returns the code for unknown locales', async () => {
    const { useLocale } = await import('../composables/useLocale.js')
    let captured

    await mountWithLocale(i18n, () => {
      captured = useLocale()
    })

    expect(captured.getLocaleName('fr')).toBe('fr')
  })
})

// ─── setLocale / persistence tests ────────────────────────────────────────────

describe('setLocale', () => {
  let storageMock

  beforeEach(() => {
    storageMock = makeLocalStorageMock()
    vi.stubGlobal('localStorage', storageMock)
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('persists locale to localStorage', async () => {
    // Import setLocale directly — it uses the shared i18n singleton
    const { setLocale } = await import('../i18n/index.js')
    setLocale('en')
    expect(storageMock.getItem('regattadesk-locale')).toBe('en')
  })

  it('persists nl locale to localStorage', async () => {
    const { setLocale } = await import('../i18n/index.js')
    setLocale('nl')
    expect(storageMock.getItem('regattadesk-locale')).toBe('nl')
  })

  it('falls back to nl for unsupported locale', async () => {
    const { setLocale } = await import('../i18n/index.js')
    setLocale('fr') // Unsupported — should fall back to nl
    expect(storageMock.getItem('regattadesk-locale')).toBe('nl')
  })
})

// ─── normalizeLocale tests ─────────────────────────────────────────────────────

describe('normalizeLocale (utils/locale.js)', () => {
  it('returns nl for nl input', async () => {
    const { normalizeLocale } = await import('../utils/locale.js')
    expect(normalizeLocale('nl')).toBe('nl')
  })

  it('returns en for en input', async () => {
    const { normalizeLocale } = await import('../utils/locale.js')
    expect(normalizeLocale('en')).toBe('en')
  })

  it('returns nl for nl-NL locale tag', async () => {
    const { normalizeLocale } = await import('../utils/locale.js')
    expect(normalizeLocale('nl-NL')).toBe('nl')
  })

  it('returns en for en-US locale tag', async () => {
    const { normalizeLocale } = await import('../utils/locale.js')
    expect(normalizeLocale('en-US')).toBe('en')
  })

  it('returns null for unsupported locale', async () => {
    const { normalizeLocale } = await import('../utils/locale.js')
    expect(normalizeLocale('fr')).toBeNull()
  })

  it('returns null for null input', async () => {
    const { normalizeLocale } = await import('../utils/locale.js')
    expect(normalizeLocale(null)).toBeNull()
  })

  it('returns null for empty string', async () => {
    const { normalizeLocale } = await import('../utils/locale.js')
    expect(normalizeLocale('')).toBeNull()
  })
})
