package com.library.common.idcard;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class IdCardPdfGeneratorTest {

    private final IdCardPdfGenerator generator = new IdCardPdfGenerator();

    private byte[] tinyPngBytes() throws Exception {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    @Test
    void generate_noPhoto_returnsValidPdfBytes() {
        IdCardData data = new IdCardData(
                "Manish Meena", "9876543210", "A1B2C3D4",
                "Full Day Plan", "03 Aug 2026", null);

        byte[] pdf = generator.generate(data);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generate_withPhoto_returnsValidPdfBytes() throws Exception {
        IdCardData data = new IdCardData(
                "Manish Meena", "9876543210", "A1B2C3D4",
                "Full Day Plan", "03 Aug 2026", tinyPngBytes());

        byte[] pdf = generator.generate(data);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generate_missingOptionalFields_doesNotThrow() {
        IdCardData data = new IdCardData(null, null, null, null, null, null);

        byte[] pdf = generator.generate(data);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generate_invalidPhotoBytes_fallsBackToSilhouetteGracefully() {
        IdCardData data = new IdCardData(
                "Manish Meena", "9876543210", "A1B2C3D4",
                "Full Day Plan", "03 Aug 2026", new byte[]{1, 2, 3});

        byte[] pdf = generator.generate(data);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
