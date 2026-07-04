package com.library.common.idcard;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

// The single branded Target Zone student ID card design used for every card
// generated across the app. Static artwork (header, title bar, footer, photo
// placeholder, signature) is composited from designer-provided PNG assets —
// see src/main/resources/idcard-assets/ — rather than hand-drawn with vector
// primitives. Only the fields (name/mobile/ID/plan/valid-upto) and the
// per-student barcode are actually dynamic, so those are drawn in code.
public class IdCardPdfGenerator {

    private static final Logger log = LoggerFactory.getLogger(IdCardPdfGenerator.class);

    private static final Color NAVY       = new Color(0x0d, 0x1b, 0x4b);
    private static final Color WHITE      = new Color(255, 255, 255);
    private static final Color BLACK      = new Color(0, 0, 0);
    private static final Color GRAY_LABEL = new Color(0x44, 0x44, 0x44);
    private static final Color GRAY_BORDER = new Color(0xd1, 0xd5, 0xdb);

    // Page matches the reference design's 1536x1024 canvas at a 3:2 ratio.
    private static final float PAGE_W = 342f;
    private static final float PAGE_H = 228f;
    private static final float SCALE  = PAGE_W / 1536f;

    // Static artwork placement — derived from the reference canvas's pixel
    // layout (header 1536x243, title bar 915x106, fields 949x535, footer
    // 1536x97, photo/signature column 401 wide starting at x=1035).
    private static final float HEADER_W = 1536f * SCALE;
    private static final float HEADER_H = 243f * SCALE;
    private static final float HEADER_Y = PAGE_H - HEADER_H;

    private static final float TITLEBAR_W = 915f * SCALE;
    private static final float TITLEBAR_H = 106f * SCALE;
    private static final float TITLEBAR_Y = HEADER_Y - TITLEBAR_H;

    private static final float FOOTER_W = 1536f * SCALE;
    private static final float FOOTER_H = 97f * SCALE;

    private static final float PHOTO_X = 1035f * SCALE;
    private static final float PHOTO_W = 401f * SCALE;
    private static final float PHOTO_H = 447f * SCALE;
    private static final float PHOTO_Y = PAGE_H - 277f * SCALE - PHOTO_H;

    private static final float SIG_X = PHOTO_X;
    private static final float SIG_W = 401f * SCALE;
    private static final float SIG_H = 150f * SCALE;
    private static final float SIG_Y = PAGE_H - 735f * SCALE - SIG_H;

    // Fields column (dynamic) — positions derived from the reference design.
    private static final float FIELDS_TOP_Y = TITLEBAR_Y;
    private static final float FIELD_X       = 100f * SCALE;
    private static final float COLON_X       = 370f * SCALE;
    private static final float VALUE_X       = 425f * SCALE;
    private static final float FIELD_ROW0_Y  = FIELDS_TOP_Y - 74f * SCALE;
    private static final float FIELD_GAP     = 72f * SCALE;

    private static final float BARCODE_X      = FIELD_X;
    private static final float BARCODE_H      = 16f;
    private static final float BARCODE_Y      = 48f;
    private static final float BARCODE_W      = 180f;
    private static final float BARCODE_TEXT_Y = BARCODE_Y - 9f;

    private static final byte[] HEADER_PNG    = loadAsset("header.png");
    private static final byte[] TITLEBAR_PNG  = loadAsset("titlebar.png");
    private static final byte[] FOOTER_PNG    = loadAsset("footer.png");
    private static final byte[] PHOTO_PLACEHOLDER_PNG = loadAsset("photo-placeholder.png");
    private static final byte[] SIGNATURE_PNG = loadAsset("signature.png");

