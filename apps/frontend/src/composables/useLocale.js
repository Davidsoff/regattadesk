import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { setLocale } from '../i18n';

/**
 * Composable for locale management and switching
 */
export function useLocale() {
  const { locale, t } = useI18n();

  const currentLocale = computed(() => locale.value);
  
  const supportedLocales = computed(() => ['nl', 'en']);

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
    supportedLocales,
    switchLocale,
    getLocaleName,
    t
  };
}
