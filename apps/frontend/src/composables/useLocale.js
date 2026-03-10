import { computed } from 'vue';
import { useI18n } from 'vue-i18n';

const SUPPORTED_LOCALES = ['nl', 'en'];

function normalizeLocale(value) {
  if (typeof value !== 'string') {
    return 'nl';
  }

  const baseLanguage = value.trim().toLowerCase().split(/[-_]/)[0];
  return baseLanguage === 'en' ? 'en' : 'nl';
}

/**
 * Composable for locale management and switching
 */
export function useLocale() {
  const { locale, t } = useI18n();

  const currentLocale = computed(() => locale.value);

  const switchLocale = (newLocale) => {
    const normalizedLocale = normalizeLocale(newLocale);
    locale.value = normalizedLocale;

    try {
      localStorage.setItem('regattadesk-locale', normalizedLocale);
    } catch {
      // Storage may be unavailable in some environments.
    }

    if (typeof document !== 'undefined') {
      document.documentElement.setAttribute('lang', normalizedLocale);
    }
  };

  const getLocaleName = (localeCode) => {
    const names = {
      nl: 'Nederlands',
      en: 'English'
    };
    return names[localeCode] || localeCode;
  };

  return {
    currentLocale,
    supportedLocales: SUPPORTED_LOCALES,
    switchLocale,
    getLocaleName,
    t
  };
}
