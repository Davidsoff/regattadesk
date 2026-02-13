package com.regattadesk.formatting;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class DateTimeFormattersTest {
    
    @Test
    void testFormatDateISO() {
        LocalDate date = LocalDate.of(2026, 2, 6);
        assertEquals("2026-02-06", DateTimeFormatters.formatDateISO(date));
    }
    
    @Test
    void testFormatDateISOWithNull() {
        assertNull(DateTimeFormatters.formatDateISO((LocalDate) null));
    }
    
    @Test
    void testFormatDateDisplayDutch() {
        LocalDate date = LocalDate.of(2026, 2, 6);
        Locale nlLocale = Locale.forLanguageTag("nl");
        assertEquals("06-02-2026", DateTimeFormatters.formatDateDisplay(date, nlLocale));
    }
    
    @Test
    void testFormatDateDisplayEnglish() {
        LocalDate date = LocalDate.of(2026, 2, 6);
        Locale enLocale = Locale.forLanguageTag("en");
        assertEquals("2026-02-06", DateTimeFormatters.formatDateDisplay(date, enLocale));
    }
    
    @Test
    void testFormatDateDisplayNull() {
        assertNull(DateTimeFormatters.formatDateDisplay((LocalDate) null, Locale.ENGLISH));
    }
    
    @Test
    void testFormatScheduledTime() {
        LocalTime time = LocalTime.of(14, 30);
        assertEquals("14:30", DateTimeFormatters.formatScheduledTime(time));
    }
    
    @Test
    void testFormatScheduledTimeNull() {
        assertNull(DateTimeFormatters.formatScheduledTime((LocalTime) null));
    }
    
    @Test
    void testFormatElapsedTimeUnderOneHour() {
        long ms = (5 * 60 * 1000) + (23 * 1000) + 456; // 5:23.456
        assertEquals("5:23.456", DateTimeFormatters.formatElapsedTime(ms));
    }
    
    @Test
    void testFormatElapsedTimeOverOneHour() {
        long ms = (1 * 60 * 60 * 1000) + (15 * 60 * 1000) + (42 * 1000) + 789; // 1:15:42.789
        assertEquals("1:15:42.789", DateTimeFormatters.formatElapsedTime(ms));
    }
    
    @Test
    void testFormatElapsedTimePadsZeros() {
        long ms = (2 * 1000) + 45; // 0:02.045
        assertEquals("0:02.045", DateTimeFormatters.formatElapsedTime(ms));
    }
    
    @Test
    void testFormatElapsedTimeZero() {
        assertEquals("0:00.000", DateTimeFormatters.formatElapsedTime(0));
    }
    
    @Test
    void testFormatDeltaTimeLeader() {
        assertEquals("+0:00.000", DateTimeFormatters.formatDeltaTime(0));
    }
    
    @Test
    void testFormatDeltaTimeWithPrefix() {
        long ms = (1 * 60 * 1000) + (15 * 1000) + 234; // +1:15.234
        assertEquals("+1:15.234", DateTimeFormatters.formatDeltaTime(ms));
    }
    
    @Test
    void testFormatDeltaTimeLarge() {
        long ms = (1 * 60 * 60 * 1000) + (5 * 60 * 1000) + (30 * 1000) + 500; // +1:05:30.500
        assertEquals("+1:05:30.500", DateTimeFormatters.formatDeltaTime(ms));
    }
    
    @Test
    void testFormatTimestampISO() {
        ZonedDateTime dateTime = ZonedDateTime.of(
            2026, 2, 6, 14, 30, 0, 0,
            ZoneId.of("Europe/Amsterdam")
        );
        String formatted = DateTimeFormatters.formatTimestampISO(dateTime);
        assertTrue(formatted.startsWith("2026-02-06T14:30:00"));
    }
    
    @Test
    void testFormatTimestampISONull() {
        assertNull(DateTimeFormatters.formatTimestampISO(null));
    }
    
    @Test
    void testFormatTimestampDisplay() {
        ZonedDateTime dateTime = ZonedDateTime.of(
            2026, 2, 6, 14, 30, 0, 0,
            ZoneId.of("Europe/Amsterdam")
        );
        Locale nlLocale = Locale.forLanguageTag("nl");
        assertEquals("06-02-2026 14:30", DateTimeFormatters.formatTimestampDisplay(dateTime, nlLocale));
    }
    
    @Test
    void testFormatTimestampDisplayNull() {
        assertNull(DateTimeFormatters.formatTimestampDisplay(null, Locale.ENGLISH));
    }
    
    @Test
    void testToRegattaTimezone() {
        ZonedDateTime utcTime = ZonedDateTime.of(
            2026, 2, 6, 13, 30, 0, 0,
            ZoneId.of("UTC")
        );
        ZoneId amsterdam = ZoneId.of("Europe/Amsterdam");
        ZonedDateTime converted = DateTimeFormatters.toRegattaTimezone(utcTime, amsterdam);
        
        assertEquals(14, converted.getHour()); // UTC+1 in winter
        assertEquals(amsterdam, converted.getZone());
    }
    
    @Test
    void testToRegattaTimezoneNull() {
        assertNull(DateTimeFormatters.toRegattaTimezone(null, ZoneId.of("UTC")));
    }
}
