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
  return mount(TestComponent, {
    global: { plugins: [i18nInstance] }
  })
}

async function captureUseLocale(i18nInstance) {
  const { useLocale } = await import('../composables/useLocale.js')
  let captured

  await mountWithLocale(i18nInstance, () => {
    captured = useLocale()
  })

  return captured
}

async function setAndReadPersistedLocale(locale) {
  const { setLocale } = await import('../i18n/index.js')
  setLocale(locale)
  return globalThis.localStorage.getItem('regattadesk-locale')
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
    const captured = await captureUseLocale(i18n)
    expect(captured.currentLocale.value).toBe('nl')
  })

  it('supportedLocales contains nl and en', async () => {
    const captured = await captureUseLocale(i18n)
    expect(captured.supportedLocales).toContain('nl')
    expect(captured.supportedLocales).toContain('en')
  })

  it.each([
    ['nl', 'Nederlands'],
    ['en', 'English'],
    ['fr', 'fr'],
  ])('getLocaleName returns %s for %s', async (locale, expectedName) => {
    const captured = await captureUseLocale(i18n)
    expect(captured.getLocaleName(locale)).toBe(expectedName)
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

  it.each([
    ['en', 'en'],
    ['nl', 'nl'],
    ['fr', 'nl'],
  ])('persists %s as %s', async (locale, expectedPersistedLocale) => {
    expect(await setAndReadPersistedLocale(locale)).toBe(expectedPersistedLocale)
  })
})

// ─── normalizeLocale tests ─────────────────────────────────────────────────────

describe('normalizeLocale (utils/locale.js)', () => {
  it.each([
    ['nl', 'nl'],
    ['en', 'en'],
    ['nl-NL', 'nl'],
    ['en-US', 'en'],
    ['fr', null],
    [null, null],
    ['', null],
  ])('normalizes %s to %s', async (input, expected) => {
    const { normalizeLocale } = await import('../utils/locale.js')
    expect(normalizeLocale(input)).toBe(expected)
  })
})
