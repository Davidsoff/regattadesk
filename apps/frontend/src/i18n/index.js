import { createI18n } from 'vue-i18n';
import en from './locales/en.json';
import nl from './locales/nl.json';

function normalizeLocale(value) {
  if (typeof value !== 'string') {
    return null;
  }

  const normalizedValue = value.trim();
  if (normalizedValue.length === 0) {
    return null;
  }

  const baseLanguage = normalizedValue.toLowerCase().split(/[-_]/)[0];
  if (baseLanguage === 'nl') {
    return 'nl';
  }
  if (baseLanguage === 'en') {
    return 'en';
  }
  return null;
}

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
  } catch {
    // Storage may be unavailable in some environments.
  }

  if (typeof document !== 'undefined') {
    document.documentElement.setAttribute('lang', normalizedLocale);
  }
}
