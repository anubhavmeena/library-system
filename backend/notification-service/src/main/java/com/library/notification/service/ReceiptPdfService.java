package com.library.notification.service;

import com.library.notification.dto.PaymentReceiptEvent;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;

@Slf4j
@Service
public class ReceiptPdfService {

    private static final Color HEADER_BG   = new Color(0x0d, 0x1b, 0x4b);
    private static final Color BORDER_GRAY = new Color(0xd0, 0xd0, 0xd0);
    private static final Font TITLE_FONT  = new Font(Font.HELVETICA, 16, Font.BOLD, Color.WHITE);
    private static final Font LABEL_FONT  = new Font(Font.HELVETICA, 10, Font.BOLD, Color.DARK_GRAY);
    private static final Font VALUE_FONT  = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font FOOTER_FONT = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);

    private static final float LOGO_SIZE = 32f;

    // Loaded once at class-init time — immutable byte[], safe to reuse across
    // requests (a fresh Image wrapper is built per-PDF in buildReceipt()).
    private static final byte[] LOGO_BYTES = loadLogoBytes();

    private static byte[] loadLogoBytes() {
        try (InputStream in = ReceiptPdfService.class.getResourceAsStream("/tz-logo.png")) {
            return in != null ? in.readAllBytes() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public byte[] buildReceipt(PaymentReceiptEvent event) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A5, 30, 30, 20, 30);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            doc.add(buildHeader());
            doc.add(new Paragraph(" "));

            addRow(doc, "Invoice No.", nvl(event.getInvoiceId()));
            addRow(doc, "Date", nvl(event.getPaymentDate()));
            addRow(doc, "Student Name", nvl(event.getUserName()));
            addRow(doc, "Phone", nvl(event.getUserMobile()));
            if (hasValue(event.getPlanName()))   addRow(doc, "Plan", event.getPlanName());
            if (hasValue(event.getSeatNumber())) addRow(doc, "Seat", event.getSeatNumber());
            addRow(doc, "Payment Method", nvl(event.getPaymentMethod()));

            doc.add(new Paragraph(" "));

            PdfPTable amountTable = new PdfPTable(2);
            amountTable.setWidthPercentage(100);
            amountTable.setWidths(new float[]{1.5f, 1f});

            addAmountRow(amountTable, "Amount Paid", event.getAmountPaid());
            addAmountRow(amountTable, "Amount Pending", event.getAmountPending());
            doc.add(amountTable);

            doc.add(new Paragraph(" "));
            Paragraph footer = new Paragraph(
                    "This is a system-generated receipt and does not require a signature.\n" +
                            "Target Zone Library — https://targetzone.co.in",
                    FOOTER_FONT);
            doc.add(footer);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Receipt PDF generation failed for invoice {}: {}", event.getInvoiceId(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate payment receipt PDF.");
        }
    }

    private PdfPTable buildHeader() throws Exception {
        Image logo = (LOGO_BYTES != null) ? Image.getInstance(LOGO_BYTES) : null;

        PdfPTable headerTable = new PdfPTable(logo != null ? 2 : 1);
        headerTable.setWidthPercentage(100);
        if (logo != null) headerTable.setWidths(new float[]{1f, 6f});

        if (logo != null) {
            logo.scaleToFit(LOGO_SIZE, LOGO_SIZE);
            PdfPCell logoCell = new PdfPCell(logo, false);
            logoCell.setBackgroundColor(HEADER_BG);
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.setPadding(10);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            headerTable.addCell(logoCell);
        }

        Paragraph title = new Paragraph("TARGET ZONE LIBRARY\nPayment Receipt", TITLE_FONT);
        title.setAlignment(logo != null ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
        PdfPCell titleCell = new PdfPCell(title);
        titleCell.setBackgroundColor(HEADER_BG);
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setPadding(14);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        headerTable.addCell(titleCell);

        return headerTable;
    }

    private void addRow(Document doc, String label, String value) throws DocumentException {
        PdfPTable row = new PdfPTable(2);
        row.setWidthPercentage(100);
        row.setWidths(new float[]{1f, 2f});
        row.addCell(bordered(label, LABEL_FONT));
        row.addCell(bordered(value, VALUE_FONT));
        doc.add(row);
    }

    private void addAmountRow(PdfPTable table, String label, BigDecimal amount) {
        // "Rs." not the rupee glyph — the default Helvetica/WinAnsi font used here
        // (same as IdCardService's formatPaid()) can't render the ₹ symbol.
        String display = "Rs. " + (amount != null ? amount.stripTrailingZeros().toPlainString() : "0");
        table.addCell(bordered(label, LABEL_FONT));
        PdfPCell valueCell = bordered(display, VALUE_FONT);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private PdfPCell bordered(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER_GRAY);
        cell.setBorderWidth(0.5f);
        cell.setPadding(5);
        return cell;
    }

    private String nvl(String s) {
        return hasValue(s) ? s : "—";
    }

    private boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }
}
