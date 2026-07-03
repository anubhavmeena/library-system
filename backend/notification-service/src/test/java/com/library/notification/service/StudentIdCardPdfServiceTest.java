package com.library.notification.service;

import com.library.notification.dto.BookingConfirmedEvent;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class StudentIdCardPdfServiceTest {

    private final StudentIdCardPdfService service = new StudentIdCardPdfService();

    private BookingConfirmedEvent buildEvent() {
        BookingConfirmedEvent e = new BookingConfirmedEvent();
        e.setUserId("user-123");
        e.setMembershipId("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        e.setUserName("Manish Meena");
        e.setUserMobile("9876543210");
        e.setUserEmail("manish@test.com");
        e.setPlanName("Full Day Plan");
        e.setSeatNumber("B12");
        e.setShift("FULL_DAY");
        e.setStartDate("2026-07-03");
        e.setEndDate("2026-08-03");
        return e;
    }

    private byte[] tinyPngBytes() throws Exception {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    @Test
    void buildIdCard_noPhoto_returnsValidPdfBytes() {
        byte[] pdf = service.buildIdCard(buildEvent(), null);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void buildIdCard_withPhoto_returnsValidPdfBytes() throws Exception {
        byte[] pdf = service.buildIdCard(buildEvent(), tinyPngBytes());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void buildIdCard_missingOptionalFields_doesNotThrow() {
        BookingConfirmedEvent e = buildEvent();
        e.setPlanName(null);
        e.setUserMobile(null);
        e.setUserName(null);
        e.setEndDate(null);

        byte[] pdf = service.buildIdCard(e, null);

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void buildIdCard_shortMembershipId_doesNotThrow() {
        BookingConfirmedEvent e = buildEvent();
        e.setMembershipId("abc");

        byte[] pdf = service.buildIdCard(e, null);

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void buildIdCard_nullMembershipId_doesNotThrow() {
        BookingConfirmedEvent e = buildEvent();
        e.setMembershipId(null);

        byte[] pdf = service.buildIdCard(e, null);

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void buildIdCard_invalidPhotoBytes_fallsBackToSilhouetteGracefully() {
        byte[] pdf = service.buildIdCard(buildEvent(), new byte[]{1, 2, 3});

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
