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

describe('i18n initialization', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    document.documentElement.setAttribute('lang', 'en');
  });

  it('uses stored locale when available and valid', async () => {
    vi.stubGlobal('localStorage', {
      getItem: () => 'nl',
      setItem: vi.fn()
    });
    vi.stubGlobal('navigator', { language: 'en-US' });

    const { default: i18n } = await importFreshI18n();
    expect(i18n.global.locale.value).toBe('nl');
    expect(document.documentElement.getAttribute('lang')).toBe('nl');
  });

  it('falls back to navigator locale when localStorage access fails', async () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('storage unavailable');
      },
      setItem: vi.fn()
    });
    vi.stubGlobal('navigator', { language: 'en-US' });

    const { default: i18n } = await importFreshI18n();
    expect(i18n.global.locale.value).toBe('en');
    expect(document.documentElement.getAttribute('lang')).toBe('en');
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
    expect(i18n.global.locale.value).toBe('nl');
    expect(document.documentElement.getAttribute('lang')).toBe('nl');
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

    expect(i18n.global.locale.value).toBe('nl');
    expect(storage.setItem).toHaveBeenCalledWith('regattadesk-locale', 'nl');
    expect(document.documentElement.getAttribute('lang')).toBe('nl');
  });
});
