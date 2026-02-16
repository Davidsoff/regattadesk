package com.regattadesk.pdf;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class PdfGeneratorTest {
    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-06T13:30:00Z");
    private static final Clock UTC_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private static byte[] generatePdf(String regattaName,
                                      Integer drawRevision,
                                      Integer resultsRevision,
                                      Locale locale,
                                      ZoneId zoneId) throws IOException {
        return PdfGenerator.generateSamplePdf(
            regattaName,
            drawRevision,
            resultsRevision,
            locale,
            zoneId,
            UTC_CLOCK
        );
    }

    private static void assertValidPdf(byte[] pdf) {
        assertNotNull(pdf);
        assertTrue(pdf.length > 4);
        assertTrue(pdf[0] == '%');
        assertTrue(pdf[1] == 'P');
        assertTrue(pdf[2] == 'D');
        assertTrue(pdf[3] == 'F');
        assertTrue(pdf[4] == '-');
    }

    private static String extractFirstPageText(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            String pageText = new PdfTextExtractor(reader).getTextFromPage(1);
            return pageText.replaceAll("\\s+", " ").trim();
        } finally {
            reader.close();
        }
    }
    
    @Test
    void testGenerateSamplePdf() throws IOException {
        String regattaName = "Amsterdam Head Race 2026";
        byte[] pdf = generatePdf(regattaName, 1, 3, Locale.forLanguageTag("nl"), ZoneId.of("Europe/Amsterdam"));
        assertValidPdf(pdf);

        String text = extractFirstPageText(pdf);
        assertTrue(text.contains(regattaName));
        assertTrue(text.contains("Gegenereerd:"));
        assertTrue(text.contains("06-02-2026 14:30"));
        assertTrue(text.contains("Lotingversie: v1"));
        assertTrue(text.contains("Resultatenversie: v3"));
        assertTrue(text.contains("Pagina 1"));
    }
    
    @Test
    void testGenerateSamplePdfEnglish() throws IOException {
        String regattaName = "London Head Race 2026";
        byte[] pdf = generatePdf(regattaName, 2, 5, Locale.ENGLISH, ZoneId.of("Europe/London"));
        assertValidPdf(pdf);

        String text = extractFirstPageText(pdf);
        assertTrue(text.contains(regattaName));
        assertTrue(text.contains("Generated:"));
        assertTrue(text.contains("2026-02-06 13:30"));
        assertTrue(text.contains("Draw Version: v2"));
        assertTrue(text.contains("Results Version: v5"));
        assertTrue(text.contains("Page 1"));
    }
    
    @Test
    void testGenerateSamplePdfWithoutRevisions() throws IOException {
        String regattaName = "Test Regatta";
        byte[] pdf = generatePdf(regattaName, null, null, Locale.ENGLISH, ZoneId.of("UTC"));
        assertValidPdf(pdf);

        String text = extractFirstPageText(pdf);
        assertTrue(text.contains(regattaName));
        assertTrue(text.contains("Generated:"));
        assertTrue(text.contains("2026-02-06 13:30"));
        assertFalse(text.contains("Draw Version:"));
        assertFalse(text.contains("Results Version:"));
    }

    @Test
    void testGenerateSamplePdfWithNullLocale() throws IOException {
        byte[] pdf = generatePdf("Test Regatta", 1, 2, null, ZoneId.of("Europe/Amsterdam"));
        assertValidPdf(pdf);
        String text = extractFirstPageText(pdf);
        assertTrue(text.contains("Generated:"));
        assertTrue(text.contains("Draw Version: v1"));
        assertTrue(text.contains("Results Version: v2"));
    }

    @Test
    void testGenerateSamplePdfWithEmptyRegattaName() throws IOException {
        byte[] pdf = generatePdf("", 1, 2, Locale.ENGLISH, ZoneId.of("Europe/Amsterdam"));
        assertValidPdf(pdf);
        String text = extractFirstPageText(pdf);
        assertTrue(text.contains("Generated:"));
    }

    @Test
    void testGenerateSamplePdfWithNegativeRevisions() throws IOException {
        byte[] pdf = generatePdf("Test Regatta", -1, -5, Locale.ENGLISH, ZoneId.of("Europe/Amsterdam"));
        assertValidPdf(pdf);
        String text = extractFirstPageText(pdf);
        assertTrue(text.contains("Draw Version: v-1"));
        assertTrue(text.contains("Results Version: v-5"));
    }

    @Test
    void testGenerateSamplePdfWithNullTimezoneThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            PdfGenerator.generateSamplePdf("Test Regatta", 1, 2, Locale.ENGLISH, null)
        );
    }

    @Test
    void testGenerateSamplePdfUsesRegattaTimezoneNotClockZone() throws IOException {
        byte[] pdf = PdfGenerator.generateSamplePdf(
            "Timezone Test",
            1,
            1,
            Locale.ENGLISH,
            ZoneId.of("Europe/Amsterdam"),
            Clock.fixed(Instant.parse("2026-02-06T13:30:00Z"), ZoneId.of("America/New_York"))
        );

        String text = extractFirstPageText(pdf);
        assertTrue(text.contains("Generated: 2026-02-06 14:30"));
    }
}
