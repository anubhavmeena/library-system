package com.library.membership.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.library.membership.dto.MembershipDto;
import com.library.membership.dto.UserApiResponse;
import com.library.membership.dto.UserProfileDto;
import com.library.membership.exception.ResourceNotFoundException;
import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdCardService {

    private final MembershipService membershipService;
    private final RestTemplate      restTemplate;

    @Value("${app.user-service.base-url}")
    private String userServiceBaseUrl;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public byte[] generateIdCard(String userId) {
        MembershipDto membership = membershipService.getUserActiveMembership(userId);
        if (membership == null) {
            throw new ResourceNotFoundException(
                    "No active membership found. Purchase a plan to download your ID card.");
        }
        UserProfileDto user       = fetchUserProfile(userId);
        byte[]         photoBytes = fetchPhotoBytes(user.getPhotoUrl());
        return buildPdf(user, membership, photoBytes);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cross-service HTTP calls
    // ─────────────────────────────────────────────────────────────────────────

    private UserProfileDto fetchUserProfile(String userId) {
        String url = userServiceBaseUrl + "/api/users/" + userId;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id",   userId);
            headers.set("X-User-Role", "STUDENT");
            HttpEntity<Void> req = new HttpEntity<>(headers);

            ResponseEntity<UserApiResponse> resp = restTemplate.exchange(
                    url, HttpMethod.GET, req, UserApiResponse.class);

            if (resp.getBody() != null && resp.getBody().getData() != null) {
                return resp.getBody().getData();
            }
            throw new ResourceNotFoundException("User profile not found for ID: " + userId);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch user profile for userId={}: {}", userId, e.getMessage());
            throw new RuntimeException("Unable to retrieve user profile. Please try again.");
        }
    }

    private byte[] fetchPhotoBytes(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) return null;
        try {
            ResponseEntity<byte[]> resp = restTemplate.getForEntity(
                    userServiceBaseUrl + photoUrl, byte[].class);
            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                return resp.getBody();
            }
        } catch (Exception e) {
            log.warn("Could not fetch photo from {}: {} — proceeding without photo",
                    photoUrl, e.getMessage());
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PDF generation — idgenerator.py layout
    // ─────────────────────────────────────────────────────────────────────────

    // Colors
    private static final Color BLACK      = new Color(0, 0, 0);
    private static final Color WHITE      = new Color(255, 255, 255);
    private static final Color GRAY_LABEL = new Color(0x44, 0x44, 0x44);
    private static final Color GRAY_PHOTO = new Color(0x9C, 0xA3, 0xAF);

    // Layout (300 × 200 pt, PDF origin = bottom-left)
    private static final float PAGE_W    = 300f;
    private static final float PAGE_H    = 200f;
    private static final float MARGIN    = 4f;
    private static final float HEADER_H  = 28f;
    private static final float HEADER_Y  = PAGE_H - MARGIN - HEADER_H;  // 168

    private static final float FIELD_X       = 10f;
    private static final float FIELD_START_Y = HEADER_Y - 14f;  // 154
    private static final float FIELD_GAP     = 18f;

    private static final float PHOTO_SIZE = 65f;
    private static final float QR_SIZE    = 65f;
    private static final float RIGHT_X    = PAGE_W - MARGIN - PHOTO_SIZE;  // 231
    private static final float PHOTO_Y    = HEADER_Y - 5f - PHOTO_SIZE;    // 98
    private static final float QR_Y       = PHOTO_Y - 4f - QR_SIZE;        // 29

    private byte[] buildPdf(UserProfileDto user, MembershipDto m, byte[] photoBytes) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document  doc    = new Document(new Rectangle(PAGE_W, PAGE_H));
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            doc.setMargins(0, 0, 0, 0);
            doc.open();

            PdfContentByte cb = writer.getDirectContent();

            BaseFont bf     = BaseFont.createFont(BaseFont.HELVETICA,      BaseFont.CP1252, false);
            BaseFont bfBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, false);

            drawBackground(cb);
            drawBorder(cb);
            drawHeader(cb, bfBold);
            drawFields(cb, bf, bfBold, user, m);
            drawPhoto(cb, bf, photoBytes);
            drawQr(cb, buildQrData(user, m));
            drawFooter(cb, bf);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("PDF generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate ID card PDF. Please try again.");
        }
    }

    private void drawBackground(PdfContentByte cb) {
        cb.setColorFill(WHITE);
        cb.rectangle(0, 0, PAGE_W, PAGE_H);
        cb.fill();
    }

    private void drawBorder(PdfContentByte cb) {
        cb.setColorStroke(BLACK);
        cb.setLineWidth(1.5f);
        cb.rectangle(MARGIN, MARGIN, PAGE_W - 2 * MARGIN, PAGE_H - 2 * MARGIN);
        cb.stroke();
    }

    private void drawHeader(PdfContentByte cb, BaseFont bfBold) throws Exception {
        cb.setColorFill(BLACK);
        cb.rectangle(MARGIN, HEADER_Y, PAGE_W - 2 * MARGIN, HEADER_H);
        cb.fill();

        String title   = "TARGET ZONE LIBRARY";
        float  fontSize = 12f;
        float  textW   = bfBold.getWidthPoint(title, fontSize);
        float  textX   = (PAGE_W - textW) / 2f;
        float  textY   = HEADER_Y + (HEADER_H - fontSize) / 2f + 2f;

        cb.setColorFill(WHITE);
        cb.beginText();
        cb.setFontAndSize(bfBold, fontSize);
        cb.setTextMatrix(textX, textY);
        cb.showText(title);
        cb.endText();
    }

    private void drawFields(PdfContentByte cb, BaseFont bf, BaseFont bfBold,
                            UserProfileDto user, MembershipDto m) throws Exception {
        String[][] rows = {
            { "Name",          orDash(user.getName()) },
            { "Father's Name", orDash(user.getFatherName()) },
            { "Age",           calcAge(user.getDateOfBirth()) },
            { "Shift",         formatShift(m.getShift()) },
            { "Phone",         orDash(user.getMobile()) },
            { "Paid",          formatPaid(m.getPlanPrice()) },
        };

        for (int i = 0; i < rows.length; i++) {
            float y = FIELD_START_Y - i * FIELD_GAP;
            drawRow(cb, bf, bfBold, rows[i][0], rows[i][1], y);
        }
    }

    private void drawRow(PdfContentByte cb, BaseFont bf, BaseFont bfBold,
                         String label, String value, float y) throws Exception {
        String labelText = label + ": ";
        float  labelW    = bf.getWidthPoint(labelText, 6f);

        cb.setColorFill(GRAY_LABEL);
        cb.beginText();
        cb.setFontAndSize(bf, 6f);
        cb.setTextMatrix(FIELD_X, y);
        cb.showText(labelText);
        cb.endText();

        cb.setColorFill(BLACK);
        cb.beginText();
        cb.setFontAndSize(bfBold, 6f);
        cb.setTextMatrix(FIELD_X + labelW, y);
        cb.showText(value);
        cb.endText();
    }

    private void drawPhoto(PdfContentByte cb, BaseFont bf, byte[] photoBytes) throws Exception {
        if (photoBytes != null) {
            Image photo = Image.getInstance(photoBytes);
            photo.scaleToFit(PHOTO_SIZE, PHOTO_SIZE);
            float scaledW = photo.getScaledWidth();
            float scaledH = photo.getScaledHeight();
            photo.setAbsolutePosition(
                    RIGHT_X + (PHOTO_SIZE - scaledW) / 2f,
                    PHOTO_Y + (PHOTO_SIZE - scaledH) / 2f);
            cb.addImage(photo);
        } else {
            cb.setColorFill(GRAY_PHOTO);
            cb.rectangle(RIGHT_X, PHOTO_Y, PHOTO_SIZE, PHOTO_SIZE);
            cb.fill();

            cb.setColorFill(WHITE);
            cb.beginText();
            cb.setFontAndSize(bf, 5f);
            float tw = bf.getWidthPoint("No Photo", 5f);
            cb.setTextMatrix(RIGHT_X + (PHOTO_SIZE - tw) / 2f, PHOTO_Y + PHOTO_SIZE / 2f - 5f);
            cb.showText("No Photo");
            cb.endText();
        }

        cb.setColorStroke(BLACK);
        cb.setLineWidth(0.5f);
        cb.rectangle(RIGHT_X, PHOTO_Y, PHOTO_SIZE, PHOTO_SIZE);
        cb.stroke();
    }

    private void drawQr(PdfContentByte cb, String qrData) throws Exception {
        BitMatrix     matrix = new QRCodeWriter().encode(qrData, BarcodeFormat.QR_CODE, 260, 260);
        BufferedImage qrImg  = MatrixToImageWriter.toBufferedImage(matrix);
        ByteArrayOutputStream qrBaos = new ByteArrayOutputStream();
        ImageIO.write(qrImg, "PNG", qrBaos);

        Image qr = Image.getInstance(qrBaos.toByteArray());
        qr.scaleToFit(QR_SIZE, QR_SIZE);
        qr.setAbsolutePosition(RIGHT_X, QR_Y);
        cb.addImage(qr);

        cb.setColorStroke(BLACK);
        cb.setLineWidth(0.5f);
        cb.rectangle(RIGHT_X, QR_Y, QR_SIZE, QR_SIZE);
        cb.stroke();
    }

    private void drawFooter(PdfContentByte cb, BaseFont bf) throws Exception {
        cb.setColorFill(GRAY_LABEL);
        cb.beginText();
        cb.setFontAndSize(bf, 5f);
        cb.setTextMatrix(FIELD_X, MARGIN + 5f);
        cb.showText("Issued: " + LocalDate.now() + "   |   This card is non-transferable.");
        cb.endText();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String buildQrData(UserProfileDto user, MembershipDto m) {
        String shortId = m.getId().length() >= 8
                ? m.getId().substring(0, 8).toUpperCase()
                : m.getId().toUpperCase();
        return "Name: " + orDash(user.getName()) + "\n"
             + "Father's Name: " + orDash(user.getFatherName()) + "\n"
             + "Phone: " + orDash(user.getMobile()) + "\n"
             + "Shift: " + formatShift(m.getShift()) + "\n"
             + "Seat: " + orDash(m.getSeatNumber()) + "\n"
             + "Valid Till: " + orDash(m.getEndDate()) + "\n"
             + "Member ID: " + shortId;
    }

    private String calcAge(String dateOfBirth) {
        if (dateOfBirth == null || dateOfBirth.isBlank()) return "—";
        try {
            int age = Period.between(LocalDate.parse(dateOfBirth), LocalDate.now()).getYears();
            return age + " years";
        } catch (Exception e) {
            return dateOfBirth;
        }
    }

    private String formatShift(String shift) {
        if (shift == null) return "—";
        return switch (shift) {
            case "MORNING"  -> "Morning Hours";
            case "EVENING"  -> "Evening Hours";
            case "FULL_DAY" -> "Full Day Hours";
            default         -> shift;
        };
    }

    private String formatPaid(BigDecimal price) {
        if (price == null) return "—";
        if (price.stripTrailingZeros().scale() <= 0) {
            return "Rs. " + price.toBigInteger();
        }
        return "Rs. " + price.toPlainString();
    }

    private String orDash(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }
}
