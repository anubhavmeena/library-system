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
import java.math.BigDecimal;

@Slf4j
@Service
public class ReceiptPdfService {

    private static final Color HEADER_BG = new Color(0x0d, 0x1b, 0x4b);
    private static final Font TITLE_FONT  = new Font(Font.HELVETICA, 18, Font.BOLD, Color.WHITE);
    private static final Font LABEL_FONT  = new Font(Font.HELVETICA, 10, Font.BOLD, Color.DARK_GRAY);
    private static final Font VALUE_FONT  = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font FOOTER_FONT = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);

    public byte[] buildReceipt(PaymentReceiptEvent event) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A5, 30, 30, 20, 30);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Paragraph header = new Paragraph("TARGET ZONE LIBRARY\nPayment Receipt", TITLE_FONT);
            header.setAlignment(Element.ALIGN_CENTER);
            PdfPTable headerTable = new PdfPTable(1);
            headerTable.setWidthPercentage(100);
            PdfPCell headerCell = new PdfPCell(header);
            headerCell.setBackgroundColor(HEADER_BG);
            headerCell.setPadding(14);
            headerCell.setBorder(Rectangle.NO_BORDER);
            headerTable.addCell(headerCell);
            doc.add(headerTable);
            doc.add(new Paragraph(" "));

            addRow(doc, "Invoice No.", nvl(event.getInvoiceId()));
            addRow(doc, "Date", nvl(event.getPaymentDate()));
            addRow(doc, "Student Name", nvl(event.getUserName()));
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

    private void addRow(Document doc, String label, String value) throws DocumentException {
        PdfPTable row = new PdfPTable(2);
        row.setWidthPercentage(100);
        row.setWidths(new float[]{1f, 2f});
        row.addCell(borderless(label, LABEL_FONT));
        row.addCell(borderless(value, VALUE_FONT));
        doc.add(row);
    }

    private void addAmountRow(PdfPTable table, String label, BigDecimal amount) {
        // "Rs." not the rupee glyph — the default Helvetica/WinAnsi font used here
        // (same as IdCardService's formatPaid()) can't render the ₹ symbol.
        String display = "Rs. " + (amount != null ? amount.stripTrailingZeros().toPlainString() : "0");
        table.addCell(borderless(label, LABEL_FONT));
        PdfPCell valueCell = borderless(display, VALUE_FONT);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private PdfPCell borderless(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(4);
        return cell;
    }

    private String nvl(String s) {
        return hasValue(s) ? s : "—";
    }

    private boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }
}
