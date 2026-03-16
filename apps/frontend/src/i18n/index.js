import { createI18n } from 'vue-i18n';
import en from './locales/en.json';
import nl from './locales/nl.json';
import { normalizeLocale } from '../utils/locale.js';

/**
 * Get the user's preferred locale from localStorage or browser settings.
 * Defaults to 'nl' (Dutch) as per RegattaDesk requirements.
 */
function getDefaultLocale() {
  let stored = null;
  try {
    stored = localStorage.getItem('regattadesk-locale');
  } catch {
    stored = null;
  }

  const storedLocale = normalizeLocale(stored);
  if (storedLocale) {
    return storedLocale;
  }

  try {
    return normalizeLocale(navigator.language) || 'nl';
  } catch {
    return 'nl';
  }
}

// Locale detection runs at module scope so the i18n instance is ready
// immediately on import.  The DOM write (document.lang) is deferred to
// initI18n() which must be called once at app bootstrap in main.js.
const detectedLocale = getDefaultLocale();

const i18n = createI18n({
  legacy: false, // Use Composition API mode
  locale: detectedLocale,
  fallbackLocale: 'en',
  messages: {
    en,
    nl
  }
});

export default i18n;

/**
 * Apply the current i18n locale to the document <html lang> attribute.
 * Must be called once at app bootstrap (main.js) before mounting the Vue app.
 * Kept separate from module-scope code so import-time DOM mutations are
 * avoided in test and SSR environments.
 */
export function initI18n() {
  if (typeof document !== 'undefined') {
    document.documentElement.setAttribute('lang', i18n.global.locale.value);
  }
}

/**
 * Update the locale and persist it to localStorage
 */
export function setLocale(locale) {
  let normalizedLocale = normalizeLocale(locale);
  if (!normalizedLocale) {
    console.warn(`Unsupported locale: ${locale}. Using 'nl' instead.`);
    normalizedLocale = 'nl';
  }
  
  i18n.global.locale.value = normalizedLocale;
  try {
    localStorage.setItem('regattadesk-locale', normalizedLocale);
  } catch (storageError) {
    // localStorage may be unavailable (e.g. private browsing, storage quota exceeded).
    // Locale preference is not persisted but the app continues to function correctly.
    console.debug('Could not persist locale to localStorage:', storageError)
  }

  if (typeof document !== 'undefined') {
    document.documentElement.setAttribute('lang', normalizedLocale);
  }
}
