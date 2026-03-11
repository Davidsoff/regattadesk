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

const defaultLocale = getDefaultLocale();
if (typeof document !== 'undefined') {
  document.documentElement.setAttribute('lang', defaultLocale);
}

const i18n = createI18n({
  legacy: false, // Use Composition API mode
  locale: defaultLocale,
  fallbackLocale: 'en',
  messages: {
    en,
    nl
  }
});

export default i18n;

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
