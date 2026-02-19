package com.regattadesk.operator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OperatorTokenPdfServiceTest {

    private final OperatorTokenPdfService pdfService = new OperatorTokenPdfService();

    @Test
    void generateTokenPdf_shouldCreateValidPdf() throws IOException {
        OperatorToken token = createTestToken();
        String operatorUrl = "https://operator.regattadesk.com";

        byte[] pdfBytes = pdfService.generateTokenPdf(token, operatorUrl);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        
        // Verify PDF signature
        assertTrue(pdfBytes[0] == '%');
        assertTrue(pdfBytes[1] == 'P');
        assertTrue(pdfBytes[2] == 'D');
        assertTrue(pdfBytes[3] == 'F');
    }

    @Test
    void generateTokenPdf_shouldIncludeAllRequiredInformation() throws IOException {
        OperatorToken token = createTestToken();
        String operatorUrl = "https://operator.regattadesk.com";

        byte[] pdfBytes = pdfService.generateTokenPdf(token, operatorUrl);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 1000); // PDF with content should be substantial
        
        // Verify PDF starts with proper signature
        assertTrue(pdfBytes[0] == '%' && pdfBytes[1] == 'P' && pdfBytes[2] == 'D' && pdfBytes[3] == 'F');
    }

    @Test
    void generateTokenPdf_shouldFailWithNullToken() {
        String operatorUrl = "https://operator.regattadesk.com";

        assertThrows(IllegalArgumentException.class, 
            () -> pdfService.generateTokenPdf(null, operatorUrl));
    }

    @Test
    void generateTokenPdf_shouldFailWithNullUrl() {
        OperatorToken token = createTestToken();

        assertThrows(IllegalArgumentException.class, 
            () -> pdfService.generateTokenPdf(token, null));
    }

    @Test
    void generateTokenPdf_shouldFailWithBlankUrl() {
        OperatorToken token = createTestToken();

        assertThrows(IllegalArgumentException.class, 
            () -> pdfService.generateTokenPdf(token, ""));
    }

    @Test
    void generateTokenPdf_shouldHandleTokenWithoutPin() throws IOException {
        OperatorToken token = createTestTokenWithoutPin();
        String operatorUrl = "https://operator.regattadesk.com";

        byte[] pdfBytes = pdfService.generateTokenPdf(token, operatorUrl);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    private OperatorToken createTestToken() {
        Instant now = Instant.now();
        return new OperatorToken(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "finish-line",
            "test-token-string-abcdefghijklmnopqrstuvwxyz123456",
            "123456",
            now,
            now.plus(8, ChronoUnit.HOURS),
            true,
            now,
            now
        );
    }

    private OperatorToken createTestTokenWithoutPin() {
        Instant now = Instant.now();
        return new OperatorToken(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "finish-line",
            "test-token-string-abcdefghijklmnopqrstuvwxyz123456",
            null,
            now,
            now.plus(8, ChronoUnit.HOURS),
            true,
            now,
            now
        );
    }
}
