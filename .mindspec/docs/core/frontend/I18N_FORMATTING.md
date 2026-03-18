# I18n, Locale Formatting, and Printing Guide

## Overview

RegattaDesk supports Dutch (nl) and English (en) localization with proper date/time formatting and printable A4 outputs.

## Features

### Supported Locales

- **Dutch (nl)**: Default locale
- **English (en)**: Alternative locale

### Date and Time Formatting

#### Technical Formats (for APIs)

- Date: ISO 8601 `YYYY-MM-DD` (e.g., `2026-02-06`)
- Timestamp: ISO 8601 with timezone (e.g., `2026-02-06T14:30:00+01:00`)

#### Display Formats (locale-dependent)

- **Dutch (nl)**: `DD-MM-YYYY` (e.g., `06-02-2026`)
- **English (en)**: `YYYY-MM-DD` (e.g., `2026-02-06`)

#### Time Formats

- Scheduled times: 24-hour format `HH:mm` (e.g., `14:30`)
- Elapsed times: `M:SS.mmm` or `H:MM:SS.mmm` (e.g., `5:23.456` or `1:15:42.789`)
- Delta times: `+M:SS.mmm` or `+H:MM:SS.mmm` (e.g., `+1:15.234` or `+1:05:30.500`)

### Print and PDF Output

- **Page Size**: A4 (210 mm × 297 mm)
- **Margins**: 20 mm top/bottom, 15 mm left/right
- **Design**: Monochrome-friendly (grayscale only)
- **Header includes**:
  - Regatta name
  - Generated timestamp
  - Draw revision (if applicable)
  - Results revision (if applicable)
  - Page number

## Frontend Usage

### Using i18n in Components

```vue
<script setup>
import { useI18n } from 'vue-i18n';

const { t } = useI18n();
</script>

<template>
  <button>{{ t('common.save') }}</button>
  <span>{{ t('status.dns') }}</span>
</template>
```

### Switching Locales

```vue
<script setup>
import { useLocale } from '@/composables/useLocale';

const { currentLocale, switchLocale, supportedLocales } = useLocale();

function changeLanguage(locale) {
  switchLocale(locale);
}
</script>

<template>
  <select @change="changeLanguage($event.target.value)" :value="currentLocale">
    <option v-for="locale in supportedLocales" :key="locale" :value="locale">
      {{ locale.toUpperCase() }}
    </option>
  </select>
</template>
```

### Formatting Dates and Times

```vue
<script setup>
import { useFormatting } from '@/composables/useFormatting';
import { useI18n } from 'vue-i18n';

const { locale } = useI18n();
const formatting = useFormatting(locale);

const date = new Date('2026-02-06T14:30:00Z');
const elapsedMs = (5 * 60 * 1000) + (23 * 1000) + 456; // 5:23.456
</script>

<template>
  <div>
    <!-- Display date in locale-specific format -->
    <p>Date: {{ formatting.formatDateDisplay(date) }}</p>
    
    <!-- Technical ISO date -->
    <p>ISO Date: {{ formatting.formatDateISO(date) }}</p>
    
    <!-- Scheduled time -->
    <p>Time: {{ formatting.formatScheduledTime(date) }}</p>
    
    <!-- Elapsed time -->
    <p>Elapsed: {{ formatting.formatElapsedTime(elapsedMs) }}</p>
    
    <!-- Delta time -->
    <p>Delta: {{ formatting.formatDeltaTime(elapsedMs) }}</p>
  </div>
</template>
```

### Using the Print Header

```vue
<script setup>
import PrintHeader from '@/components/print/PrintHeader.vue';

const regattaName = 'Amsterdam Head Race 2026';
const drawRevision = 1;
const resultsRevision = 3;
</script>

<template>
  <div class="print-page">
    <PrintHeader
      :regattaName="regattaName"
      :drawRevision="drawRevision"
      :resultsRevision="resultsRevision"
      :timestamp="new Date()"
    />
    
    <!-- Your content here -->
  </div>
</template>
```

