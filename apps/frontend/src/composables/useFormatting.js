import { computed } from 'vue';
import { normalizeLocale } from '../utils/locale.js';

/**
 * Composable for date/time formatting with locale and timezone support.
 *
 * RegattaDesk formatting requirements:
 * - Technical formats: ISO 8601 YYYY-MM-DD
 * - Display formats: locale-dependent (nl: DD-MM-YYYY, en: YYYY-MM-DD)
 * - Time: 24-hour format (HH:mm for scheduled, M:SS.mmm for elapsed)
 * - Timezone: regatta-local
 */
/**
 * Internal guard: check if val is null, undefined, or empty string.
 * For dates: also validates by checking if it's a valid Date using Number.isNaN(d.getTime()).
 * @param {*} val
 * @param {function(*): string} fn
 * @param {string} fallback
 * @returns {string}
 */
function withValue(val, fn, fallback = '') {
  if (val == null || val === '') return fallback;
  const d = new Date(val);
  if (Number.isNaN(d.getTime())) return fallback;
  return fn(val);
}

/**
 * Internal guard: check if val is null, undefined, or NaN for numeric values.
 * @param {*} val
 * @param {function(*): string|number} fn
 * @param {string|number} fallback
 * @returns {string|number}
 */
function withNumericValue(val, fn, fallback = '') {
  if (val == null || Number.isNaN(val)) return fallback;
  return fn(val);
}

/**
 * Internal guard for numeric formatters that always return strings.
 * @param {*} val
 * @param {function(*): string} fn
 * @param {string} fallback
 * @returns {string}
 */
function withNumericStringValue(val, fn, fallback = '') {
  if (val == null || Number.isNaN(val)) return fallback;
  return fn(val);
}

/**
 * Internal guard for numeric formatters that always return numbers.
 * @param {*} val
 * @param {function(*): number} fn
 * @param {number} fallback
 * @returns {number}
 */
function withNumericNumberValue(val, fn, fallback = 0) {
  if (val == null || Number.isNaN(val)) return fallback;
  return fn(val);
}

