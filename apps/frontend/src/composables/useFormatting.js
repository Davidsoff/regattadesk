import { computed } from 'vue';

/**
 * Composable for date/time formatting with locale and timezone support.
 * 
 * RegattaDesk formatting requirements:
 * - Technical formats: ISO 8601 YYYY-MM-DD
 * - Display formats: locale-dependent (nl: DD-MM-YYYY, en: YYYY-MM-DD)
 * - Time: 24-hour format (HH:mm for scheduled, M:SS.mmm for elapsed)
 * - Timezone: regatta-local
 */
export function useFormatting(locale = 'en') {
  // Accept locale as parameter or computed ref
  const currentLocale = computed(() => {
    if (typeof locale === 'object' && 'value' in locale) {
      return locale.value;
    }
    return locale;
  });

  /**
   * Format a date in ISO 8601 format (YYYY-MM-DD) for technical/API use
   */
  const formatDateISO = (date) => {
    if (!date) return '';
    const d = new Date(date);
    if (isNaN(d.getTime())) return '';
    
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  };

  /**
   * Format a date for display according to locale
   * nl: DD-MM-YYYY
   * en: YYYY-MM-DD
   */
  const formatDateDisplay = (date, regattaTimezone = null) => {
    if (!date) return '';
    const d = new Date(date);
    if (isNaN(d.getTime())) return '';

    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');

    // Locale-dependent format
    if (currentLocale.value === 'nl') {
      return `${day}-${month}-${year}`;
    }
    // Default to ISO format for 'en' and other locales
    return `${year}-${month}-${day}`;
  };

  /**
   * Format a time in 24-hour format (HH:mm) for scheduled times
   */
  const formatScheduledTime = (time, regattaTimezone = null) => {
    if (!time) return '';
    const d = new Date(time);
    if (isNaN(d.getTime())) return '';

    const hours = String(d.getHours()).padStart(2, '0');
    const minutes = String(d.getMinutes()).padStart(2, '0');
    return `${hours}:${minutes}`;
  };

  /**
   * Format elapsed time in M:SS.mmm or H:MM:SS.mmm format
   * @param {number} milliseconds - Elapsed time in milliseconds
   */
  const formatElapsedTime = (milliseconds) => {
    if (milliseconds == null || isNaN(milliseconds)) return '';
    
    const totalSeconds = Math.floor(milliseconds / 1000);
    const ms = milliseconds % 1000;
    const seconds = totalSeconds % 60;
    const minutes = Math.floor(totalSeconds / 60) % 60;
    const hours = Math.floor(totalSeconds / 3600);

    const msStr = String(ms).padStart(3, '0');
    const secStr = String(seconds).padStart(2, '0');

    if (hours > 0) {
      const minStr = String(minutes).padStart(2, '0');
      return `${hours}:${minStr}:${secStr}.${msStr}`;
    }
    
    return `${minutes}:${secStr}.${msStr}`;
  };

  /**
   * Format delta time (time behind leader) in +M:SS.mmm or +H:MM:SS.mmm format
   * @param {number} milliseconds - Delta time in milliseconds
   */
  const formatDeltaTime = (milliseconds) => {
    if (milliseconds == null || isNaN(milliseconds)) return '';
    
    // Leader shows +0:00.000
    if (milliseconds === 0) {
      return '+0:00.000';
    }
    
    const formatted = formatElapsedTime(milliseconds);
    return `+${formatted}`;
  };

  /**
   * Format a timestamp in ISO 8601 format with timezone for technical use
   * Example: 2026-02-06T14:30:00+01:00
   */
  const formatTimestampISO = (date, regattaTimezone = null) => {
    if (!date) return '';
    const d = new Date(date);
    if (isNaN(d.getTime())) return '';
    
    return d.toISOString();
  };

  /**
   * Format a timestamp for display with locale-specific formatting
   * Includes date and time in regatta timezone
   */
  const formatTimestampDisplay = (date, regattaTimezone = null) => {
    if (!date) return '';
    const d = new Date(date);
    if (isNaN(d.getTime())) return '';

    const datePart = formatDateDisplay(d, regattaTimezone);
    const timePart = formatScheduledTime(d, regattaTimezone);
    return `${datePart} ${timePart}`;
  };

  /**
   * Round time to millisecond precision using round-half-up rule
   * @param {number} milliseconds - Time in milliseconds
   * @param {number} precision - Number of decimal places (default 3 for milliseconds)
   */
  const roundTime = (milliseconds, precision = 3) => {
    if (milliseconds == null || isNaN(milliseconds)) return 0;
    
    const factor = Math.pow(10, precision);
    return Math.round(milliseconds * factor) / factor;
  };

  return {
    formatDateISO,
    formatDateDisplay,
    formatScheduledTime,
    formatElapsedTime,
    formatDeltaTime,
    formatTimestampISO,
    formatTimestampDisplay,
    roundTime
  };
}