    private static byte[] loadAsset(String name) {
        try (InputStream in = IdCardPdfGenerator.class.getClassLoader()
                .getResourceAsStream("idcard-assets/" + name)) {
            if (in == null) {
                throw new IllegalStateException("idcard-assets/" + name + " not found on classpath");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] generate(IdCardData data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document  doc    = new Document(new Rectangle(PAGE_W, PAGE_H));
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            doc.setMargins(0, 0, 0, 0);
            doc.open();

            PdfContentByte cb = writer.getDirectContent();

            BaseFont bf     = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, false);
            BaseFont bfBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, false);

            String idNumber  = (data.memberId() == null || data.memberId().isBlank())
                    ? "UNKNOWN" : data.memberId();
            String validUpto = orDash(data.validTillDisplay());

            drawBackground(cb);
            placeImage(cb, HEADER_PNG, 0, HEADER_Y, HEADER_W, HEADER_H);
            placeImage(cb, TITLEBAR_PNG, 0, TITLEBAR_Y, TITLEBAR_W, TITLEBAR_H);
            placeImage(cb, FOOTER_PNG, 0, 0, FOOTER_W, FOOTER_H);
            drawFields(cb, bf, bfBold, data, idNumber, validUpto);
            drawPhoto(cb, data.photoBytes());
            placeImage(cb, SIGNATURE_PNG, SIG_X, SIG_Y, SIG_W, SIG_H);
            drawBarcode(cb, bf, idNumber);
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

    private void placeImage(PdfContentByte cb, byte[] pngBytes, float x, float y, float w, float h) throws Exception {
        Image img = Image.getInstance(pngBytes);
        img.scaleAbsolute(w, h);
        img.setAbsolutePosition(x, y);
        cb.addImage(img);
    }

    private void drawFields(PdfContentByte cb, BaseFont bf, BaseFont bfBold,
                            IdCardData data, String idNumber, String validUpto) throws Exception {
        String[][] rows = {
            { "Name",       orDash(data.name()) },
            { "Mobile No.", orDash(data.mobile()) },
            { "ID No.",     idNumber },
            { "Plan",       orDash(data.planName()) },
            { "Valid Upto", validUpto },
        };

        for (int i = 0; i < rows.length; i++) {
            float y = FIELD_ROW0_Y - i * FIELD_GAP;
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

        cb.setColorFill(GRAY_LABEL);
        cb.beginText();
        cb.setFontAndSize(bf, size);
        cb.setTextMatrix(COLON_X, y);
        cb.showText(":");
        cb.endText();

        cb.setColorFill(BLACK);
        cb.beginText();
        cb.setFontAndSize(bfBold, size);
        cb.setTextMatrix(VALUE_X, y);
        cb.showText(value);
        cb.endText();
    }

    private void drawPhoto(PdfContentByte cb, byte[] photoBytes) throws Exception {
        Image photo = null;
        if (photoBytes != null) {
            try {
                photo = Image.getInstance(photoBytes);
            } catch (Exception e) {
                log.warn("Could not decode student photo for ID card — falling back to placeholder: {}", e.getMessage());
            }
        }

        if (photo == null) {
            placeImage(cb, PHOTO_PLACEHOLDER_PNG, PHOTO_X, PHOTO_Y, PHOTO_W, PHOTO_H);
            return;
        }

        float radius = 6f;
        cb.saveState();
        cb.roundRectangle(PHOTO_X, PHOTO_Y, PHOTO_W, PHOTO_H, radius);
        cb.clip();
        cb.newPath();

        photo.scaleToFit(PHOTO_W, PHOTO_H);
        float scaledW = photo.getScaledWidth();
        float scaledH = photo.getScaledHeight();
        photo.setAbsolutePosition(
                PHOTO_X + (PHOTO_W - scaledW) / 2f,
                PHOTO_Y + (PHOTO_H - scaledH) / 2f);
        cb.addImage(photo);

        cb.restoreState();

        cb.setColorStroke(NAVY);
        cb.setLineWidth(1f);
        cb.roundRectangle(PHOTO_X, PHOTO_Y, PHOTO_W, PHOTO_H, radius);
        cb.stroke();
    }

    private void drawBarcode(PdfContentByte cb, BaseFont bf, String idNumber) throws Exception {
        // Encode at higher pixel density than the final point size so the
        // barcode stays crisp after scaling into the box.
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

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String orDash(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }
}