export function useFormatting(locale = 'en') {
  const currentLocale = computed(() => {
    if (locale !== null && typeof locale === 'object') {
      const value = locale.value;
      if (typeof value === 'string' && value.length > 0) {
        return normalizeLocale(value) ?? 'en';
      }
      return 'en';
    }

    if (typeof locale === 'string' && locale.length > 0) {
      return normalizeLocale(locale) ?? 'en';
    }

    return 'en';
  });

  const getDateTimeParts = (date, regattaTimezone = null) => {
    const options = {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    };

    if (regattaTimezone) {
      options.timeZone = regattaTimezone;
    }

    try {
      const formatter = new Intl.DateTimeFormat('en-CA', options);
      const parts = formatter.formatToParts(date);
      const partMap = Object.fromEntries(parts.map((part) => [part.type, part.value]));

      return {
        year: Number(partMap.year),
        month: Number(partMap.month),
        day: Number(partMap.day),
        hour: Number(partMap.hour),
        minute: Number(partMap.minute),
        second: Number(partMap.second)
      };
    } catch {
      return {
        year: date.getFullYear(),
        month: date.getMonth() + 1,
        day: date.getDate(),
        hour: date.getHours(),
        minute: date.getMinutes(),
        second: date.getSeconds()
      };
    }
  };

  const getOffsetMinutes = (date, regattaTimezone = null) => {
    if (!regattaTimezone) {
      return -date.getTimezoneOffset();
    }

    const localParts = getDateTimeParts(date, regattaTimezone);
    const asUtc = Date.UTC(
      localParts.year,
      localParts.month - 1,
      localParts.day,
      localParts.hour,
      localParts.minute,
      localParts.second
    );

    return Math.round((asUtc - date.getTime()) / 60000);
  };

  const formatOffset = (offsetMinutes) => {
    const sign = offsetMinutes >= 0 ? '+' : '-';
    const absoluteMinutes = Math.abs(offsetMinutes);
    const hours = String(Math.floor(absoluteMinutes / 60)).padStart(2, '0');
    const minutes = String(absoluteMinutes % 60).padStart(2, '0');
    return `${sign}${hours}:${minutes}`;
  };

  /**
   * Format a date in ISO 8601 format (YYYY-MM-DD) for technical/API use
   */
  const formatDateISO = (date) => {
    return withValue(date, (val) => {
      if (typeof val === 'string') {
        const dateOnlyMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(val);
        if (dateOnlyMatch) {
          return dateOnlyMatch[0];
        }
      }

      const d = new Date(val);
      const year = d.getUTCFullYear();
      const month = String(d.getUTCMonth() + 1).padStart(2, '0');
      const day = String(d.getUTCDate()).padStart(2, '0');
      return `${year}-${month}-${day}`;
    });
  };

  /**
   * Format a date for display according to locale
   * nl: DD-MM-YYYY
   * en: YYYY-MM-DD
   */
  const formatDateDisplay = (date, regattaTimezone = null) => {
    return withValue(date, (val) => {
      const d = new Date(val);
      const parts = getDateTimeParts(d, regattaTimezone);
      const year = String(parts.year);
      const month = String(parts.month).padStart(2, '0');
      const day = String(parts.day).padStart(2, '0');

      const localeValue = currentLocale.value;
      if (localeValue === 'nl') {
        return `${day}-${month}-${year}`;
      }
      return `${year}-${month}-${day}`;
    });
  };

  /**
   * Format a time in 24-hour format (HH:mm) for scheduled times
   */
  const formatScheduledTime = (time, regattaTimezone = null) => {
    return withValue(time, (val) => {
      const d = new Date(val);
      const parts = getDateTimeParts(d, regattaTimezone);
      const hours = String(parts.hour).padStart(2, '0');
      const minutes = String(parts.minute).padStart(2, '0');
      return `${hours}:${minutes}`;
    });
  };

  /**
   * Format elapsed time in M:SS.mmm or H:MM:SS.mmm format
   * @param {number} milliseconds - Elapsed time in milliseconds
   */
  const formatElapsedTime = (milliseconds) => {
    return withNumericStringValue(milliseconds, (ms) => {
      const totalSeconds = Math.floor(ms / 1000);
      const msRemainder = ms % 1000;
      const seconds = totalSeconds % 60;
      const minutes = Math.floor(totalSeconds / 60) % 60;
      const hours = Math.floor(totalSeconds / 3600);

      const msStr = String(msRemainder).padStart(3, '0');
      const secStr = String(seconds).padStart(2, '0');

      if (hours > 0) {
        const minStr = String(minutes).padStart(2, '0');
        return `${hours}:${minStr}:${secStr}.${msStr}`;
      }

      return `${minutes}:${secStr}.${msStr}`;
    });
  };

  /**
   * Format delta time (time behind leader) in +M:SS.mmm or +H:MM:SS.mmm format
   * @param {number} milliseconds - Delta time in milliseconds
   */
  const formatDeltaTime = (milliseconds) => {
    return withNumericStringValue(milliseconds, (ms) => {
      if (ms === 0) {
        return '+0:00.000';
      }

      const formatted = formatElapsedTime(ms);
      return `+${formatted}`;
    });
  };

  /**
   * Format a timestamp in ISO 8601 format with timezone for technical use
   * Example: 2026-02-06T14:30:00+01:00
   */
  const formatTimestampISO = (date, regattaTimezone = null) => {
    return withValue(date, (val) => {
      const d = new Date(val);
      const parts = getDateTimeParts(d, regattaTimezone);
      const offsetMinutes = getOffsetMinutes(d, regattaTimezone);

      const year = String(parts.year);
      const month = String(parts.month).padStart(2, '0');
      const day = String(parts.day).padStart(2, '0');
      const hour = String(parts.hour).padStart(2, '0');
      const minute = String(parts.minute).padStart(2, '0');
      const second = String(parts.second).padStart(2, '0');

      return `${year}-${month}-${day}T${hour}:${minute}:${second}${formatOffset(offsetMinutes)}`;
    });
  };

  /**
   * Format a timestamp for display with locale-specific formatting
   * Includes date and time in regatta timezone
   */
  const formatTimestampDisplay = (date, regattaTimezone = null) => {
    return withValue(date, (val) => {
      const d = new Date(val);
      const datePart = formatDateDisplay(d, regattaTimezone);
      const timePart = formatScheduledTime(d, regattaTimezone);
      return `${datePart} ${timePart}`;
    });
  };

  /**
   * Round time to millisecond precision using round-half-up rule
   * @param {number} milliseconds - Time in milliseconds
   * @param {number} precision - Number of decimal places (default 3 for milliseconds)
   */
  const roundTime = (milliseconds, precision = 3) => {
    return withNumericNumberValue(milliseconds, (ms) => {
      if (precision <= 0) return ms;

      const clampedPrecision = Math.min(3, precision);
      const scale = Math.pow(10, 3 - clampedPrecision);
      return Math.round(ms / scale) * scale;
    }, 0);
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
