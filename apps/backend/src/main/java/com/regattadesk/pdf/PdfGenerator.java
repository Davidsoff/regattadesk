package com.regattadesk.pdf;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.regattadesk.formatting.DateTimeFormatters;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * PDF generation service for RegattaDesk.
 * 
 * Requirements:
 * - A4 page size (210mm x 297mm)
 * - Margins: 20mm top/bottom, 15mm left/right
 * - Header on every page: regatta name, generated timestamp, draw/results revisions, page number
 * - Monochrome-friendly output (grayscale only)
 * - Minimum 300 DPI
 */
public class PdfGenerator {
    
    // A4 page size in points (1 point = 1/72 inch)
    private static final Rectangle PAGE_SIZE = PageSize.A4;
    
    // Convert mm to points (1 inch = 72 points = 25.4mm)
    private static final float MM_TO_POINTS = 72f / 25.4f;
    
    // Margins in points (20mm top/bottom, 15mm left/right)
    private static final float MARGIN_TOP = 20 * MM_TO_POINTS;
    private static final float MARGIN_BOTTOM = 20 * MM_TO_POINTS;
    private static final float MARGIN_LEFT = 15 * MM_TO_POINTS;
    private static final float MARGIN_RIGHT = 15 * MM_TO_POINTS;
    
    // Font sizes (in points)
    private static final float FONT_SIZE_TITLE = 14f;
    private static final float FONT_SIZE_BODY = 10f;
    private static final float FONT_SIZE_META = 8f;
    
    /**
     * Create a new PDF document with RegattaDesk standard formatting
     */
    public static Document createDocument() {
        Document document = new Document(PAGE_SIZE, MARGIN_LEFT, MARGIN_RIGHT, MARGIN_TOP, MARGIN_BOTTOM);
        return document;
    }
    
    /**
     * Add a RegattaDesk header to the PDF with required metadata
     */
    public static class RegattaDeskHeaderFooter extends PdfPageEventHelper {
        private final String regattaName;
        private final ZonedDateTime timestamp;
        private final Integer drawRevision;
        private final Integer resultsRevision;
        private final Locale locale;
        
        public RegattaDeskHeaderFooter(String regattaName, ZonedDateTime timestamp, 
                                      Integer drawRevision, Integer resultsRevision, Locale locale) {
            this.regattaName = regattaName;
            this.timestamp = timestamp;
            this.drawRevision = drawRevision;
            this.resultsRevision = resultsRevision;
            this.locale = locale != null ? locale : Locale.ENGLISH;
        }
        
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                PdfContentByte cb = writer.getDirectContent();
                
                // Header
                Font headerTitleFont = new Font(Font.HELVETICA, FONT_SIZE_TITLE, Font.BOLD);
                Font headerMetaFont = new Font(Font.HELVETICA, FONT_SIZE_META, Font.NORMAL);
                
                // Title on the left
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    new Phrase(regattaName, headerTitleFont),
                    document.left(), document.top() + 10, 0);
                
                // Metadata on the right
                float rightX = document.right();
                float topY = document.top() + 10;
                
                String timestampStr = DateTimeFormatters.formatTimestampDisplay(timestamp, locale);
                ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase("Generated: " + timestampStr, headerMetaFont),
                    rightX, topY, 0);
                
                if (drawRevision != null) {
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                        new Phrase("Draw Version: v" + drawRevision, headerMetaFont),
                        rightX, topY - 10, 0);
                }
                
                if (resultsRevision != null) {
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                        new Phrase("Results Version: v" + resultsRevision, headerMetaFont),
                        rightX, topY - 20, 0);
                }
                
                // Page number
                ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase("Page " + writer.getPageNumber(), headerMetaFont),
                    rightX, topY - 30, 0);
                
                // Header border line
                cb.setLineWidth(2f);
                cb.moveTo(document.left(), document.top());
                cb.lineTo(document.right(), document.top());
                cb.stroke();
                
                // Footer with RegattaDesk wordmark
                Font footerFont = new Font(Font.HELVETICA, FONT_SIZE_META, Font.BOLD);
                ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                    new Phrase("RegattaDesk", footerFont),
                    (document.left() + document.right()) / 2, 
                    document.bottom() - 15, 0);
                
            } catch (DocumentException e) {
                throw new RuntimeException("Error adding header/footer", e);
            }
        }
    }
    
    /**
     * Generate a PDF with sample regatta data
     * This is a basic implementation to demonstrate the structure
     */
    public static byte[] generateSamplePdf(String regattaName, Integer drawRevision,
                                          Integer resultsRevision, Locale locale,
                                          ZoneId regattaTimezone) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = createDocument();
        
        try {
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            
            // Add header/footer event handler
            ZoneId effectiveTimezone = regattaTimezone != null ? regattaTimezone : ZoneId.systemDefault();
            ZonedDateTime now = ZonedDateTime.now(effectiveTimezone);
            RegattaDeskHeaderFooter headerFooter = new RegattaDeskHeaderFooter(
                regattaName, now, drawRevision, resultsRevision, locale
            );
            writer.setPageEvent(headerFooter);
            
            document.open();
            
            // Add sample content
            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Font bodyFont = new Font(Font.HELVETICA, FONT_SIZE_BODY, Font.NORMAL);
            
            Paragraph title = new Paragraph("Sample Results", titleFont);
            title.setSpacingAfter(20);
            document.add(title);
            
            // Sample table
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            
            // Header row
            Font tableHeaderFont = new Font(Font.HELVETICA, FONT_SIZE_BODY, Font.BOLD);
            String[] headers = {"Rank", "Bib", "Crew", "Club", "Time"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, tableHeaderFont));
                cell.setGrayFill(0.9f); // Light gray background
                cell.setPadding(5);
                table.addCell(cell);
            }
            
            // Sample data rows
            Font tableDataFont = new Font(Font.HELVETICA, FONT_SIZE_BODY, Font.NORMAL);
            String[][] data = {
                {"1", "42", "Sample Crew A", "Rowing Club A", "5:23.456"},
                {"2", "17", "Sample Crew B", "Rowing Club B", "5:24.789"},
                {"3", "8", "Sample Crew C", "Rowing Club C", "5:25.123"}
            };
            
            for (String[] row : data) {
                for (String value : row) {
                    PdfPCell cell = new PdfPCell(new Phrase(value, tableDataFont));
                    cell.setPadding(4);
                    table.addCell(cell);
                }
            }
            
            document.add(table);
            
        } catch (DocumentException e) {
            throw new IOException("Error generating PDF", e);
        } finally {
            document.close();
        }
        
        return baos.toByteArray();
    }

    public static byte[] generateSamplePdf(String regattaName, Integer drawRevision,
                                          Integer resultsRevision, Locale locale) throws IOException {
        return generateSamplePdf(regattaName, drawRevision, resultsRevision, locale, null);
    }
}
