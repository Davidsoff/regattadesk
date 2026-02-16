package com.regattadesk.formatting;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;

/**
 * Date and time formatting utilities for RegattaDesk.
 * 
 * Formatting requirements:
 * - Technical formats: ISO 8601 YYYY-MM-DD
 * - Display formats: locale-dependent (nl: DD-MM-YYYY, en: YYYY-MM-DD)
 * - Time: 24-hour format (HH:mm for scheduled times)
 * - Timezone: regatta-local
 */
public class DateTimeFormatters {
    
    /**
     * ISO 8601 date formatter (YYYY-MM-DD) for technical/API use
     */
    public static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    
    /**
     * ISO 8601 datetime formatter with timezone for technical use
     */
    public static final DateTimeFormatter ISO_DATETIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    
    /**
     * 24-hour time formatter (HH:mm) for scheduled times
     */
    public static final DateTimeFormatter SCHEDULED_TIME = DateTimeFormatter.ofPattern("HH:mm");
    
    /**
     * Dutch date formatter (DD-MM-YYYY)
     */
    public static final DateTimeFormatter NL_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.forLanguageTag("nl"));
    
    /**
     * English date formatter (YYYY-MM-DD) - same as ISO
     */
    public static final DateTimeFormatter EN_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    
    /**
     * Format a date in ISO 8601 format (YYYY-MM-DD) for technical/API use
     */
    public static String formatDateISO(LocalDate date) {
        if (date == null) {
            return null;
        }
        return ISO_DATE.format(date);
    }
    
    /**
     * Format a date in ISO 8601 format (YYYY-MM-DD) for technical/API use
     */
    public static String formatDateISO(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return ISO_DATE.format(dateTime);
    }
    
    /**
     * Format a date for display according to locale
     * nl: DD-MM-YYYY
     * en: YYYY-MM-DD
     */
    public static String formatDateDisplay(LocalDate date, Locale locale) {
        if (date == null) {
            return null;
        }
        
        if (locale != null && "nl".equals(locale.getLanguage())) {
            return NL_DATE.format(date);
        }
        
        // Default to ISO format for 'en' and other locales
        return EN_DATE.format(date);
    }
    
    /**
     * Format a date for display according to locale
     * nl: DD-MM-YYYY
     * en: YYYY-MM-DD
     */
    public static String formatDateDisplay(ZonedDateTime dateTime, Locale locale) {
        if (dateTime == null) {
            return null;
        }
        
        return formatDateDisplay(dateTime.toLocalDate(), locale);
    }
    
    /**
     * Format a time in 24-hour format (HH:mm) for scheduled times
     */
    public static String formatScheduledTime(LocalTime time) {
        if (time == null) {
            return null;
        }
        return SCHEDULED_TIME.format(time);
    }
    
    /**
     * Format a time in 24-hour format (HH:mm) for scheduled times
     */
    public static String formatScheduledTime(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return SCHEDULED_TIME.format(dateTime);
    }
    
    /**
     * Format elapsed time in M:SS.mmm or H:MM:SS.mmm format
     * @param milliseconds Elapsed time in milliseconds
     */
    public static String formatElapsedTime(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long ms = milliseconds % 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, ms);
        }
        
        return String.format("%d:%02d.%03d", minutes, seconds, ms);
    }
    
    /**
     * Format delta time (time behind leader) in +M:SS.mmm or +H:MM:SS.mmm format
     * @param milliseconds Delta time in milliseconds
     */
    public static String formatDeltaTime(long milliseconds) {
        // Leader shows +0:00.000
        if (milliseconds == 0) {
            return "+0:00.000";
        }
        
        return "+" + formatElapsedTime(milliseconds);
    }
    
    /**
     * Format a timestamp in ISO 8601 format with timezone for technical use
     * Example: 2026-02-06T14:30:00+01:00
     */
    public static String formatTimestampISO(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return ISO_DATETIME.format(dateTime);
    }
    
    /**
     * Format a timestamp for display with locale-specific formatting
     * Includes date and time
     */
    public static String formatTimestampDisplay(ZonedDateTime dateTime, Locale locale) {
        if (dateTime == null) {
            return null;
        }
        
        String datePart = formatDateDisplay(dateTime, locale);
        String timePart = formatScheduledTime(dateTime);
        return datePart + " " + timePart;
    }
    
    /**
     * Convert a timestamp to the regatta's local timezone
     */
    public static ZonedDateTime toRegattaTimezone(ZonedDateTime dateTime, ZoneId regattaTimezone) {
        if (dateTime == null || regattaTimezone == null) {
            return dateTime;
        }
        return dateTime.withZoneSameInstant(regattaTimezone);
    }
    
    /**
     * Round time to millisecond precision using round-half-up rule
     * @param milliseconds Time in milliseconds
     * @param precision Number of decimal places (default 3 for milliseconds)
     */
    public static long roundTime(long milliseconds, int precision) {
        if (precision <= 0) {
            return milliseconds;
        }

        int clampedPrecision = Math.min(3, precision);
        long scale = (long) Math.pow(10, 3 - clampedPrecision);
        return Math.round((double) milliseconds / scale) * scale;
    }
}