### Applying Print Styles

To preview print layout, add the `print-preview` class to the body element:

```vue
<script setup>
import { onMounted, onUnmounted } from 'vue';

onMounted(() => {
  document.body.classList.add('print-preview');
});

onUnmounted(() => {
  document.body.classList.remove('print-preview');
});
</script>
```

## Backend Usage

### Formatting Dates and Times in Java

```java
import com.regattadesk.formatting.DateTimeFormatters;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Locale;

// ISO 8601 date formatting
LocalDate date = LocalDate.of(2026, 2, 6);
String isoDate = DateTimeFormatters.formatDateISO(date); // "2026-02-06"

// Locale-specific date display
Locale nlLocale = Locale.forLanguageTag("nl");
String nlDate = DateTimeFormatters.formatDateDisplay(date, nlLocale); // "06-02-2026"

Locale enLocale = Locale.forLanguageTag("en");
String enDate = DateTimeFormatters.formatDateDisplay(date, enLocale); // "2026-02-06"

// Scheduled time
ZonedDateTime dateTime = ZonedDateTime.now();
String time = DateTimeFormatters.formatScheduledTime(dateTime); // "14:30"

// Elapsed time
long elapsedMs = (5 * 60 * 1000) + (23 * 1000) + 456;
String elapsed = DateTimeFormatters.formatElapsedTime(elapsedMs); // "5:23.456"

// Delta time
String delta = DateTimeFormatters.formatDeltaTime(elapsedMs); // "+5:23.456"
```

### Generating PDFs

```java
import com.regattadesk.pdf.PdfGenerator;
import java.time.ZoneId;
import java.util.Locale;

// Generate a sample PDF
String regattaName = "Amsterdam Head Race 2026";
Integer drawRevision = 1;
Integer resultsRevision = 3;
Locale locale = Locale.forLanguageTag("nl");
ZoneId regattaTimezone = ZoneId.of("Europe/Amsterdam"); // required

byte[] pdfBytes = PdfGenerator.generateSamplePdf(
    regattaName, 
    drawRevision, 
    resultsRevision, 
    locale,
    regattaTimezone
);

// Save or return the PDF bytes
```

## Translation Keys

### Common

- `common.save` - Save
- `common.cancel` - Cancel
- `common.delete` - Delete
- `common.edit` - Edit
- `common.print` - Print
- `common.download` - Download

### Status

- `status.dns` - DNS (Did Not Start)
- `status.dnf` - DNF (Did Not Finish)
- `status.dsq` - DSQ (Disqualified)
- `status.provisional` - Provisional
- `status.official` - Official

### Regatta

- `regatta.name` - Regatta
- `regatta.schedule` - Schedule
- `regatta.results` - Results
- `regatta.draw_revision` - Draw Revision
- `regatta.results_revision` - Results Revision

### Print

- `print.generated` - Generated
- `print.page` - Page
- `print.of` - of
- `print.draw_version` - Draw Version
- `print.results_version` - Results Version

See `src/i18n/locales/en.json` and `src/i18n/locales/nl.json` for complete translation catalogs.

## Adding New Translations

1. Add new keys to both `en.json` and `nl.json`:

```json
{
  "myFeature": {
    "title": "My Feature Title",
    "description": "My feature description"
  }
}
```

1. Use in components:

```vue
<template>
  <h1>{{ t('myFeature.title') }}</h1>
  <p>{{ t('myFeature.description') }}</p>
</template>
```

## Testing

### Frontend Tests

Run frontend tests:

```bash
cd apps/frontend
npm run test
```

### Backend Tests

Run backend tests:

```bash
cd apps/backend
./mvnw test
```

## Notes

- Locale preference is stored in localStorage as `regattadesk-locale`
- Browser language is used as fallback if no preference is stored
- Default locale is Dutch (nl) if no preference or unsupported browser language
- All timezone handling uses regatta-local timezone
- PDF generation uses OpenPDF (LGPL license)
