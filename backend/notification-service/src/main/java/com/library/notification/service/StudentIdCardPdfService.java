package com.library.notification.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.library.notification.dto.BookingConfirmedEvent;
import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

// Automatic, branded Target Zone Library ID card — built and sent on every
// booking-confirmed event (see NotificationService.sendStudentIdCard()).
// Deliberately independent of membership-service's IdCardService, which is a
// different, self-serve/on-demand, plain black-and-white card with a QR code
// — same purpose, different trigger and visual design, no shared code (this
// repo has no shared lib between services; see BookingConfirmedEvent itself,
// duplicated per-service for the same reason).
@Service
@Slf4j
public class StudentIdCardPdfService {

    // ── Colors — matches ReceiptPdfService's navy and the frontend's amber-500/600 ──
    private static final Color NAVY            = new Color(0x0d, 0x1b, 0x4b);
    private static final Color GOLD             = new Color(0xf5, 0x9e, 0x0b);
    private static final Color GOLD_DARK        = new Color(0xd9, 0x77, 0x06);
    private static final Color WHITE            = new Color(255, 255, 255);
    private static final Color BLACK            = new Color(0, 0, 0);
    private static final Color GRAY_LABEL       = new Color(0x44, 0x44, 0x44);
    private static final Color GRAY_PHOTO_BG    = new Color(0xe5, 0xe7, 0xeb);
    private static final Color GRAY_SILHOUETTE  = new Color(0x9c, 0xa3, 0xaf);
    private static final Color GRAY_BORDER      = new Color(0xd1, 0xd5, 0xdb);

    // ── Page (CR80-ish landscape card, PDF origin = bottom-left) ────────────────
    private static final float PAGE_W = 340f;
    private static final float PAGE_H = 214f;
    private static final float MARGIN = 10f;

    // Header zone (logo left, "Smart Work / Targeted Success" banner right)
    private static final float HEADER_Y = 158f;
    private static final float HEADER_H = 56f;
    private static final float LOGO_X   = MARGIN;
    private static final float BANNER_X = 172f;
    private static final float BANNER_W = PAGE_W - MARGIN - BANNER_X;

    // "STUDENT ID CARD" title bar — full width
    private static final float TITLE_BAR_Y = 140f;
    private static final float TITLE_BAR_H = 18f;

    // Field rows
    private static final float FIELD_X       = 14f;
    private static final float FIELD_START_Y = 128f;
    private static final float FIELD_GAP     = 15f;

    // Photo box
    private static final float PHOTO_X      = 258f;
    private static final float PHOTO_Y      = 72f;
    private static final float PHOTO_SIZE   = 62f;
    private static final float PHOTO_RADIUS = 6f;

    // Signature block, below the photo
    private static final float SIG_LINE_Y    = 50f;
    private static final float SIG_TEXT_Y    = 56f;
    private static final float SIG_CAPTION_Y = 42f;

    // Barcode
    private static final float BARCODE_X      = 14f;
    private static final float BARCODE_Y      = 26f;
    private static final float BARCODE_W      = 190f;
    private static final float BARCODE_H      = 24f;
    private static final float BARCODE_TEXT_Y = 15f;

    // Footer bar
    private static final float FOOTER_H      = 14f;
    private static final float FOOTER_GOLD_W = 90f;

    public byte[] buildIdCard(BookingConfirmedEvent event, byte[] photoBytes) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document  doc    = new Document(new Rectangle(PAGE_W, PAGE_H));
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            doc.setMargins(0, 0, 0, 0);
            doc.open();

            PdfContentByte cb = writer.getDirectContent();

