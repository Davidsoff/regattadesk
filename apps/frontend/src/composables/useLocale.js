import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { setLocale } from '../i18n';

const SUPPORTED_LOCALES = ['nl', 'en'];

/**
 * Composable for locale management and switching
 */
export function useLocale() {
  const { locale, t } = useI18n();

  const currentLocale = computed(() => locale.value);

  const switchLocale = (newLocale) => {
    setLocale(newLocale);
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
