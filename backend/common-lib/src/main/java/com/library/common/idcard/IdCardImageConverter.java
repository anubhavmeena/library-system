package com.library.common.idcard;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

// Rasterizes the first page of a generated ID card PDF into a PNG — used for
// the WhatsApp send, which uses an image-header template rather than a
// document-header one. Rendering from the already-generated PDF (rather than
// drawing a second, separate image-native version) keeps the card's visual
// design defined in exactly one place, IdCardPdfGenerator.
public final class IdCardImageConverter {

    private static final int DEFAULT_DPI = 300;

    private IdCardImageConverter() {
    }

    public static byte[] toPng(byte[] pdfBytes) {
        return toPng(pdfBytes, DEFAULT_DPI);
    }

    public static byte[] toPng(byte[] pdfBytes, int dpi) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(0, dpi, ImageType.RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to rasterize ID card PDF to PNG", e);
        }
    }
}