            BaseFont bf       = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, false);
            BaseFont bfBold   = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, false);
            BaseFont bfItalic = BaseFont.createFont(BaseFont.HELVETICA_OBLIQUE, BaseFont.CP1252, false);

            String idNumber = buildIdNumber(event.getMembershipId());
            String validUpto = formatDateDdMmmYyyy(event.getEndDate());

            drawBackground(cb);
            drawLogo(cb, bfBold, bf);
            drawTopRightBanner(cb, bf, bfBold);
            drawTitleBar(cb, bfBold);
            drawFields(cb, bf, bfBold, event, idNumber, validUpto);
            drawPhoto(cb, photoBytes);
            drawSignature(cb, bfItalic, bf);
            drawBarcode(cb, bf, idNumber);
            drawFooter(cb, bf, bfBold);
            drawBorder(cb);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Student ID card PDF generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate student ID card PDF.", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drawing
    // ─────────────────────────────────────────────────────────────────────────

    private void drawBackground(PdfContentByte cb) {
        cb.setColorFill(WHITE);
        cb.rectangle(0, 0, PAGE_W, PAGE_H);
        cb.fill();
    }

    private void drawBorder(PdfContentByte cb) {
        cb.setColorStroke(GRAY_BORDER);
        cb.setLineWidth(0.75f);
        cb.rectangle(0.5f, 0.5f, PAGE_W - 1f, PAGE_H - 1f);
        cb.stroke();
    }

    // "TARGET Z[bullseye]NE" wordmark + "LIBRARY" below
    private void drawLogo(PdfContentByte cb, BaseFont bfBold, BaseFont bf) throws Exception {
        float size = 13f;
        float y    = 200f;

        cb.setColorFill(NAVY);
        cb.beginText();
        cb.setFontAndSize(bfBold, size);
        cb.setTextMatrix(LOGO_X, y);
        cb.showText("TARGET ");
        cb.endText();
        float x = LOGO_X + bfBold.getWidthPoint("TARGET ", size);

        cb.setColorFill(GOLD);
        cb.beginText();
        cb.setFontAndSize(bfBold, size);
        cb.setTextMatrix(x, y);
        cb.showText("Z");
        cb.endText();
        x += bfBold.getWidthPoint("Z", size);

        // Bullseye-with-arrow mark standing in for the "O"
        float cx = x + 7f;
        float cy = y + 4.5f;
        cb.setColorFill(NAVY);
        cb.circle(cx, cy, 6f);
        cb.fill();
        cb.setColorFill(GOLD);
        cb.circle(cx, cy, 4f);
        cb.fill();
        cb.setColorFill(NAVY);
        cb.circle(cx, cy, 2f);
        cb.fill();

        cb.setColorStroke(NAVY);
        cb.setLineWidth(1.2f);
        cb.moveTo(cx - 7f, cy - 7f);
        cb.lineTo(cx + 8f, cy + 8f);
        cb.stroke();
        // Arrowhead
        cb.setColorFill(NAVY);
        cb.moveTo(cx + 8f, cy + 8f);
        cb.lineTo(cx + 4f, cy + 7.5f);
        cb.lineTo(cx + 7.5f, cy + 4f);
        cb.closePath();
        cb.fill();

        x = cx + 9f;
        cb.setColorFill(GOLD);
        cb.beginText();
        cb.setFontAndSize(bfBold, size);
        cb.setTextMatrix(x, y);
        cb.showText("NE");
        cb.endText();
        float wordmarkEndX = x + bfBold.getWidthPoint("NE", size);

        cb.setColorStroke(NAVY);
        cb.setLineWidth(1f);
        cb.moveTo(LOGO_X, 192f);
        cb.lineTo(wordmarkEndX, 192f);
        cb.stroke();

        cb.setColorFill(NAVY);
        cb.setCharacterSpacing(2.5f);
        cb.beginText();
        cb.setFontAndSize(bfBold, 8f);
        cb.setTextMatrix(LOGO_X, 178f);
        cb.showText("LIBRARY");
        cb.endText();
        cb.setCharacterSpacing(0f);
    }

    private void drawTopRightBanner(PdfContentByte cb, BaseFont bf, BaseFont bfBold) throws Exception {
        cb.setColorFill(NAVY);
        cb.rectangle(BANNER_X, HEADER_Y, BANNER_W, HEADER_H);
        cb.fill();

        drawDiagonalStripe(cb, BANNER_X, HEADER_Y, BANNER_W, HEADER_H, 14f);

        cb.setColorFill(WHITE);
        cb.beginText();
        cb.setFontAndSize(bfBold, 9f);
        cb.setTextMatrix(BANNER_X + 12f, 200f);
        cb.showText("Smart Work");
        cb.endText();

        cb.setColorFill(WHITE);
        cb.beginText();
        cb.setFontAndSize(bfBold, 9f);
        cb.setTextMatrix(BANNER_X + 12f, 186f);
        cb.showText("Targeted ");
        cb.endText();
        float x2 = BANNER_X + 12f + bfBold.getWidthPoint("Targeted ", 9f);
        cb.setColorFill(GOLD);
        cb.beginText();
        cb.setFontAndSize(bfBold, 9f);
        cb.setTextMatrix(x2, 186f);
        cb.showText("Success");
        cb.endText();

        cb.setColorStroke(GOLD);
        cb.setLineWidth(1f);
        cb.moveTo(BANNER_X + 12f, 181f);
        cb.lineTo(PAGE_W - MARGIN - 12f, 181f);
        cb.stroke();
    }

    private void drawTitleBar(PdfContentByte cb, BaseFont bfBold) throws Exception {
        cb.setColorFill(NAVY);
        cb.rectangle(0, TITLE_BAR_Y, PAGE_W, TITLE_BAR_H);
        cb.fill();

        drawDiagonalStripe(cb, 0, TITLE_BAR_Y, 60f, TITLE_BAR_H, 10f);

        String title = "STUDENT ID CARD";
        float  size  = 10.5f;
        float  textW = bfBold.getWidthPoint(title, size);
        float  textX = (PAGE_W - textW) / 2f;
        float  textY = TITLE_BAR_Y + (TITLE_BAR_H - size) / 2f + 2f;

        cb.setColorFill(WHITE);
        cb.beginText();
        cb.setFontAndSize(bfBold, size);
        cb.setTextMatrix(textX, textY);
        cb.showText(title);
        cb.endText();
    }

    // A gold parallelogram slanting across the given rect, clipped so it never
    // spills outside it — the "diagonal stripe" accent seen throughout the
    // reference design's navy banners.
    private void drawDiagonalStripe(PdfContentByte cb, float x, float y, float w, float h, float stripeW) {
        cb.saveState();
        cb.rectangle(x, y, w, h);
        cb.clip();
        cb.newPath();

        float slant = h * 0.6f;
        cb.setColorFill(GOLD);
        cb.moveTo(x + w - stripeW - slant, y);
        cb.lineTo(x + w - slant, y);
        cb.lineTo(x + w, y + h);
        cb.lineTo(x + w - stripeW, y + h);
        cb.closePath();
        cb.fill();

        cb.restoreState();
    }

    private void drawFields(PdfContentByte cb, BaseFont bf, BaseFont bfBold,
                            BookingConfirmedEvent event, String idNumber, String validUpto) throws Exception {
        String[][] rows = {
            { "Name",       orDash(event.getUserName()) },
            { "Mobile No.", orDash(event.getUserMobile()) },
            { "ID No.",     idNumber },
            { "Plan",       orDash(event.getPlanName()) },
            { "Valid Upto", validUpto },
        };

        for (int i = 0; i < rows.length; i++) {
            float y = FIELD_START_Y - i * FIELD_GAP;
            drawFieldRow(cb, bf, bfBold, rows[i][0], rows[i][1], y);
        }
    }

    private void drawFieldRow(PdfContentByte cb, BaseFont bf, BaseFont bfBold,
                              String label, String value, float y) throws Exception {
        float size = 8f;

        cb.setColorFill(NAVY);
        cb.beginText();
        cb.setFontAndSize(bfBold, size);
        cb.setTextMatrix(FIELD_X, y);
        cb.showText(label);
        cb.endText();

        float labelColX = FIELD_X + 62f;
        cb.setColorFill(GRAY_LABEL);
        cb.beginText();
        cb.setFontAndSize(bf, size);
        cb.setTextMatrix(labelColX, y);
        cb.showText(":");
        cb.endText();

        cb.setColorFill(BLACK);
        cb.beginText();
        cb.setFontAndSize(bfBold, size);
        cb.setTextMatrix(labelColX + 10f, y);
        cb.showText(value);
        cb.endText();
    }

    private void drawPhoto(PdfContentByte cb, byte[] photoBytes) throws Exception {
        Image photo = null;
        if (photoBytes != null) {
            try {
                photo = Image.getInstance(photoBytes);
            } catch (Exception e) {
                log.warn("Could not decode student photo for ID card — falling back to silhouette: {}", e.getMessage());
            }
        }

        if (photo != null) {
            cb.saveState();
            cb.roundRectangle(PHOTO_X, PHOTO_Y, PHOTO_SIZE, PHOTO_SIZE, PHOTO_RADIUS);
            cb.clip();
            cb.newPath();

            photo.scaleToFit(PHOTO_SIZE, PHOTO_SIZE);
            float scaledW = photo.getScaledWidth();
            float scaledH = photo.getScaledHeight();
            photo.setAbsolutePosition(
                    PHOTO_X + (PHOTO_SIZE - scaledW) / 2f,
                    PHOTO_Y + (PHOTO_SIZE - scaledH) / 2f);
            cb.addImage(photo);

            cb.restoreState();
        } else {
            drawSilhouette(cb);
        }

        cb.setColorStroke(NAVY);
        cb.setLineWidth(1f);
        cb.roundRectangle(PHOTO_X, PHOTO_Y, PHOTO_SIZE, PHOTO_SIZE, PHOTO_RADIUS);
        cb.stroke();
    }

    private void drawSilhouette(PdfContentByte cb) {
        cb.setColorFill(GRAY_PHOTO_BG);
        cb.roundRectangle(PHOTO_X, PHOTO_Y, PHOTO_SIZE, PHOTO_SIZE, PHOTO_RADIUS);
        cb.fill();

        float cx = PHOTO_X + PHOTO_SIZE / 2f;

        float headR  = PHOTO_SIZE * 0.16f;
        float headCy = PHOTO_Y + PHOTO_SIZE * 0.66f;
        cb.setColorFill(GRAY_SILHOUETTE);
        cb.circle(cx, headCy, headR);
        cb.fill();

        // Shoulders — a wide ellipse whose center sits below the box, clipped to
        // the rounded photo box so only its upper arc shows.
        cb.saveState();
        cb.roundRectangle(PHOTO_X, PHOTO_Y, PHOTO_SIZE, PHOTO_SIZE, PHOTO_RADIUS);
        cb.clip();
        cb.newPath();
        cb.setColorFill(GRAY_SILHOUETTE);
        cb.ellipse(PHOTO_X + PHOTO_SIZE * 0.08f, PHOTO_Y - PHOTO_SIZE * 0.30f,
                   PHOTO_X + PHOTO_SIZE * 0.92f, PHOTO_Y + PHOTO_SIZE * 0.34f);
        cb.fill();
        cb.restoreState();
    }

    private void drawSignature(PdfContentByte cb, BaseFont bfItalic, BaseFont bf) throws Exception {
        String sig  = "Malooki";
        float  size = 15f;
        float  sigW = bfItalic.getWidthPoint(sig, size);
        float  cx   = PHOTO_X + PHOTO_SIZE / 2f;

        cb.setColorFill(NAVY);
        cb.beginText();
        cb.setFontAndSize(bfItalic, size);
        cb.setTextMatrix(cx - sigW / 2f, SIG_TEXT_Y);
        cb.showText(sig);
        cb.endText();

        cb.setColorStroke(GRAY_LABEL);
        cb.setLineWidth(0.5f);
        cb.moveTo(PHOTO_X, SIG_LINE_Y);
        cb.lineTo(PHOTO_X + PHOTO_SIZE, SIG_LINE_Y);
        cb.stroke();

        String caption  = "Authorised Signatory";
        float  capSize  = 5.5f;
        float  capW     = bf.getWidthPoint(caption, capSize);
        cb.setColorFill(GRAY_LABEL);
        cb.beginText();
        cb.setFontAndSize(bf, capSize);
        cb.setTextMatrix(cx - capW / 2f, SIG_CAPTION_Y);
        cb.showText(caption);
        cb.endText();
    }

    private void drawBarcode(PdfContentByte cb, BaseFont bf, String idNumber) throws Exception {
        // Encode at higher pixel density than the final point size — same
        // "encode big, scale down" pattern IdCardService.drawQr already uses for
        // its QR code — so the barcode stays crisp after scaling into the box.
        BitMatrix matrix = new Code128Writer().encode(idNumber, BarcodeFormat.CODE_128, 800, 160);
        BufferedImage img = MatrixToImageWriter.toBufferedImage(matrix);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);

        Image barcode = Image.getInstance(baos.toByteArray());
        barcode.scaleAbsolute(BARCODE_W, BARCODE_H);
        barcode.setAbsolutePosition(BARCODE_X, BARCODE_Y);
        cb.addImage(barcode);

        String spaced = String.join(" ", idNumber.split(""));
        float  size   = 7f;
        float  textW  = bf.getWidthPoint(spaced, size);
        float  textX  = BARCODE_X + (BARCODE_W - textW) / 2f;

        cb.setColorFill(BLACK);
        cb.setCharacterSpacing(0.5f);
        cb.beginText();
        cb.setFontAndSize(bf, size);
        cb.setTextMatrix(textX, BARCODE_TEXT_Y);
        cb.showText(spaced);
        cb.endText();
        cb.setCharacterSpacing(0f);
    }

    private void drawFooter(PdfContentByte cb, BaseFont bf, BaseFont bfBold) throws Exception {
        cb.setColorFill(GOLD);
        cb.rectangle(0, 0, FOOTER_GOLD_W, FOOTER_H);
        cb.fill();

        // Hand-drawn globe icon: outer circle + a narrow inscribed ellipse
        // (meridian) + a horizontal line (equator).
        float globeCx = 12f;
        float globeCy = FOOTER_H / 2f;
        float globeR  = 4f;
        cb.setColorStroke(NAVY);
        cb.setLineWidth(0.6f);
        cb.ellipse(globeCx - globeR, globeCy - globeR, globeCx + globeR, globeCy + globeR);
        cb.stroke();
        cb.ellipse(globeCx - globeR * 0.45f, globeCy - globeR, globeCx + globeR * 0.45f, globeCy + globeR);
        cb.stroke();
        cb.moveTo(globeCx - globeR, globeCy);
        cb.lineTo(globeCx + globeR, globeCy);
        cb.stroke();

        cb.setColorFill(NAVY);
        cb.beginText();
        cb.setFontAndSize(bfBold, 6f);
        cb.setTextMatrix(globeCx + globeR + 4f, globeCy - 2f);
        cb.showText("targetzone.co.in");
        cb.endText();

        cb.setColorFill(NAVY);
        cb.rectangle(FOOTER_GOLD_W, 0, PAGE_W - FOOTER_GOLD_W, FOOTER_H);
        cb.fill();

        String part1 = "FOCUSED ENVIRONMENT. ";
        String part2 = "PROVEN RESULTS.";
        float  size  = 6f;
        float  w1    = bfBold.getWidthPoint(part1, size);
        float  w2    = bfBold.getWidthPoint(part2, size);
        float  startX = PAGE_W - MARGIN - w1 - w2;
        float  textY  = FOOTER_H / 2f - 2f;

        cb.setColorFill(WHITE);
        cb.beginText();
        cb.setFontAndSize(bfBold, size);
        cb.setTextMatrix(startX, textY);
        cb.showText(part1);
        cb.endText();

        cb.setColorFill(GOLD);
        cb.beginText();
        cb.setFontAndSize(bfBold, size);
        cb.setTextMatrix(startX + w1, textY);
        cb.showText(part2);
        cb.endText();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    // Same convention membership-service's IdCardService.buildQrData() already
    // uses for its member ID — first 8 chars of the membership UUID, uppercased.
    // Kept in lock-step deliberately (see the plan's decision #2) rather than
    // introducing a second ID scheme.
    private String buildIdNumber(String membershipId) {
        if (membershipId == null || membershipId.isBlank()) return "UNKNOWN";
        return membershipId.length() >= 8
                ? membershipId.substring(0, 8).toUpperCase()
                : membershipId.toUpperCase();
    }

    private String formatDateDdMmmYyyy(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) return "—";
        try {
            LocalDate date = LocalDate.parse(isoDate);
            return date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        } catch (DateTimeParseException e) {
            return isoDate;
        }
    }

    private String orDash(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }
}
