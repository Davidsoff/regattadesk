package com.regattadesk.pdf;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class PdfGeneratorTest {
    
    @Test
    void testGenerateSamplePdf() throws IOException {
        String regattaName = "Amsterdam Head Race 2026";
        Integer drawRevision = 1;
        Integer resultsRevision = 3;
        Locale locale = Locale.forLanguageTag("nl");
        
        byte[] pdf = PdfGenerator.generateSamplePdf(regattaName, drawRevision, resultsRevision, locale);
        
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        
        // Verify PDF magic bytes (%PDF-)
        assertTrue(pdf[0] == '%');
        assertTrue(pdf[1] == 'P');
        assertTrue(pdf[2] == 'D');
        assertTrue(pdf[3] == 'F');
        assertTrue(pdf[4] == '-');
    }
    
    @Test
    void testGenerateSamplePdfEnglish() throws IOException {
        String regattaName = "London Head Race 2026";
        Integer drawRevision = 2;
        Integer resultsRevision = 5;
        Locale locale = Locale.ENGLISH;
        
        byte[] pdf = PdfGenerator.generateSamplePdf(regattaName, drawRevision, resultsRevision, locale);
        
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }
    
    @Test
    void testGenerateSamplePdfWithoutRevisions() throws IOException {
        String regattaName = "Test Regatta";
        
        byte[] pdf = PdfGenerator.generateSamplePdf(regattaName, null, null, Locale.ENGLISH);
        
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }
}
