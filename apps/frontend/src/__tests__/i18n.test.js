import { beforeEach, describe, expect, it, vi } from 'vitest';

const EN_MESSAGES = {
  common: { save: 'Save' },
  print: { generated: 'Generated', draw_version: 'Draw Version', results_version: 'Results Version', page: 'Page', of: 'of' }
};

const NL_MESSAGES = {
  common: { save: 'Opslaan' },
  print: { generated: 'Gegenereerd', draw_version: 'Lotingversie', results_version: 'Resultatenversie', page: 'Pagina', of: 'van' }
};

async function importFreshI18n() {
  vi.resetModules();
  return import('../i18n/index.js');
}

function stubStorageAndNavigator(storedLocale, navigatorLanguage = 'en-US') {
  vi.stubGlobal('localStorage', {
    getItem: () => storedLocale,
    setItem: vi.fn()
  });
  vi.stubGlobal('navigator', { language: navigatorLanguage });
}

function expectLocaleApplied(i18n, expectedLocale) {
  expect(i18n.global.locale.value).toBe(expectedLocale);
  expect(document.documentElement.getAttribute('lang')).toBe(expectedLocale);
}

describe('i18n initialization', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    document.documentElement.setAttribute('lang', 'en');
  });

  it('uses stored locale when available and valid', async () => {
    stubStorageAndNavigator('nl');

    const { default: i18n } = await importFreshI18n();
    expectLocaleApplied(i18n, 'nl');
  });

  it.each([
    ['en-US', 'nl-NL', 'en'],
    [null, 'EN_us', 'en']
  ])(
    'normalizes locale tags (stored=%s, navigator=%s)',
    async (storedLocale, navigatorLanguage, expectedLocale) => {
      stubStorageAndNavigator(storedLocale, navigatorLanguage);
      const { default: i18n } = await importFreshI18n();
      expectLocaleApplied(i18n, expectedLocale);
    }
  );

  it('falls back to navigator locale when localStorage access fails', async () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('storage unavailable');
      },
      setItem: vi.fn()
    });
    vi.stubGlobal('navigator', { language: 'en-US' });
    const { default: i18n } = await importFreshI18n();
    expectLocaleApplied(i18n, 'en');
  });

  it('falls back to nl when both storage and navigator are unusable', async () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('storage unavailable');
      },
      setItem: vi.fn()
    });
    vi.stubGlobal('navigator', {
      get language() {
        throw new Error('navigator unavailable');
      }
    });

    const { default: i18n } = await importFreshI18n();
    expectLocaleApplied(i18n, 'nl');
  });
});

describe('setLocale', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    vi.stubGlobal('localStorage', {
      getItem: () => 'en',
      setItem: vi.fn()
    });
    vi.stubGlobal('navigator', { language: 'en-US' });
    document.documentElement.setAttribute('lang', 'en');
  });

  it('updates locale, storage, and html lang', async () => {
    const storage = globalThis.localStorage;
    const { default: i18n, setLocale } = await importFreshI18n();

    i18n.global.setLocaleMessage('en', EN_MESSAGES);
    i18n.global.setLocaleMessage('nl', NL_MESSAGES);
    setLocale('nl');

    expectLocaleApplied(i18n, 'nl');
    expect(storage.setItem).toHaveBeenCalledWith('regattadesk-locale', 'nl');
  });

  it('falls back to nl for unsupported locale values', async () => {
    const storage = globalThis.localStorage;
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const { default: i18n, setLocale } = await importFreshI18n();

    setLocale('fr');

    expectLocaleApplied(i18n, 'nl');
    expect(storage.setItem).toHaveBeenCalledWith('regattadesk-locale', 'nl');
    expect(warnSpy).toHaveBeenCalled();
    warnSpy.mockRestore();
  });

  it.each([
    ['nl-BE', 'nl'],
    ['en-US', 'en'],
    ['  NL_be  ', 'nl']
  ])('normalizes locale %s to %s before persisting', async (inputLocale, expectedLocale) => {
    const storage = globalThis.localStorage;
    const { default: i18n, setLocale } = await importFreshI18n();

    setLocale(inputLocale);
    expectLocaleApplied(i18n, expectedLocale);
    expect(storage.setItem).toHaveBeenLastCalledWith('regattadesk-locale', expectedLocale);
  });

  it('still updates runtime locale when storage write fails', async () => {
    vi.stubGlobal('localStorage', {
      getItem: () => 'en',
      setItem: () => {
        throw new Error('storage write failure');
      }
    });

    const { default: i18n, setLocale } = await importFreshI18n();
    setLocale('nl');

    expectLocaleApplied(i18n, 'nl');
  });
});
