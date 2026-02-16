package com.regattadesk.pdf;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.ZoneId;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class PdfGeneratorTest {

    private static void assertPdfMagicBytes(byte[] pdf) {
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
            return new PdfTextExtractor(reader).getTextFromPage(1);
        } finally {
            reader.close();
        }
    }
    
    @Test
    void testGenerateSamplePdf() throws IOException {
        String regattaName = "Amsterdam Head Race 2026";
        Integer drawRevision = 1;
        Integer resultsRevision = 3;
        Locale locale = Locale.forLanguageTag("nl");
        
        byte[] pdf = PdfGenerator.generateSamplePdf(
            regattaName,
            drawRevision,
            resultsRevision,
            locale,
            ZoneId.of("Europe/Amsterdam")
        );
        
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        assertPdfMagicBytes(pdf);

        String text = extractFirstPageText(pdf);
        assertTrue(text.contains(regattaName));
        assertTrue(text.contains("Gegenereerd:"));
        assertTrue(text.contains("Lotingversie: v1"));
        assertTrue(text.contains("Resultatenversie: v3"));
        assertTrue(text.contains("Pagina 1"));
    }
    
    @Test
    void testGenerateSamplePdfEnglish() throws IOException {
        String regattaName = "London Head Race 2026";
        Integer drawRevision = 2;
        Integer resultsRevision = 5;
        Locale locale = Locale.ENGLISH;
        
        byte[] pdf = PdfGenerator.generateSamplePdf(
            regattaName,
            drawRevision,
            resultsRevision,
            locale,
            ZoneId.of("Europe/London")
        );
        
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        assertPdfMagicBytes(pdf);

        String text = extractFirstPageText(pdf);
        assertTrue(text.contains(regattaName));
        assertTrue(text.contains("Generated:"));
        assertTrue(text.contains("Draw Version: v2"));
        assertTrue(text.contains("Results Version: v5"));
        assertTrue(text.contains("Page 1"));
    }
    
    @Test
    void testGenerateSamplePdfWithoutRevisions() throws IOException {
        String regattaName = "Test Regatta";
        
        byte[] pdf = PdfGenerator.generateSamplePdf(
            regattaName,
            null,
            null,
            Locale.ENGLISH,
            ZoneId.of("UTC")
        );
        
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        assertPdfMagicBytes(pdf);

        String text = extractFirstPageText(pdf);
        assertTrue(text.contains(regattaName));
        assertTrue(text.contains("Generated:"));
        assertFalse(text.contains("Draw Version:"));
        assertFalse(text.contains("Results Version:"));
    }

    @Test
    void testGenerateSamplePdfWithNullLocale() throws IOException {
        byte[] pdf = PdfGenerator.generateSamplePdf(
            "Test Regatta",
            1,
            2,
            null,
            ZoneId.of("Europe/Amsterdam")
        );

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        assertPdfMagicBytes(pdf);
    }

    @Test
    void testGenerateSamplePdfWithEmptyRegattaName() throws IOException {
        byte[] pdf = PdfGenerator.generateSamplePdf(
            "",
            1,
            2,
            Locale.ENGLISH,
            ZoneId.of("Europe/Amsterdam")
        );

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        assertPdfMagicBytes(pdf);
    }

    @Test
    void testGenerateSamplePdfWithNegativeRevisions() throws IOException {
        byte[] pdf = PdfGenerator.generateSamplePdf(
            "Test Regatta",
            -1,
            -5,
            Locale.ENGLISH,
            ZoneId.of("Europe/Amsterdam")
        );

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        assertPdfMagicBytes(pdf);
    }

    @Test
    void testGenerateSamplePdfWithNullTimezoneThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            PdfGenerator.generateSamplePdf("Test Regatta", 1, 2, Locale.ENGLISH, null)
        );
    }
}
