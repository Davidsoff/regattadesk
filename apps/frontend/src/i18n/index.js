import { createI18n } from 'vue-i18n';
import en from './locales/en.json';
import nl from './locales/nl.json';

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

  if (stored && ['nl', 'en'].includes(stored)) {
    return stored;
  }

  try {
    const browserLang = navigator.language.split('-')[0];
    return ['nl', 'en'].includes(browserLang) ? browserLang : 'nl';
  } catch {
    return 'nl';
  }
}

const i18n = createI18n({
  legacy: false, // Use Composition API mode
  locale: getDefaultLocale(),
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
  if (!['nl', 'en'].includes(locale)) {
    console.warn(`Unsupported locale: ${locale}. Using 'nl' instead.`);
    locale = 'nl';
  }
  
  i18n.global.locale.value = locale;
  try {
    localStorage.setItem('regattadesk-locale', locale);
  } catch {
    // Storage may be unavailable in some environments.
  }

  if (typeof document !== 'undefined') {
    document.documentElement.setAttribute('lang', locale);
  }
}
