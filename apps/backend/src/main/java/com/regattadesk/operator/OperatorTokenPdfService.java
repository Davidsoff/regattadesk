package com.regattadesk.operator;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Service for generating PDF documents containing operator token information.
 * 
 * Generates printable token sheets with QR codes and fallback instructions.
 */
@ApplicationScoped
public class OperatorTokenPdfService {
    
    private static final int QR_CODE_SIZE = 200;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    
    /**
     * Generates a PDF document for an operator token.
     * 
     * The PDF includes:
     * - QR code for quick scanning
     * - Token details (station, validity period)
     * - Fallback instructions with token string and PIN
     * 
     * @param token the operator token to export
     * @param operatorUrl the base URL for the operator interface
     * @return PDF document as byte array
     * @throws IOException if PDF generation fails
     */
    public byte[] generateTokenPdf(OperatorToken token, String operatorUrl) throws IOException {
        if (token == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }
        if (operatorUrl == null || operatorUrl.isBlank()) {
            throw new IllegalArgumentException("Operator URL cannot be null or blank");
        }
        
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                float margin = 50;
                float yPosition = page.getMediaBox().getHeight() - margin;
                float pageWidth = page.getMediaBox().getWidth();
                
                // Title
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 20);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("RegattaDesk Operator Token");
                contentStream.endText();
                yPosition -= 40;
                
                // Station information
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Station: " + token.getStation());
                contentStream.endText();
                yPosition -= 30;
                
                // Validity information
                String validFrom = token.getValidFrom().atZone(ZoneId.systemDefault()).format(DATE_FORMAT);
                String validUntil = token.getValidUntil().atZone(ZoneId.systemDefault()).format(DATE_FORMAT);
                
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Valid from: " + validFrom);
                contentStream.endText();
                yPosition -= 20;
                
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Valid until: " + validUntil);
                contentStream.endText();
                yPosition -= 40;
                
                // QR Code
                try {
                    String tokenUrl = operatorUrl + "?token=" + token.getToken();
                    BufferedImage qrImage = generateQRCode(tokenUrl);
                    PDImageXObject pdImage = LosslessFactory.createFromImage(document, qrImage);
                    
                    contentStream.drawImage(pdImage, margin, yPosition - QR_CODE_SIZE, QR_CODE_SIZE, QR_CODE_SIZE);
                    yPosition -= QR_CODE_SIZE + 40;
                } catch (WriterException e) {
                    throw new IOException("Failed to generate QR code", e);
                }
                
                // Fallback instructions section
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Fallback Instructions");
                contentStream.endText();
                yPosition -= 25;
                
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("If QR code scanning fails, use the following manual login:");
                contentStream.endText();
                yPosition -= 25;
                
                // URL
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("1. Open URL:");
                contentStream.endText();
                yPosition -= 15;
                
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER), 10);
                contentStream.newLineAtOffset(margin + 20, yPosition);
                contentStream.showText(operatorUrl);
                contentStream.endText();
                yPosition -= 25;
                
                // Token
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("2. Enter Token:");
                contentStream.endText();
                yPosition -= 15;
                
                // Split token into chunks for better readability
                String tokenString = token.getToken();
                int chunkSize = 20;
                for (int i = 0; i < tokenString.length(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, tokenString.length());
                    String chunk = tokenString.substring(i, end);
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER), 10);
                    contentStream.newLineAtOffset(margin + 20, yPosition);
                    contentStream.showText(chunk);
                    contentStream.endText();
                    yPosition -= 15;
                }
                yPosition -= 10;
                
                // PIN
                if (token.getPin() != null) {
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText("3. Station Handoff PIN:");
                    contentStream.endText();
                    yPosition -= 15;
                    
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD), 14);
                    contentStream.newLineAtOffset(margin + 20, yPosition);
                    contentStream.showText(token.getPin());
                    contentStream.endText();
                    yPosition -= 25;
                }
                
                // Footer note
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Keep this document secure. The token grants access to operator workflows.");
                contentStream.endText();
            }
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
    
    /**
     * Generates a QR code image from the given content.
     * 
     * @param content the content to encode
     * @return BufferedImage containing the QR code
     * @throws WriterException if QR code generation fails
     */
    private BufferedImage generateQRCode(String content) throws WriterException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE);
        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }
}
