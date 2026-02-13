# Backend I18n and Formatting

## Date and Time Formatting

The `DateTimeFormatters` class provides utilities for formatting dates, times, and timestamps according to RegattaDesk requirements.

### ISO 8601 Formatting (Technical/API Use)

```java
import com.regattadesk.formatting.DateTimeFormatters;
import java.time.LocalDate;
import java.time.ZonedDateTime;

// Format date in ISO 8601 format
LocalDate date = LocalDate.of(2026, 2, 6);
String isoDate = DateTimeFormatters.formatDateISO(date);
// Result: "2026-02-06"

// Format timestamp in ISO 8601 format
ZonedDateTime dateTime = ZonedDateTime.now();
String isoTimestamp = DateTimeFormatters.formatTimestampISO(dateTime);
// Result: "2026-02-06T14:30:00+01:00"
```

### Locale-Dependent Display Formatting

```java
import java.util.Locale;

// Dutch locale (DD-MM-YYYY)
Locale nlLocale = Locale.forLanguageTag("nl");
String nlDate = DateTimeFormatters.formatDateDisplay(date, nlLocale);
// Result: "06-02-2026"

// English locale (YYYY-MM-DD)
Locale enLocale = Locale.forLanguageTag("en");
String enDate = DateTimeFormatters.formatDateDisplay(date, enLocale);
// Result: "2026-02-06"
```

### Time Formatting

```java
import java.time.LocalTime;

// Scheduled time (24-hour format)
LocalTime time = LocalTime.of(14, 30);
String scheduledTime = DateTimeFormatters.formatScheduledTime(time);
// Result: "14:30"

// Elapsed time (M:SS.mmm or H:MM:SS.mmm)
long elapsedMs = (5 * 60 * 1000) + (23 * 1000) + 456; // 5:23.456
String elapsedTime = DateTimeFormatters.formatElapsedTime(elapsedMs);
// Result: "5:23.456"

long longElapsedMs = (1 * 60 * 60 * 1000) + (15 * 60 * 1000) + (42 * 1000) + 789;
String longElapsedTime = DateTimeFormatters.formatElapsedTime(longElapsedMs);
// Result: "1:15:42.789"

// Delta time (time behind leader)
long deltaMs = (1 * 60 * 1000) + (15 * 1000) + 234;
String deltaTime = DateTimeFormatters.formatDeltaTime(deltaMs);
// Result: "+1:15.234"

// Leader always shows +0:00.000
String leaderDelta = DateTimeFormatters.formatDeltaTime(0);
// Result: "+0:00.000"
```

### Timezone Conversion

```java
import java.time.ZoneId;

// Convert timestamp to regatta timezone
ZonedDateTime utcTime = ZonedDateTime.now(ZoneId.of("UTC"));
ZoneId regattaTimezone = ZoneId.of("Europe/Amsterdam");

ZonedDateTime localTime = DateTimeFormatters.toRegattaTimezone(utcTime, regattaTimezone);
```

## PDF Generation

The `PdfGenerator` class provides utilities for generating PDF documents with RegattaDesk branding and formatting.

### Features

- **A4 Page Size**: 210mm × 297mm
- **Margins**: 20mm top/bottom, 15mm left/right
- **Monochrome**: Grayscale-only for print-friendliness
- **Header on Every Page**: Includes regatta name, timestamp, revisions, page number
- **Footer**: RegattaDesk wordmark

### Basic Usage

```java
import com.regattadesk.pdf.PdfGenerator;
import java.util.Locale;

String regattaName = "Amsterdam Head Race 2026";
Integer drawRevision = 1;
Integer resultsRevision = 3;
Locale locale = Locale.forLanguageTag("nl");

byte[] pdfBytes = PdfGenerator.generateSamplePdf(
    regattaName,
    drawRevision,
    resultsRevision,
    locale
);

// Save or stream the PDF bytes
```

### Custom PDF Generation

```java
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.time.ZonedDateTime;

// Create document
Document document = PdfGenerator.createDocument();
ByteArrayOutputStream baos = new ByteArrayOutputStream();
PdfWriter writer = PdfWriter.getInstance(document, baos);

// Add header/footer event handler
ZonedDateTime now = ZonedDateTime.now();
PdfGenerator.RegattaDeskHeaderFooter headerFooter = 
    new PdfGenerator.RegattaDeskHeaderFooter(
        "My Regatta",
        now,
        1,  // drawRevision
        3,  // resultsRevision
        Locale.forLanguageTag("nl")
    );
writer.setPageEvent(headerFooter);

document.open();

// Add content
document.add(new Paragraph("Your content here"));

document.close();

byte[] pdfBytes = baos.toByteArray();
```

## Dependencies

### OpenPDF

RegattaDesk uses [OpenPDF](https://github.com/LibrePDF/OpenPDF) (version 2.0.4) for PDF generation.

- **License**: LGPL (Lesser General Public License)
- **Purpose**: Generate A4 PDFs with custom headers and monochrome-friendly styling
- **Maven Dependency**:
  ```xml
  <dependency>
      <groupId>com.github.librepdf</groupId>
      <artifactId>openpdf</artifactId>
      <version>2.0.4</version>
  </dependency>
  ```

## Testing

All formatters and PDF generation functionality have comprehensive unit tests.

### Run Tests

```bash
# Run all tests
./mvnw test

# Run specific test classes
./mvnw test -Dtest=DateTimeFormattersTest
./mvnw test -Dtest=PdfGeneratorTest
```

### Test Coverage

- **DateTimeFormattersTest**: 20 tests covering all formatting methods
- **PdfGeneratorTest**: 3 tests verifying PDF generation and structure

## Design Requirements

All date/time formatting and PDF generation follows the requirements specified in:

- `pdd/design/detailed-design.md`
- `pdd/design/style-guide.md`
- `pdd/implementation/bc05-public-experience-and-delivery.md`

### Key Requirements

1. **ISO 8601 Dates**: Technical formats use `YYYY-MM-DD`
2. **Locale-Specific Display**: Dutch uses `DD-MM-YYYY`, English uses `YYYY-MM-DD`
3. **24-Hour Time**: All time displays use 24-hour format
4. **Millisecond Precision**: Elapsed times show `.mmm` precision
5. **Monochrome PDFs**: All PDFs are grayscale-only for print-friendliness
6. **A4 Layout**: Standard A4 size with specified margins
7. **Required Metadata**: All PDFs include regatta name, timestamp, revisions, page numbers
